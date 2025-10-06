/**
 * Edge Viewer Web - TypeScript Viewer for Android processed frames
 */

interface FrameData {
    width: number;
    height: number;
    format: string;
    base64: string;
    fps?: number;
    timestamp?: number;
}

class EdgeViewerWeb {
    private canvas: HTMLCanvasElement;
    private ctx: CanvasRenderingContext2D;
    private statsElement: HTMLElement;
    private imageElement: HTMLImageElement;
    private wsStatusElement: HTMLElement;
    private websocket: WebSocket | null = null;
    
    private currentFrame: FrameData | null = null;
    private frameCount = 0;
    private lastFpsTime = Date.now();
    private currentFps = 0;

    constructor() {
        this.canvas = document.getElementById('canvas') as HTMLCanvasElement;
        this.ctx = this.canvas.getContext('2d')!;
        this.statsElement = document.getElementById('stats')!;
        this.imageElement = document.getElementById('frameImage') as HTMLImageElement;
        this.wsStatusElement = document.getElementById('wsStatus')!;

        this.init();
    }

    private init(): void {
        console.log('Edge Viewer Web initialized');
        
        // Load sample frame
        this.loadSampleFrame();
        
        // Setup WebSocket (mock for now)
        this.setupWebSocket();
        
        // Setup file upload
        this.setupFileUpload();
        
        // Update stats periodically
        setInterval(() => this.updateStats(), 100);
    }

    private loadSampleFrame(): void {
        // Sample frame data (placeholder - will be replaced with actual Android export)
        const sampleFrame: FrameData = {
            width: 1280,
            height: 720,
            format: 'RGBA',
            base64: this.generateSampleBase64(),
            fps: 30,
            timestamp: Date.now()
        };

        this.displayFrame(sampleFrame);
    }

    private generateSampleBase64(): string {
        // Generate a simple gradient pattern as sample
        const width = 320;
        const height = 240;
        const tempCanvas = document.createElement('canvas');
        tempCanvas.width = width;
        tempCanvas.height = height;
        const tempCtx = tempCanvas.getContext('2d')!;

        // Draw gradient
        const gradient = tempCtx.createLinearGradient(0, 0, width, height);
        gradient.addColorStop(0, '#000000');
        gradient.addColorStop(1, '#ffffff');
        tempCtx.fillStyle = gradient;
        tempCtx.fillRect(0, 0, width, height);

        // Add text
        tempCtx.fillStyle = '#00ff00';
        tempCtx.font = '20px monospace';
        tempCtx.fillText('Sample Frame', 10, 30);
        tempCtx.fillText('Load Android export', 10, 60);
        tempCtx.fillText('or use WebSocket', 10, 90);

        return tempCanvas.toDataURL().split(',')[1];
    }

    private displayFrame(frame: FrameData): void {
        this.currentFrame = frame;

        // Create image from base64
        const img = new Image();
        img.onload = () => {
            // Resize canvas to match frame
            this.canvas.width = frame.width;
            this.canvas.height = frame.height;

            // Draw frame
            this.ctx.drawImage(img, 0, 0, frame.width, frame.height);

            // Update frame count for FPS
            this.frameCount++;
            const now = Date.now();
            const elapsed = now - this.lastFpsTime;
            if (elapsed >= 1000) {
                this.currentFps = (this.frameCount * 1000) / elapsed;
                this.frameCount = 0;
                this.lastFpsTime = now;
            }

            this.updateStats();
        };
        img.src = 'data:image/png;base64,' + frame.base64;
    }

    private updateStats(): void {
        if (!this.currentFrame) return;

        const stats = `
            <div class="stat-item">
                <span class="stat-label">Resolution:</span>
                <span class="stat-value">${this.currentFrame.width} x ${this.currentFrame.height}</span>
            </div>
            <div class="stat-item">
                <span class="stat-label">Format:</span>
                <span class="stat-value">${this.currentFrame.format}</span>
            </div>
            <div class="stat-item">
                <span class="stat-label">FPS:</span>
                <span class="stat-value">${this.currentFps.toFixed(1)}</span>
            </div>
            <div class="stat-item">
                <span class="stat-label">Timestamp:</span>
                <span class="stat-value">${new Date(this.currentFrame.timestamp || Date.now()).toLocaleTimeString()}</span>
            </div>
        `;

        this.statsElement.innerHTML = stats;
    }

    private setupWebSocket(): void {
        const wsUrl = 'ws://localhost:8080/frames'; // Mock endpoint
        
        try {
            this.websocket = new WebSocket(wsUrl);
            
            this.websocket.onopen = () => {
                console.log('WebSocket connected');
                this.wsStatusElement.textContent = 'Connected';
                this.wsStatusElement.className = 'ws-status connected';
            };

            this.websocket.onmessage = (event) => {
                try {
                    const frame: FrameData = JSON.parse(event.data);
                    this.displayFrame(frame);
                } catch (e) {
                    console.error('Failed to parse frame data:', e);
                }
            };

            this.websocket.onerror = (error) => {
                console.log('WebSocket error (expected for mock):', error);
                this.wsStatusElement.textContent = 'Disconnected (Mock)';
                this.wsStatusElement.className = 'ws-status disconnected';
            };

            this.websocket.onclose = () => {
                console.log('WebSocket closed');
                this.wsStatusElement.textContent = 'Disconnected';
                this.wsStatusElement.className = 'ws-status disconnected';
            };

        } catch (e) {
            console.log('WebSocket not available (mock mode)');
            this.wsStatusElement.textContent = 'Mock Mode';
            this.wsStatusElement.className = 'ws-status mock';
        }
    }

    private setupFileUpload(): void {
        const fileInput = document.getElementById('fileInput') as HTMLInputElement;
        const uploadBtn = document.getElementById('uploadBtn') as HTMLButtonElement;

        uploadBtn.addEventListener('click', () => fileInput.click());

        fileInput.addEventListener('change', (event) => {
            const file = (event.target as HTMLInputElement).files?.[0];
            if (!file) return;

            const reader = new FileReader();
            reader.onload = (e) => {
                const content = e.target?.result as string;
                this.parseFrameFile(content);
            };
            reader.readAsText(file);
        });
    }

    private parseFrameFile(content: string): void {
        try {
            const lines = content.split('\n');
            let width = 1280;
            let height = 720;
            let format = 'RGBA';
            let base64 = '';

            let inBase64 = false;
            for (const line of lines) {
                if (line.startsWith('Width:')) {
                    width = parseInt(line.split(':')[1].trim());
                } else if (line.startsWith('Height:')) {
                    height = parseInt(line.split(':')[1].trim());
                } else if (line.startsWith('Format:')) {
                    format = line.split(':')[1].trim();
                } else if (line.startsWith('Base64:')) {
                    inBase64 = true;
                } else if (inBase64) {
                    base64 += line.trim();
                }
            }

            const frame: FrameData = {
                width,
                height,
                format,
                base64,
                timestamp: Date.now()
            };

            this.displayFrame(frame);
            console.log('Frame loaded from file:', frame.width, 'x', frame.height);

        } catch (e) {
            console.error('Failed to parse frame file:', e);
            alert('Failed to parse frame file. Please check the format.');
        }
    }

    public destroy(): void {
        if (this.websocket) {
            this.websocket.close();
        }
    }
}

// Initialize when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        new EdgeViewerWeb();
    });
} else {
    new EdgeViewerWeb();
}

// Export for potential module usage
export { EdgeViewerWeb, FrameData };
