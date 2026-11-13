"""BYD Split Telegram API relay. Only four fixed Bot API operations; no request logging."""
import collections
import hashlib
import hmac
import json
import os
import re
import socket
import ssl
import threading
import time
import urllib.error
import urllib.request
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MAX_BODY = 2 * 1024 * 1024
MAX_DOCUMENT = MAX_RESPONSE = 1024 * 1024
TOKEN = re.compile(r"[0-9]{5,20}:[A-Za-z0-9_-]{20,100}\Z")
METHODS = {"getMe", "getUpdates", "sendMessage", "sendDocument"}


class RelayError(Exception):
    def __init__(self, code, status=400):
        self.code, self.status = code, status


def validate(data):
    if not isinstance(data, dict) or set(data) - {"method", "token", "fields", "document"}:
        raise RelayError("invalid_request")
    method, token, fields = data.get("method"), data.get("token"), data.get("fields")
    if not isinstance(method, str) or method not in METHODS or not isinstance(token, str) or not TOKEN.fullmatch(token):
        raise RelayError("invalid_request")
    if not isinstance(fields, dict):
        raise RelayError("invalid_request")
    allowed = {"getMe": set(), "getUpdates": {"offset", "limit", "timeout", "allowed_updates"},
               "sendMessage": {"chat_id", "text", "link_preview_options"},
               "sendDocument": {"chat_id", "caption"}}[method]
    if set(fields) - allowed:
        raise RelayError("invalid_request")
    if method == "getUpdates":
        if fields != {"offset": -100, "limit": 100, "timeout": 0, "allowed_updates": ["message"]}:
            raise RelayError("invalid_request")
    if method in {"sendMessage", "sendDocument"}:
        chat = fields.get("chat_id")
        if isinstance(chat, bool) or not isinstance(chat, int) or chat == 0 or abs(chat) > 2**53:
            raise RelayError("invalid_request")
    document = data.get("document")
    if method == "sendMessage":
        text = fields.get("text")
        if not isinstance(text, str) or len(text.encode("utf-16-le")) // 2 > 4096:
            raise RelayError("invalid_request")
        if fields.get("link_preview_options", {"is_disabled": True}) != {"is_disabled": True}:
            raise RelayError("invalid_request")
    if method == "sendDocument":
        if not isinstance(document, str) or len(document.encode("utf-8")) > MAX_DOCUMENT:
            raise RelayError("too_large", 413)
        caption = fields.get("caption", "")
        if not isinstance(caption, str) or len(caption) > 1024:
            raise RelayError("invalid_request")
    elif document is not None:
        raise RelayError("invalid_request")
    return method, token, fields, document


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def telegram(method, token, fields, document):
    # Target host and methods cannot be supplied by a client. TLS verification stays enabled.
    target = "https://api.telegram.org/bot" + token + "/" + method
    if document is None:
        body = json.dumps(fields, ensure_ascii=False).encode("utf-8")
        content_type = "application/json; charset=utf-8"
    else:
        boundary = "bydsplit" + uuid.uuid4().hex
        chunks = []
        for key, value in fields.items():
            chunks.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode())
        chunks.extend([f'--{boundary}\r\nContent-Disposition: form-data; name="document"; filename="BYD-Split-diagnostics.txt"\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n'.encode(),
                       document.encode("utf-8"), f"\r\n--{boundary}--\r\n".encode()])
        body = b"".join(chunks)
        content_type = "multipart/form-data; boundary=" + boundary
    req = urllib.request.Request(target, body, {"Content-Type": content_type}, method="POST")
    # Do not inherit HTTP_PROXY from an unrelated deployment environment.
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect(),
                                       urllib.request.HTTPSHandler(context=ssl.create_default_context()))
    try:
        try:
            response = opener.open(req, timeout=15)
        except urllib.error.HTTPError as e:
            if e.code in range(300, 400):
                e.close()
                raise RelayError("upstream_response", 502) from None
            response = e
        with response:
            raw = response.read(MAX_RESPONSE + 1)
        if len(raw) > MAX_RESPONSE:
            raise RelayError("upstream_response", 502)
        result = json.loads(raw)
        if not isinstance(result, dict) or not isinstance(result.get("ok"), bool):
            raise RelayError("upstream_response", 502)
        if result["ok"] and "result" not in result:
            raise RelayError("upstream_response", 502)
        if not result["ok"] and not isinstance(result.get("error_code"), int):
            raise RelayError("upstream_response", 502)
        return result
    except RelayError:
        raise
    except (TimeoutError, socket.timeout):
        raise RelayError("upstream_timeout", 504) from None
    except Exception:
        # Never log exception text: urllib errors may contain the bot token in their URL.
        raise RelayError("upstream_unreachable", 502) from None


class RelayServer(ThreadingHTTPServer):
    daemon_threads = True
    block_on_close = False
    request_queue_size = 16

    def __init__(self, address, key_hash, transport=telegram, clock=time.monotonic):
        if not re.fullmatch(r"[a-f0-9]{64}", key_hash):
            raise ValueError("Set RELAY_KEY_SHA256 to a SHA-256 hex digest")
        self.key_hash, self.transport, self.clock = key_hash, transport, clock
        self.slots, self.lock, self.recent = threading.BoundedSemaphore(8), threading.Lock(), collections.deque()
        super().__init__(address, Handler)

    def process_request(self, request, client_address):
        if not self.slots.acquire(False):
            try:
                request.settimeout(1)
                request.sendall(b"HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
            except OSError:
                pass
            self.shutdown_request(request)
            return
        try:
            super().process_request(request, client_address)
        except Exception:
            self.slots.release()
            raise

    def process_request_thread(self, request, client_address):
        try:
            super().process_request_thread(request, client_address)
        finally:
            self.slots.release()

    def handle_error(self, request, client_address):
        pass

    def authenticate(self, value):
        if not isinstance(value, str) or not re.fullmatch(r"[A-Za-z0-9_-]{32,128}", value):
            raise RelayError("unauthorized", 401)
        if not hmac.compare_digest(hashlib.sha256(value.encode()).hexdigest(), self.key_hash):
            raise RelayError("unauthorized", 401)
        with self.lock:
            now = self.clock()
            while self.recent and self.recent[0] <= now - 60:
                self.recent.popleft()
            if len(self.recent) >= 60:
                raise RelayError("rate_limited", 429)
            self.recent.append(now)


class Handler(BaseHTTPRequestHandler):
    server_version = "SplitRelay"
    sys_version = ""
    protocol_version = "HTTP/1.1"

    def setup(self):
        super().setup()
        self.connection.settimeout(10)

    def log_message(self, *args):
        pass

    def send_error(self, code, message=None, explain=None):
        self.reply(code, {"ok": False, "relay_error": "invalid_request"})

    def reply(self, status, body):
        raw = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.close_connection = True
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(raw)

    def do_GET(self):
        self.reply(200 if self.path == "/healthz" else 404,
                   {"status": "ok"} if self.path == "/healthz" else {"ok": False, "relay_error": "not_found"})

    def do_POST(self):
        try:
            if self.path != "/v1/telegram":
                raise RelayError("not_found", 404)
            self.server.authenticate(self.headers.get("X-Split-Relay-Key", ""))
            if self.headers.get("Transfer-Encoding") or len(self.headers.get_all("Content-Length", [])) != 1:
                raise RelayError("invalid_request")
            if self.headers.get("Content-Type", "").split(";", 1)[0].strip().lower() != "application/json":
                raise RelayError("invalid_request")
            length = self.headers.get("Content-Length", "")
            if not re.fullmatch(r"[0-9]{1,8}", length):
                raise RelayError("invalid_request")
            length = int(length)
            if length < 2 or length > MAX_BODY:
                raise RelayError("too_large", 413)
            raw = self.rfile.read(length)
            if len(raw) != length:
                raise RelayError("invalid_request")
            data = json.loads(raw)
            method, token, fields, document = validate(data)
            self.reply(200, self.server.transport(method, token, fields, document))
        except RelayError as e:
            self.reply(e.status, {"ok": False, "relay_error": e.code})
        except (json.JSONDecodeError, UnicodeError, TypeError, ValueError):
            self.reply(400, {"ok": False, "relay_error": "invalid_request"})
        except (OSError, TimeoutError):
            self.close_connection = True
        except Exception:
            self.reply(502, {"ok": False, "relay_error": "upstream_response"})


if __name__ == "__main__":
    server = RelayServer(("0.0.0.0", 8080), os.environ.get("RELAY_KEY_SHA256", ""))
    print("Split Telegram relay started", flush=True)
    server.serve_forever()
