import hashlib
import http.client
import json
import threading
import unittest
import relay

KEY = 'test_' + 'x' * 40
TOKEN = '123456:' + 'a' * 30

class RelayTests(unittest.TestCase):
    def setUp(self):
        self.calls = []
        def upstream(*args):
            self.calls.append(args)
            return {'ok': False, 'error_code': 401, 'description': 'Unauthorized'}
        self.server = relay.RelayServer(('127.0.0.1', 0), hashlib.sha256(KEY.encode()).hexdigest(), upstream)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
    def tearDown(self):
        self.server.shutdown(); self.server.server_close(); self.thread.join()
    def request(self, data, key=KEY, path='/v1/telegram'):
        c = http.client.HTTPConnection(*self.server.server_address, timeout=2)
        c.request('POST', path, json.dumps(data), {'Content-Type':'application/json', 'X-Split-Relay-Key':key})
        r = c.getresponse(); result = r.status, json.loads(r.read()); c.close(); return result
    def payload(self, method='getMe', fields=None, document=None):
        return dict(method=method, token=TOKEN, fields=fields or {}, document=document)
    def test_authentication_and_method_restrictions(self):
        self.assertEqual(self.request(self.payload(), 'bad')[0], 401)
        self.assertEqual(self.request(self.payload('deleteWebhook'))[0], 400)
        p = self.payload(); p['url'] = 'http://169.254.169.254/'
        self.assertEqual(self.request(p)[0], 400)
        self.assertEqual(self.request(self.payload(), path='/elsewhere')[0], 404)
        self.assertEqual(self.calls, [])
    def test_passes_upstream_error_without_retries(self):
        status, body = self.request(self.payload())
        self.assertEqual(status, 200); self.assertEqual(body['error_code'], 401)
        self.assertEqual(len(self.calls), 1)
    def test_message_and_full_document(self):
        self.request(self.payload('sendMessage', {'chat_id':123, 'text':'Отчёт'}))
        document = 'Расшифровки\n' * 10000
        self.request(self.payload('sendDocument', {'chat_id':123, 'caption':'Отчёт'}, document))
        self.assertEqual(self.calls[-1][-1], document)
        self.assertEqual(self.request(self.payload('sendDocument', {'chat_id':123}, 'x'*(relay.MAX_DOCUMENT+1)))[0], 413)
        self.assertEqual(self.request(self.payload('sendMessage', {'chat_id':True, 'text':'x'}))[0], 400)
        self.assertEqual(self.request(self.payload('sendMessage', {'chat_id':123, 'text':'x'*4097}))[0], 400)
    def test_updates_cannot_enable_long_polling(self):
        self.assertEqual(self.request(self.payload('getUpdates', {'timeout':60}))[0], 400)
        self.assertEqual(self.request(self.payload('getUpdates', {'offset':-100,'limit':100,'timeout':0,'allowed_updates':['message']}))[0], 200)
    def test_rate_limit_expires(self):
        now = [0]; self.server.clock = lambda: now[0]
        for _ in range(60): self.server.authenticate(KEY)
        with self.assertRaises(relay.RelayError) as raised: self.server.authenticate(KEY)
        self.assertEqual(raised.exception.status, 429)
        now[0] = 61; self.server.authenticate(KEY)

if __name__ == '__main__': unittest.main()
