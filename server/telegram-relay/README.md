# Telegram gateway for BYD Split

Optional gateway for the Telegram connection mode in **Settings → Integrations**. Every installation can use its own server. Direct mode works with a VPN on the head unit; ordinary HTTP CONNECT and SOCKS5 proxies are configured separately and do not use this gateway.

## Deployment

Requires Docker Compose, a domain, an HTTPS reverse proxy with a valid public certificate, and outbound HTTPS access to `api.telegram.org`. No Telegram bot token is stored in server configuration.

1. Copy this directory to a persistent Compose project directory. For aaPanel use `/www/server/panel/data/compose/BYD-Split-Telegram` and register/import it as a Compose project in Docker. The Compose project name is `byd-split-telegram`. Keep its real configuration path registered in aaPanel so its start/stop/rebuild actions manage this project.
2. Generate a random URL-safe access key (32–128 characters). Store the original securely for entering into Split; put **only its SHA-256 hexadecimal digest** in `.env` as `RELAY_KEY_SHA256=...`. Give `.env` mode 600. Never put a bot token or the raw gateway key in the image or repository.
3. Run `docker compose up -d --build`. The application listens only on host `127.0.0.1:18187`. Change the host port in Compose if occupied.
4. Create a reverse proxy site in aaPanel pointing to `http://127.0.0.1:18187`, issue its HTTPS certificate through aaPanel, and retain its certificate renewal registration and ACME verification location.
5. For `/v1/telegram`, require HTTPS, disable detailed access logs, buffering to disk, upstream retries and caching; set a 2 MiB body limit and approximately 25-second upstream timeouts. Keep TLS certificate verification enabled in the client. Example location:

```nginx
location = /v1/telegram {
    if ($scheme = http) { return 426; }
    client_max_body_size 2m;
    client_body_timeout 10s;
    proxy_pass http://127.0.0.1:18187;
    proxy_connect_timeout 5s;
    proxy_send_timeout 25s;
    proxy_read_timeout 25s;
    proxy_next_upstream off;
    proxy_cache off;
    proxy_request_buffering off;
    access_log off;
}
```

6. Check HTTPS `/healthz` (200) and an unauthenticated POST to `/v1/telegram` (401). In Split select **HTTPS gateway**, enter the HTTPS base URL without `/v1/telegram`, and its raw access key. Use the existing bot token check/pairing flow.

Use an operator-controlled server: the gateway necessarily receives the bot token and report in memory. It deliberately does not log request bodies, tokens, keys, reports or upstream exception text. It stores only the access-key hash. Reverse proxy monitoring/debug tools must likewise avoid body/header capture. The key is shared by clients of this instance; replace its hash and recreate the container to rotate access. Do not publish a personal key in an APK. For isolated users deploy separate instances/keys.

## Protocol

`POST /v1/telegram`, `Content-Type: application/json`, `X-Split-Relay-Key: <key>`:

```json
{"method":"getMe","token":"<bot token>","fields":{},"document":null}
```

Only `getMe`, bounded non-polling `getUpdates`, `sendMessage`, and inline-text `sendDocument` are accepted. Upstream is fixed to HTTPS `api.telegram.org`; arbitrary URLs, methods and remote document downloads are rejected. Text reports over the message limit use a complete UTF-8 TXT document (maximum 1 MiB). The envelope limit is 2 MiB.

Telegram replies, including its `error_code`, are returned as JSON with HTTP 200. Gateway failures use HTTP 4xx/5xx plus `relay_error`, distinct from Telegram errors. Authentication failures, rate limits and upstream failures have separate application messages. Limits: eight concurrent requests, 60 authenticated requests per minute per instance, no automatic retry. Health checks do not contact Telegram.

```sh
python3 -m unittest discover -s server/telegram-relay -v
```

## Android connection modes

- **Direct / HU VPN**: explicit direct socket connection through the operating system network; does not inherit an unrelated Java/system proxy.
- **HTTPS gateway**: this protocol; custom base URL and access key.
- **HTTP proxy**: HTTP CONNECT to Telegram HTTPS, with optional Basic proxy credentials.
- **SOCKS5**: remote resolution of `api.telegram.org`, with optional username/password authentication. MTProto proxies are not supported.

All modes verify Telegram/gateway HTTPS certificates. Proxy credentials are scoped to this Telegram client; no global authenticator is installed. HTTP/SOCKS5 handshakes themselves are not encrypted: use a trusted network or VPN when proxy credentials need transport protection. Profiles are encrypted with Android Keystore AES-GCM. A broken saved profile produces an error rather than silently switching to direct. Empty secret fields retain the previous secret only for the same destination and username; changing the destination requires entering its secret again.

These modes add no background scanner, alarm or retry loop. Existing cancellation on leaving the page or entering vehicle sleep also closes active network requests. A failed report remains available locally for manual sending later.

## Request metadata and retention

The deployed gateway records **only requests to `/v1/telegram`** in a separate Nginx metadata log. Health checks and scanner traffic to other paths are excluded. Recorded fields: time, HTTP method, HTTP response status, total duration, upstream HTTP status and upstream duration. No client IP, URI/query, headers, bot token, gateway key, chat ID, body or diagnostic report is included.

Example HTTP-level format (in the Nginx `http` context, or aaPanel custom HTTP block):

```nginx
log_format split_tg_meta escape=json '{"time":"$time_iso8601","method":"$request_method","status":$status,"duration_s":$request_time,"upstream_status":"$upstream_status","upstream_duration_s":"$upstream_response_time"}';
```

Replace `access_log off;` **only inside the API location** with `access_log /your/log/path.gateway-access.log split_tg_meta;`. Keep the rest of the site access logging disabled. Store both changes in aaPanel's project configuration so they survive site regeneration.

Use a dedicated `/etc/logrotate.d/` rule for the gateway access/error files: `daily`, `rotate 7`, `maxage 7`, `missingok`, `ifempty`, `compress`, `delaycompress`, with a mode-0640 replacement log and the local Nginx worker owner/group. Explicitly set gzip commands/extension if global logrotate defaults use another compressor. `ifempty` keeps daily aging active even without traffic. A shared `postrotate` sends USR1 to the Nginx master PID, reopening its files without restarting the gateway. Verify that the system logrotate timer is enabled. This retains up to seven daily archives plus the current log; archives older than seven days are removed on rotation.

An HTTP 200 means the gateway returned a Telegram API reply; it does **not** alone prove delivery, because Telegram errors are also returned in that reply. Use the application result to confirm bot pairing/report delivery. Metadata is enough to distinguish no request, rejected gateway key (401), rate limit (429), or gateway/upstream network failure (5xx).
