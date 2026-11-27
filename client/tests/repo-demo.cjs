/* Capture the actual generated browser app against demo-web.sh's disposable fixture.
 * Uses real pointer input and the existing actual-audio recorder. No UI replacement.
 */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {chromium} = require('playwright-core');
const {createHash} = require('node:crypto');
const repo = path.resolve(__dirname, '../..');
const origin = process.env.NORMALBIAS_DEMO_ORIGIN || 'http://127.0.0.1:18080';
assert.match(origin, /^http:\/\/127\.0\.0\.1:\d+$/);
const shots = path.join(repo, 'docs/screenshots');
const output = path.join(repo, 'client/target/recordings/repo-demo');
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
async function click(page, name) {
    const control = page.getByRole('button', {name, exact:true}).first();
    await control.waitFor();
    const box = await control.boundingBox();
    assert.ok(box, 'Visible control: ' + name);
    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
    await delay(700);
}
async function until(check, message) {
    for (let i = 0; i < 100; i++) {
        if (await check()) return;
        await delay(100);
    }
    throw new Error('Timed out: ' + message);
}
async function state(page) {
    return page.locator('audio').evaluateAll(items => items.map(a => ({time:a.currentTime, paused:a.paused, error:a.error?.code})));
}
(async () => {
    const discovery = await (await fetch(origin + '/api')).json();
    assert.equal(discovery.authRequired, false, 'Only a disposable unauthenticated demo');
    const tracks = await (await fetch(origin + '/api/tracks')).json();
    let album = 'Simulator Sessions';
    if (process.env.NORMALBIAS_DEMO_MUSIC_DIR) {
        const hashes = [];
        function visit(dir) {
            for (const entry of fs.readdirSync(dir, {withFileTypes:true})) {
                const file = path.join(dir, entry.name);
                if (entry.isDirectory()) visit(file);
                else if (entry.isFile() && /\.mp3$/i.test(entry.name)) hashes.push(createHash('sha256').update(fs.readFileSync(file)).digest('hex'));
            }
        }
        visit(process.env.NORMALBIAS_DEMO_MUSIC_DIR);
        assert.ok(hashes.length > 0 && hashes.length <= 200, 'Bounded local demo library');
        const all = await (await fetch(origin + '/api/tracks?limit=200')).json();
        assert.deepEqual(all.items.map(t => t.id).sort(), [...new Set(hashes)].sort(), 'Demo exactly matches the explicitly selected local music');
        album = all.items.find(t => t.artist === 'Roxy Music')?.album || all.items[0].album;
    } else {
        assert.equal(tracks.total, 2, 'Expected fresh two-track demo');
        assert.deepEqual(tracks.items.map(t => t.title).sort(), ['Evening Signal', 'Morning Circuit']);
        assert.ok(tracks.items.every(t => t.artist === 'Normal Bias Demo'));
    }
    fs.mkdirSync(shots, {recursive:true}); fs.mkdirSync(output, {recursive:true});
    const browser = await chromium.launch({
        executablePath:process.env.CHROMIUM_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
        headless:true
    });
    let stop;
    try {
        // Fresh context uses the real service worker, unlike smoke's blocked-worker fixture.
        const context = await browser.newContext({viewport:{width:1280,height:900}});
        const page = await context.newPage();
        const errors = []; page.on('pageerror', e => errors.push(String(e)));
        await page.goto(origin);
        await page.getByRole('button', {name:'Albums',exact:true}).waitFor({timeout:60000});
        await delay(2000);
        stop = await require('./capture-demo.cjs').start(page, output);
        const started = Date.now(); const marks = {};
        const mark = name => { marks[name] = (Date.now() - started) / 1000; };
        await delay(2000);
        await click(page, 'Albums');
        await delay(2200);
        await click(page, 'Open ' + album);
        await delay(2200);
        await click(page, 'Queue album');
        await until(() => page.getByRole('button',{name:'Play',exact:true}).count().then(n => n > 0), 'paused track ready');
        await delay(1600);
        await click(page, 'Play');
        await until(async () => (await state(page)).some(a => !a.paused && a.time > .5), 'actual audio advancing');
        mark('first');
        await delay(5000);
        await page.screenshot({path:path.join(shots, 'desktop.png')});
        await click(page, 'Queue');
        await until(() => page.getByRole('button',{name:'Clear queue',exact:true}).count().then(n => n > 0), 'queue visible');
        await delay(3000);
        await click(page, 'Pause'); mark('paused');
        await delay(3000);
        await click(page, 'Play'); mark('renewed');
        await delay(4000);
        await click(page, 'Next');
        await until(async () => (await state(page)).some(a => !a.paused && a.time > .5), 'next track playing');
        await delay(3000);
        await click(page, 'Pause'); mark('ending');
        await delay(3000);
        marks.duration = (Date.now() - started) / 1000;
        assert.deepEqual(errors, [], 'No browser exceptions');
        await stop(); stop = null;
        fs.writeFileSync(path.join(output,'flow.json'), JSON.stringify({origin, viewport:page.viewportSize(), marks, errors, serviceWorkers:'enabled'},null,2));
        console.log('PASS album browsing, paused preparation, playback, Queue, pause/resume and Next', marks);
    } finally {
        if (stop) await stop();
        await browser.close();
    }
})().catch(e => { console.error(e); process.exitCode = 1; });
