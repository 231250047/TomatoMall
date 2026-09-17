"""Local sandbox callback relay. Exposes only the Alipay notify POST endpoint."""
import http.client
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PATH = '/api/orders/notify'
MAX_BODY = 65536
Server = ThreadingHTTPServer


def upstream_connection():
    return http.client.HTTPConnection('127.0.0.1', 8080, timeout=10)


class Handler(BaseHTTPRequestHandler):
    def setup(self):
        super().setup()
        self.connection.settimeout(10)

    def reply(self, status, body=b'fail'):
        self.send_response(status)
        self.send_header('Content-Type', 'text/plain; charset=utf-8')
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Connection', 'close')
        self.end_headers()
        self.wfile.write(body)
        self.close_connection = True

    def do_GET(self):
        self.reply(404)

    def do_POST(self):
        if self.path != PATH:
            return self.reply(404)
        if self.headers.get('Transfer-Encoding'):
            return self.reply(400)
        try:
            length = int(self.headers.get('Content-Length', '-1'))
        except ValueError:
            return self.reply(400)
        if length < 0:
            return self.reply(411)
        if length > MAX_BODY:
            return self.reply(413)
        content_type = self.headers.get('Content-Type', '')
        if content_type.split(';', 1)[0].strip().lower() != 'application/x-www-form-urlencoded':
            return self.reply(415)
        upstream = None
        try:
            body = self.rfile.read(length)
            if len(body) != length:
                return self.reply(400)
            upstream = upstream_connection()
            upstream.request('POST', PATH, body=body, headers={'Content-Type': content_type})
            response = upstream.getresponse()
            payload = response.read(1025)
            if response.status != 200 or payload not in (b'success', b'fail'):
                return self.reply(502)
            self.reply(200, payload)
        except (OSError, http.client.HTTPException):
            self.reply(502)
        finally:
            if upstream is not None:
                upstream.close()

    def log_message(self, format, *args):
        # Do not log callback content, signatures or query strings.
        print('callback request processed', flush=True)


if __name__ == '__main__':
    server = Server(('127.0.0.1', 18090), Handler)
    print('Alipay callback relay listening on 127.0.0.1:18090', flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
