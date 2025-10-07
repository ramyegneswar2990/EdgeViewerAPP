// Simple static server + WebSocket for frames
// Run with: node server.js (or via npm script)

const http = require('http');
const fs = require('fs');
const path = require('path');
const WebSocket = require('ws');

const publicDir = path.join(__dirname, 'public');
const port = process.env.PORT || 8080;

const server = http.createServer((req, res) => {
  const reqPath = req.url === '/' ? '/index.html' : req.url;
  const filePath = path.join(publicDir, reqPath);
  fs.readFile(filePath, (err, data) => {
    if (err) {
      res.writeHead(404);
      return res.end('Not found');
    }
    const ext = (path.extname(filePath).slice(1) || '').toLowerCase();
    const types = {
      html: 'text/html',
      js: 'text/javascript',
      css: 'text/css',
      png: 'image/png',
      jpg: 'image/jpeg',
      jpeg: 'image/jpeg',
      svg: 'image/svg+xml',
      map: 'application/json'
    };
    res.writeHead(200, { 'Content-Type': types[ext] || 'application/octet-stream' });
    res.end(data);
  });
});

// WebSocket server at /frames
const wss = new WebSocket.Server({ noServer: true });

wss.on('connection', (ws) => {
  console.log('WebSocket client connected');
  ws.on('message', (msg) => {
    // Expect JSON: { width, height, format, base64, timestamp }
    // Broadcast to all browser clients connected to this WSS
    wss.clients.forEach((client) => {
      if (client.readyState === WebSocket.OPEN) {
        client.send(msg);
      }
    });
  });
  ws.on('close', () => console.log('WebSocket client disconnected'));
});

server.on('upgrade', (request, socket, head) => {
  if (request.url === '/frames') {
    wss.handleUpgrade(request, socket, head, (ws) => {
      wss.emit('connection', ws, request);
    });
  } else {
    socket.destroy();
  }
});

server.listen(port, () => {
  console.log(`HTTP+WS server running at http://localhost:${port}`);
  console.log(`WebSocket endpoint ws://localhost:${port}/frames`);
});
