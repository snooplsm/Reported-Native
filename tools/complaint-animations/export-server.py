from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from pathlib import Path
import os
os.chdir(Path(__file__).parent)
class Handler(SimpleHTTPRequestHandler):
 def do_POST(self):
  if self.path!='/save-motion': self.send_error(404);return
  data=self.rfile.read(int(self.headers['Content-Length']))
  Path('motion-capture.json').write_bytes(data)
  self.send_response(200);self.end_headers();self.wfile.write(b'ok')
ThreadingHTTPServer(('127.0.0.1',8767),Handler).serve_forever()
