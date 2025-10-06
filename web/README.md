# Edge Viewer Web

TypeScript-based web viewer for displaying processed frames from the Android Edge Viewer app.

## Features

- Display processed camera frames (RGBA format)
- Real-time FPS monitoring
- Frame statistics (resolution, format, timestamp)
- File upload for Android-exported frames
- WebSocket support for live streaming (mock endpoint ready)
- Modern, responsive UI

## Setup

```bash
# Install dependencies
npm install

# Build TypeScript
npm run build

# Start development server
npm start
```

Open browser to `http://localhost:3000`

## Usage

### Load Saved Frame

1. Run Android app and save a processed frame
2. Click "Load Frame File" button
3. Select the exported `.txt` file
4. Frame will be displayed with stats

### WebSocket Integration (Optional)

The viewer includes a mock WebSocket client ready for integration:

```typescript
// WebSocket endpoint
ws://localhost:8080/frames

// Expected message format
{
  "width": 1280,
  "height": 720,
  "format": "RGBA",
  "base64": "...",
  "fps": 30,
  "timestamp": 1234567890
}
```

## Architecture

```
web/
├── src/
│   └── index.ts          # Main TypeScript application
├── public/
│   └── index.html        # HTML viewer interface
├── dist/                 # Compiled JavaScript (generated)
├── package.json          # Dependencies
└── tsconfig.json         # TypeScript config
```

## Development

```bash
# Watch mode (auto-rebuild on changes)
npm run watch

# Build only
npm run build
```

## Browser Compatibility

- Chrome 90+
- Firefox 88+
- Safari 14+
- Edge 90+

Requires ES2020 support and Canvas API.
