import http.client
import threading
import unittest
from unittest.mock import patch, MagicMock
from notify_proxy import Handler, Server

class BoundaryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = Server(('127.0.0.1', 0), Handler)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join()

    def request(self, method, path, body='', headers=None):
        conn = http.client.HTTPConnection('127.0.0.1', self.server.server_port, timeout=3)
        conn.request(method, path, body, headers or {})
        response = conn.getresponse()
        result = response.status, response.read()
        conn.close()
        return result

    def test_other_routes_never_reach_backend(self):
        with patch('notify_proxy.upstream_connection') as upstream:
            for method, path in [('GET', '/api/orders/notify'), ('POST', '/api/accounts'),
                                 ('POST', '/api/orders/notify?redirect=/api/accounts'),
                                 ('POST', '/api/orders/notify/../accounts')]:
                self.assertEqual(self.request(method, path)[0], 404)
            upstream.assert_not_called()

    def test_body_and_response_are_preserved(self):
        response = MagicMock(status=200)
        response.read.return_value = b'fail'
        with patch('notify_proxy.upstream_connection') as factory:
            factory.return_value.getresponse.return_value = response
            self.assertEqual(self.request('POST', '/api/orders/notify', 'sign=invalid',
                             {'Content-Type': 'application/x-www-form-urlencoded'}), (200, b'fail'))
            factory.return_value.request.assert_called_once_with('POST', '/api/orders/notify',
                body=b'sign=invalid', headers={'Content-Type': 'application/x-www-form-urlencoded'})

    def test_oversized_body_is_rejected(self):
        with patch('notify_proxy.upstream_connection') as upstream:
            self.assertEqual(self.request('POST', '/api/orders/notify', 'x' * 65537,
                             {'Content-Type': 'application/x-www-form-urlencoded'})[0], 413)
            upstream.assert_not_called()

if __name__ == '__main__':
    unittest.main()
