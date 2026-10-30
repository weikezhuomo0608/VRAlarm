/* Visual check for the two "watch is parked" states and the schedule page that explains them:
     outside  — a normal gap between windows, with the moment the watch comes back
     no-rule  — custom mode with every rule switched off: no next window at all
   Renders the real bundle through the page's own preview mode (no native bridge), so it needs
   nothing but the built assets. Run it after touching the 时段 copy or the watch cards:
     HAZEL_TEST_CHROMIUM="<path to chrome>" node tests/shot-watch.cjs
   Writes build/shot-pause-*.png. Assertions for the same three states live in
   tests/ui-regressions.cjs ("outside the schedule the watch is paused, never interrupted"). */
const http = require('http'), fs = require('fs'), path = require('path');
const { chromium } = require('playwright');

const ROOT = path.join(__dirname, '..', 'app', 'src', 'main', 'assets');
const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.png': 'image/png' };

const server = http.createServer((req, res) => {
    const url = req.url.split('?')[0];
    if (!url.startsWith('/assets/')) { res.writeHead(403); res.end(); return; }
    const rel = url.slice('/assets/'.length);
    fs.readFile(path.join(ROOT, rel), (err, buf) => {
        if (err) { res.writeHead(404); res.end(); return; }
        res.writeHead(200, { 'content-type': types[path.extname(rel)] || 'application/octet-stream' });
        res.end(buf);
    });
});

server.listen(0, '127.0.0.1', async () => {
    const base = 'http://127.0.0.1:' + server.address().port + '/assets/';
    const browser = await chromium.launch({
        headless: true,
        ...(process.env.HAZEL_TEST_CHROMIUM ? { executablePath: process.env.HAZEL_TEST_CHROMIUM } : {}),
        args: ['--no-sandbox']
    });

    const shot = async (name, patch, extra) => {
        const page = await browser.newPage({ viewport: { width: 393, height: 900 } });
        await page.goto(base + 'index.html');
        await page.waitForTimeout(1200);
        await page.evaluate(patch);
        await page.waitForTimeout(2600);           // the state poll repaints with the patched preview
        if (extra) { await extra(page); await page.waitForTimeout(900); }
        await page.screenshot({ path: path.join(__dirname, 'shot-pause-' + name + '.png'), fullPage: false });
        const text = await page.locator('#content').innerText();
        console.log('--- ' + name + ' ---');
        console.log(text.split('\n').filter(Boolean).slice(0, 9).join(' | '));
        await page.close();
    };

    await shot('outside', () => {
        previewState.enabled = true; previewState.running = false; previewState.inside = false;
        previewState.schedulePaused = true; previewState.hasWindow = true;
        previewState.nextBoundary = Date.now() + 3 * 3600000;
        previewState.config.allDay = false;
        previewState.watchNotification = { status: 'paused' };
    });

    await shot('no-rule', () => {
        previewState.enabled = true; previewState.running = false; previewState.inside = false;
        previewState.schedulePaused = true; previewState.hasWindow = false; previewState.nextBoundary = 0;
        previewState.config.allDay = false;
        previewState.config.windows = [{ id: 'night', name: '凌晨守候', start: 60, end: 360, days: 127, enabled: false }];
        previewState.watchNotification = { status: 'paused' };
    });

    await shot('schedule-page', () => {
        previewState.enabled = true; previewState.running = false; previewState.inside = false;
        previewState.schedulePaused = true; previewState.hasWindow = false; previewState.nextBoundary = 0;
        previewState.config.allDay = false;
        previewState.config.windows = [{ id: 'night', name: '凌晨守候', start: 60, end: 360, days: 127, enabled: false }];
    }, async page => { await page.locator('[data-route="schedule"]').last().click(); });

    await browser.close(); server.close(); process.exit(0);
});
