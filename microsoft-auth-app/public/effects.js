/**
 * Zeus Auth - Visual Effects Engine
 * Lightning, particles, confetti, and audio feedback
 * Because authentication should be EPIC
 */

class ZeusEffects {
    constructor() {
        this.canvas = document.getElementById('lightning-canvas');
        this.ctx = this.canvas ? this.canvas.getContext('2d') : null;
        this.particlesContainer = document.getElementById('particles');
        this.audioContext = null;
        this.soundEnabled = true;

        this.init();
    }

    init() {
        this.setupCanvas();
        this.createParticles();
        this.setupTitleClick();
        this.setupKeyboardShortcuts();

        // Periodic ambient lightning
        setInterval(() => {
            if (Math.random() > 0.7) {
                this.ambientLightning();
            }
        }, 8000);

        console.log('Zeus Effects initialized. Press "L" for lightning, "C" for confetti!');
    }

    setupCanvas() {
        if (!this.canvas) return;

        const resize = () => {
            this.canvas.width = window.innerWidth;
            this.canvas.height = window.innerHeight;
        };

        resize();
        window.addEventListener('resize', resize);
    }

    // ==================
    // LIGHTNING EFFECTS
    // ==================

    drawLightningBolt(startX, startY, endX, endY, branches = 3) {
        if (!this.ctx) return;

        const ctx = this.ctx;
        const segments = 15;
        const maxOffset = 80;

        // Main bolt
        ctx.beginPath();
        ctx.moveTo(startX, startY);

        let currentX = startX;
        let currentY = startY;
        const deltaX = (endX - startX) / segments;
        const deltaY = (endY - startY) / segments;

        const points = [[startX, startY]];

        for (let i = 1; i < segments; i++) {
            const offsetX = (Math.random() - 0.5) * maxOffset;
            currentX += deltaX + offsetX;
            currentY += deltaY;
            points.push([currentX, currentY]);
            ctx.lineTo(currentX, currentY);
        }

        ctx.lineTo(endX, endY);
        points.push([endX, endY]);

        // Glow effect
        ctx.strokeStyle = 'rgba(255, 255, 255, 0.9)';
        ctx.lineWidth = 4;
        ctx.shadowColor = '#00bcf2';
        ctx.shadowBlur = 30;
        ctx.stroke();

        ctx.strokeStyle = '#00bcf2';
        ctx.lineWidth = 2;
        ctx.stroke();

        ctx.strokeStyle = 'white';
        ctx.lineWidth = 1;
        ctx.stroke();

        // Draw branches
        for (let b = 0; b < branches; b++) {
            const branchPoint = points[Math.floor(Math.random() * (points.length - 2)) + 1];
            const branchLength = 50 + Math.random() * 100;
            const branchAngle = (Math.random() - 0.5) * Math.PI;

            const branchEndX = branchPoint[0] + Math.cos(branchAngle) * branchLength;
            const branchEndY = branchPoint[1] + Math.sin(branchAngle) * branchLength + branchLength * 0.5;

            ctx.beginPath();
            ctx.moveTo(branchPoint[0], branchPoint[1]);

            const branchSegments = 5;
            let bx = branchPoint[0];
            let by = branchPoint[1];
            const bdx = (branchEndX - branchPoint[0]) / branchSegments;
            const bdy = (branchEndY - branchPoint[1]) / branchSegments;

            for (let i = 1; i < branchSegments; i++) {
                bx += bdx + (Math.random() - 0.5) * 20;
                by += bdy;
                ctx.lineTo(bx, by);
            }

            ctx.lineTo(branchEndX, branchEndY);

            ctx.strokeStyle = 'rgba(0, 188, 242, 0.6)';
            ctx.lineWidth = 1;
            ctx.shadowBlur = 15;
            ctx.stroke();
        }

        ctx.shadowBlur = 0;
    }

    strikeLightning() {
        if (!this.ctx) return;

        // Screen flash
        this.screenFlash();

        // Play thunder
        this.playThunder();

        // Draw multiple bolts
        const boltCount = 1 + Math.floor(Math.random() * 2);

        for (let i = 0; i < boltCount; i++) {
            setTimeout(() => {
                const startX = Math.random() * this.canvas.width;
                const startY = 0;
                const endX = startX + (Math.random() - 0.5) * 200;
                const endY = this.canvas.height * (0.5 + Math.random() * 0.5);

                this.drawLightningBolt(startX, startY, endX, endY, 2 + Math.floor(Math.random() * 3));

                // Fade out
                setTimeout(() => {
                    this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
                }, 100 + Math.random() * 100);
            }, i * 50);
        }
    }

    ambientLightning() {
        if (!this.ctx) return;

        // Subtle background lightning
        const startX = Math.random() * this.canvas.width;
        const endX = startX + (Math.random() - 0.5) * 150;
        const endY = this.canvas.height * 0.3;

        this.ctx.globalAlpha = 0.3;
        this.drawLightningBolt(startX, 0, endX, endY, 1);
        this.ctx.globalAlpha = 1;

        setTimeout(() => {
            this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
        }, 80);
    }

    screenFlash() {
        const flash = document.createElement('div');
        flash.className = 'screen-flash';
        document.body.appendChild(flash);

        setTimeout(() => flash.remove(), 150);
    }

    // ==================
    // PARTICLE EFFECTS
    // ==================

    createParticles() {
        if (!this.particlesContainer) return;

        const particleCount = 30;

        for (let i = 0; i < particleCount; i++) {
            this.createParticle(i * (100 / particleCount));
        }
    }

    createParticle(delay = 0) {
        if (!this.particlesContainer) return;

        const particle = document.createElement('div');
        particle.className = 'particle';

        // Random position and properties
        particle.style.left = Math.random() * 100 + '%';
        particle.style.animationDelay = delay + 's';
        particle.style.animationDuration = (3 + Math.random() * 3) + 's';

        // Random colors
        const colors = ['#0078d4', '#00bcf2', '#ffd700', '#7fba00', '#f25022'];
        particle.style.background = colors[Math.floor(Math.random() * colors.length)];
        particle.style.boxShadow = `0 0 6px ${particle.style.background}`;

        // Random sizes
        const size = 2 + Math.random() * 4;
        particle.style.width = size + 'px';
        particle.style.height = size + 'px';

        this.particlesContainer.appendChild(particle);
    }

    burstParticles(x, y, count = 20) {
        for (let i = 0; i < count; i++) {
            const particle = document.createElement('div');
            particle.style.cssText = `
                position: fixed;
                width: 8px;
                height: 8px;
                background: ${['#0078d4', '#00bcf2', '#ffd700', '#7fba00'][Math.floor(Math.random() * 4)]};
                border-radius: 50%;
                left: ${x}px;
                top: ${y}px;
                pointer-events: none;
                z-index: 10000;
                box-shadow: 0 0 10px currentColor;
            `;

            document.body.appendChild(particle);

            const angle = (Math.PI * 2 * i) / count + Math.random() * 0.5;
            const velocity = 100 + Math.random() * 200;
            const vx = Math.cos(angle) * velocity;
            const vy = Math.sin(angle) * velocity;

            let posX = x;
            let posY = y;
            let opacity = 1;

            const animate = () => {
                posX += vx * 0.02;
                posY += vy * 0.02 + 2; // gravity
                opacity -= 0.02;

                particle.style.left = posX + 'px';
                particle.style.top = posY + 'px';
                particle.style.opacity = opacity;

                if (opacity > 0) {
                    requestAnimationFrame(animate);
                } else {
                    particle.remove();
                }
            };

            requestAnimationFrame(animate);
        }
    }

    // ==================
    // CONFETTI
    // ==================

    launchConfetti(count = 100) {
        const colors = ['#0078d4', '#00bcf2', '#ffd700', '#7fba00', '#f25022', '#ffb900', '#8661c5'];

        for (let i = 0; i < count; i++) {
            setTimeout(() => {
                const confetti = document.createElement('div');
                confetti.className = 'confetti';
                confetti.style.left = Math.random() * 100 + '%';
                confetti.style.background = colors[Math.floor(Math.random() * colors.length)];
                confetti.style.animationDuration = (2 + Math.random() * 2) + 's';
                confetti.style.transform = `rotate(${Math.random() * 360}deg)`;

                // Random shapes
                if (Math.random() > 0.5) {
                    confetti.style.borderRadius = '0';
                    confetti.style.width = '8px';
                    confetti.style.height = '12px';
                }

                document.body.appendChild(confetti);

                setTimeout(() => confetti.remove(), 4000);
            }, i * 20);
        }
    }

    // ==================
    // AUDIO
    // ==================

    async initAudio() {
        if (this.audioContext) return;

        try {
            this.audioContext = new (window.AudioContext || window.webkitAudioContext)();
        } catch (e) {
            console.log('Web Audio not supported');
        }
    }

    async playThunder() {
        if (!this.soundEnabled) return;

        await this.initAudio();
        if (!this.audioContext) return;

        // Create thunder sound using oscillators and noise
        const duration = 0.8;
        const now = this.audioContext.currentTime;

        // Low rumble
        const rumble = this.audioContext.createOscillator();
        const rumbleGain = this.audioContext.createGain();

        rumble.type = 'sawtooth';
        rumble.frequency.setValueAtTime(40, now);
        rumble.frequency.exponentialRampToValueAtTime(20, now + duration);

        rumbleGain.gain.setValueAtTime(0.3, now);
        rumbleGain.gain.exponentialRampToValueAtTime(0.01, now + duration);

        rumble.connect(rumbleGain);
        rumbleGain.connect(this.audioContext.destination);

        rumble.start(now);
        rumble.stop(now + duration);

        // Crack sound (higher frequency burst)
        const crack = this.audioContext.createOscillator();
        const crackGain = this.audioContext.createGain();

        crack.type = 'square';
        crack.frequency.setValueAtTime(800, now);
        crack.frequency.exponentialRampToValueAtTime(100, now + 0.1);

        crackGain.gain.setValueAtTime(0.2, now);
        crackGain.gain.exponentialRampToValueAtTime(0.01, now + 0.15);

        crack.connect(crackGain);
        crackGain.connect(this.audioContext.destination);

        crack.start(now);
        crack.stop(now + 0.15);
    }

    async playSuccess() {
        if (!this.soundEnabled) return;

        await this.initAudio();
        if (!this.audioContext) return;

        const now = this.audioContext.currentTime;

        // Victory chime - ascending notes
        const notes = [523.25, 659.25, 783.99, 1046.50]; // C5, E5, G5, C6

        notes.forEach((freq, i) => {
            const osc = this.audioContext.createOscillator();
            const gain = this.audioContext.createGain();

            osc.type = 'sine';
            osc.frequency.value = freq;

            gain.gain.setValueAtTime(0, now + i * 0.1);
            gain.gain.linearRampToValueAtTime(0.2, now + i * 0.1 + 0.05);
            gain.gain.exponentialRampToValueAtTime(0.01, now + i * 0.1 + 0.4);

            osc.connect(gain);
            gain.connect(this.audioContext.destination);

            osc.start(now + i * 0.1);
            osc.stop(now + i * 0.1 + 0.5);
        });
    }

    async playClick() {
        if (!this.soundEnabled) return;

        await this.initAudio();
        if (!this.audioContext) return;

        const now = this.audioContext.currentTime;

        const osc = this.audioContext.createOscillator();
        const gain = this.audioContext.createGain();

        osc.type = 'sine';
        osc.frequency.value = 600;

        gain.gain.setValueAtTime(0.1, now);
        gain.gain.exponentialRampToValueAtTime(0.01, now + 0.05);

        osc.connect(gain);
        gain.connect(this.audioContext.destination);

        osc.start(now);
        osc.stop(now + 0.05);
    }

    // ==================
    // VICTORY CELEBRATION
    // ==================

    victory(message = 'Authentication Complete!') {
        // Lightning storm
        this.strikeLightning();
        setTimeout(() => this.strikeLightning(), 200);
        setTimeout(() => this.strikeLightning(), 500);

        // Confetti
        this.launchConfetti(150);

        // Victory sound
        this.playSuccess();

        // Show overlay
        const overlay = document.createElement('div');
        overlay.className = 'victory-overlay';
        overlay.innerHTML = `
            <div class="victory-content">
                <h1>AUTHENTICATED</h1>
                <p>${message}</p>
                <p style="margin-top: 20px; font-size: 1rem; color: #606070;">Click anywhere to continue</p>
            </div>
        `;

        document.body.appendChild(overlay);

        overlay.addEventListener('click', () => {
            overlay.style.animation = 'victoryFade 0.3s ease-out reverse';
            setTimeout(() => overlay.remove(), 300);
        });

        // Auto-dismiss after 3 seconds
        setTimeout(() => {
            if (overlay.parentNode) {
                overlay.style.animation = 'victoryFade 0.3s ease-out reverse';
                setTimeout(() => overlay.remove(), 300);
            }
        }, 3000);
    }

    // ==================
    // SETUP
    // ==================

    setupTitleClick() {
        const title = document.querySelector('.hero h1');
        if (title) {
            title.addEventListener('click', (e) => {
                this.strikeLightning();
                this.burstParticles(e.clientX, e.clientY, 30);
            });
        }
    }

    setupKeyboardShortcuts() {
        document.addEventListener('keydown', (e) => {
            // L for Lightning
            if (e.key.toLowerCase() === 'l' && !e.ctrlKey && !e.metaKey) {
                this.strikeLightning();
            }
            // C for Confetti
            if (e.key.toLowerCase() === 'c' && !e.ctrlKey && !e.metaKey) {
                this.launchConfetti(100);
            }
            // V for Victory
            if (e.key.toLowerCase() === 'v' && !e.ctrlKey && !e.metaKey) {
                this.victory('Keyboard shortcut activated!');
            }
            // M to toggle sound
            if (e.key.toLowerCase() === 'm') {
                this.soundEnabled = !this.soundEnabled;
                console.log('Sound:', this.soundEnabled ? 'ON' : 'OFF');
            }
        });
    }
}

// Initialize effects when DOM is ready
let zeusEffects;
document.addEventListener('DOMContentLoaded', () => {
    zeusEffects = new ZeusEffects();

    // Expose globally for the main app to use
    window.zeusEffects = zeusEffects;
});

// Export for module usage
if (typeof module !== 'undefined' && module.exports) {
    module.exports = ZeusEffects;
}
