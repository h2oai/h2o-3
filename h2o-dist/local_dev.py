from http.server import BaseHTTPRequestHandler, HTTPServer
from string import Template
from datetime import datetime

hostname = "localhost"
server_port = 8080

version_info = {
    "BRANCH_NAME": "rel-develop",
    "BUILD_NUMBER": 9,
    "BUILD_TIME_MILLIS": round((datetime.utcnow() - datetime(1970, 1, 1)).total_seconds() * 1000),
    "BUILD_TIME_ISO8601": datetime.now().isoformat(),
    "BUILD_TIME_LOCAL": datetime.utcnow(),
    "PROJECT_VERSION": "3.99.0.9",
    "LAST_COMMIT_HASH": "7d606463d8c778614e09c47c953ab65e9967b5af",
    "WHEEL_FILE_NAME": "h2o-3.99.0.9-py2.py3-none-any.whl"
}


class LocalDevServer(BaseHTTPRequestHandler):
    def do_GET(self):
        """Respond to a GET request."""
        if self.path == "/":
            self.send_response(200)
            self.send_header("Content-type", "text/html")
            self.end_headers()
            with open('index.html', 'r') as file:
                data = file.read()
                self.wfile.write(data.encode())
        elif self.path == "/buildinfo.json":
            self.send_response(200)
            self.send_header("Content-type", "application/json")
            self.end_headers()
            with open('buildinfo.json', 'r') as file:
                template_def = file.read().replace("SUBST_", "$")
                template = Template(template_def)
                data = template.substitute(**version_info)
                self.wfile.write(data.encode())


if __name__ == "__main__":
    webServer = HTTPServer((hostname, server_port), LocalDevServer)
    print("Open URL http://%s:%s in your browser" % (hostname, server_port))

    try:
        webServer.serve_forever()
    except KeyboardInterrupt:
        pass

    webServer.server_close()
