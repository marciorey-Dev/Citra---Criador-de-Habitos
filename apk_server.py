import http.server
import socketserver
import os

PORT = 3000
DIRECTORY = "/app/applet"

class ApkHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DIRECTORY, **kwargs)

    def guess_type(self, path):
        if path.endswith(".apk"):
            return "application/vnd.android.package-archive"
        return super().guess_type(path)

    def end_headers(self):
        if self.path.endswith(".apk") or self.path.startswith("/download"):
            self.send_header("Content-Disposition", 'attachment; filename="Citra.apk"')
            self.send_header("Cache-Control", "no-cache, no-store, must-revalidate")
        super().end_headers()

    def do_GET(self):
        if self.path in ("/", "/download", "/Citra", "/apk"):
            self.path = "/Citra.apk"
        return super().do_GET()

if __name__ == "__main__":
    socketserver.TCPServer.allow_reuse_address = True
    with socketserver.TCPServer(("127.0.0.1", PORT), ApkHandler) as httpd:
        print(f"Serving APK server on port {PORT}")
        httpd.serve_forever()
