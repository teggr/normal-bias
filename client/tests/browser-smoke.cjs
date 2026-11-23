/* Exercises the generated Java client against a real, disposable Spring Boot API.
 * No personal audio or existing data directory is read. Requires FFmpeg and Chromium.
 */
const assert = require('node:assert/strict');
const {spawn, spawnSync} = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const net = require('node:net');
const {chromium} = require('playwright-core');

const repo = path.resolve(__dirname, '../..');
const work = fs.mkdtempSync(path.join(os.tmpdir(), 'normal-bias-browser-'));
let server, browser, currentPage;
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
async function until(check, description, timeout = 20000) {
    const end = Date.now() + timeout;
    while (Date.now() < end) { if (await check()) return; await delay(100); }
    throw new Error('Timed out: ' + description);
}
async function click(page, name, index = 0, role = 'button') {
    const item = page.getByRole(role, {name, exact: true}).nth(index);
    await item.waitFor();
    // CN1 draws the controls on canvas. Its accessibility mirrors deliberately
    // have pointer-events:none, so send real pointer input at their current bounds.
    const box = await item.boundingBox();
    assert.ok(box, 'Control bounds: ' + name);
    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
    // Compact Now Playing intentionally removes the radio strip; its callers
    // assert the dedicated player instead. Other tab transitions retain it.
    if (role === 'radio' && name !== 'Now Playing') {
        await until(() => item.getAttribute('aria-checked').then(value => value === 'true'), name + ' selected');
    }
    if (process.env.RECORD_DEMO) await delay(650); // Readable evidence pacing, outside asserted seek timing.
}
async function text(page, value) {
    await until(() => page.getByText(value, {exact:true}).count().then(n => n > 0), value);
}
async function search(page, query, submitWithEnter = false) {
    const box = await page.getByRole('textbox').first().boundingBox();
    await page.mouse.click(box.x + 20, box.y + box.height / 2);
    const editor = page.locator('input:focus, textarea:focus');
    await editor.waitFor({timeout:5000});
    await editor.fill(query);
    if (submitWithEnter) {
        await page.keyboard.press('Enter');
        return;
    }
    await page.keyboard.press('Tab');
    await click(page, 'Search');
}
async function audio(page) {
    return page.locator('audio').evaluateAll(items => items.map(el => ({
        src: el.currentSrc || el.src, time: el.currentTime, duration: el.duration,
        paused: el.paused, ended: el.ended, ready: el.readyState, error: el.error?.code
    })));
}
async function seed(origin) {
    const base = path.join(work, 'base.mp3');
    let result = spawnSync('ffmpeg', ['-hide_banner','-loglevel','error','-f','lavfi','-i',
        'sine=frequency=440:duration=12','-codec:a','libmp3lame','-b:a','64k','-y',base]);
    assert.equal(result.status, 0, 'FFmpeg synthetic fixture');
    const cover = path.join(work, 'cover.png');
    result = spawnSync('ffmpeg', ['-hide_banner','-loglevel','error','-f','lavfi','-i',
        'color=c=0xdd3366:s=320x240','-frames:v','1','-y',cover]);
    assert.equal(result.status, 0, 'synthetic embedded cover');
    for (let i = 0; i < 28; i++) {
        const filename = `tone-${String(i).padStart(2,'0')}.mp3`;
        const file = path.join(work, filename);
        result = spawnSync('ffmpeg', ['-hide_banner','-loglevel','error','-i',base,...(i === 0 ? ['-i',cover,'-map','0:a','-map','1:v','-disposition:v','attached_pic'] : []),'-codec','copy',
            '-metadata',`title=Synthetic track ${String(i).padStart(2,'0')}`,
            '-metadata',`artist=${i < 27 ? 'Normal Bias tests' : 'Second artist'}`,'-metadata','album=Browser fixtures','-y',file]);
        assert.equal(result.status, 0);
        const response = await fetch(origin + '/api/tracks?filename=' + filename, {
            method:'POST', headers:{'Content-Type':'audio/mpeg'}, body:fs.readFileSync(file)
        });
        assert.equal(response.status, 201);
    }
}
(async () => {
    const port = await new Promise(resolve => {
        const socket = net.createServer();
        socket.listen(0, '127.0.0.1', () => { const port = socket.address().port; socket.close(() => resolve(port)); });
    });
    const origin = `http://127.0.0.1:${port}`;
    const output = fs.openSync(path.join(work, 'api.log'), 'w');
    server = spawn('java', ['-jar', path.join(repo,'api/target/normal-bias-api-0.1.0-SNAPSHOT.jar'),
        '--server.port=' + port, '--normalbias.auth.mode=demo', '--normalbias.artwork.background-enabled=false', '--normalbias.data-dir=' + path.join(work,'data'),
        '--spring.web.resources.static-locations=file:' + path.join(repo,'client/target/web/')],
        {stdio:['ignore',output,output]});
    await until(async () => {
        try { return (await fetch(origin + '/api/tracks')).ok; } catch { return false; }
    }, 'API startup');
    browser = await chromium.launch({executablePath:process.env.CHROMIUM_PATH || '/usr/bin/chromium',
        headless:true, args: [...(process.env.CI ? ['--no-sandbox'] : []), '--window-size=1280,1000']});
    const context = await browser.newContext({viewport:{width:1180,height:780}, serviceWorkers:'block'});
    const page = await context.newPage();
    currentPage = page;
    const errors = [];
    const ranges = [];
    const covers = [];
    page.on('response', response => { if (new URL(response.url()).pathname.endsWith('/artwork')) covers.push(response); });
    page.on('pageerror', error => errors.push(String(error)));
    page.on('response', response => {
        if (response.url().includes('/audio') && response.status() === 206) ranges.push(response.url());
    });
    await page.goto(origin);
    await text(page, 'Your library is empty. Upload some MP3s to begin.');
    await seed(origin);
    await click(page, 'Reset');
    await text(page, '1–25 of 28');
    await click(page, 'More');
    await text(page, '26–28 of 28');
    await click(page, 'Back');
    await text(page, '1–25 of 28');
    await search(page, 'does not exist');
    await text(page, 'No tracks match your search.');
    await search(page, 'track 00');
    await text(page, '1–1 of 1');
    await click(page, 'Reset');
    await text(page, '1–25 of 28');
    console.log('PASS empty library, pagination, search, no matches and reset');

    assert.equal(await page.getByText('All songs', {exact:true}).count(), 1, 'no duplicate root heading');
    const searchBounds = await page.getByRole('textbox').first().boundingBox();
    const tabBounds = await page.getByRole('radio', {name:'Library', exact:true}).boundingBox();
    assert.ok(searchBounds.y > tabBounds.y + tabBounds.height, 'search is inside Library beneath tabs');
    await page.screenshot({path:path.join(repo,'client/target/library-layout-root.png')});
    for (const submitWithEnter of [false, true]) {
        await search(page, 'track 00', submitWithEnter);
        await text(page, '1–1 of 1');
        await click(page, 'Queue (0)', 0, 'radio');
        await until(() => page.getByRole('button', {name:'Search', exact:true}).count().then(count => count === 0), 'Queue has no library search');
        await click(page, 'Library', 0, 'radio');
        await text(page, '1–1 of 1');
        await click(page, 'Reset');
        await text(page, '1–25 of 28');
    }
    console.log('PASS Library-only search, preserved query, Enter/Reset and no duplicate heading');

    const stopCapture = process.env.RECORD_DEMO ? await require('./capture-demo.cjs').start(page, path.resolve(process.env.RECORD_DEMO)) : null;
    await click(page, 'Queue (0)', 0, 'radio');
    await until(() => page.getByRole('button', {name:'Search', exact:true}).count().then(count => count === 0), 'Queue has no library search');
    await delay(700);
    await page.screenshot({path:path.join(repo,'client/target/library-layout-queue.png')});
    await click(page, 'Library', 0, 'radio');
    await click(page, 'Albums');
    await text(page, '1–2 of 2');
    await page.screenshot({path:path.join(repo,'client/target/library-albums.png')});
    await click(page, 'Open Browser fixtures');
    await text(page, '1–25 of 27');
    await click(page, 'More');
    await text(page, '26–27 of 27');
    await click(page, 'Library views');
    await text(page, '1–2 of 2');
    await click(page, 'Artists');
    await text(page, '1–2 of 2');
    await page.screenshot({path:path.join(repo,'client/target/library-artists.png')});
    await click(page, 'Open Second artist');
    await text(page, '1–1 of 1');
    await click(page, 'Open Browser fixtures');
    await page.getByRole('button', {name:'Play Synthetic track 27', exact:true}).waitFor();
    await click(page, 'Library views');
    await click(page, 'Library views');
    await page.getByRole('button', {name:'Open Normal Bias tests', exact:true}).waitFor();
    await search(page, 'missing artist');
    await text(page, 'No artists found.');
    await click(page, 'Reset');
    await click(page, 'All songs');
    await text(page, '1–25 of 28');
    console.log('PASS album/artist grouping, drill-down, paging, back and empty search');
    await click(page, '+ Queue', 0);
    await text(page, 'Queue (1)');
    await click(page, '+ Queue', 0);
    await text(page, 'Queue (1)'); // duplicate does not grow the queue
    await click(page, '+ Queue', 1);
    await text(page, 'Queue (2)');
    await click(page, 'Play Synthetic track 00');
    await until(async () => (await audio(page)).some(a => !a.paused && a.time > 0.3), 'audio advancing');
    const firstSrc = (await audio(page))[0].src;
    await click(page, 'Artists');
    await click(page, 'Open Normal Bias tests');
    await click(page, 'Open Browser fixtures');
    await until(async () => (await audio(page)).some(a => a.src === firstSrc && !a.paused), 'browsing preserves playing track');
    await text(page, 'Queue (2)');
    await click(page, 'All songs');
    await until(() => Promise.resolve(covers.some(r => r.status() === 200)), 'embedded artwork delivered');
    const embedded = covers.find(r => r.status() === 200);
    assert.equal(embedded.headers()['content-type'], 'image/png');
    assert.ok(covers.some(r => r.status() === 404), 'missing covers use fallback sleeves');
    await delay(400);
    await page.screenshot({path:path.join(repo,'client/target/browser-artwork.png')});
    console.log('PASS embedded cover and missing-art fallback endpoints');
    await click(page, 'Pause');
    await until(async () => (await audio(page))[0].paused, 'pause');
    const pausedAt = (await audio(page))[0].time;
    await delay(600);
    assert.ok(Math.abs((await audio(page))[0].time - pausedAt) < 0.1, 'paused time stays still');
    await click(page, 'Play'); // central transport retains its accessible action name
    await until(async () => !(await audio(page))[0].paused, 'resume');
    const seek = await page.getByRole('slider').boundingBox();
    await page.mouse.click(seek.x + seek.width * 0.6, seek.y + seek.height / 2);
    await until(async () => Math.abs((await audio(page))[0].time - 7.2) < 1, 'seek promptly reaches the chosen position', 2000);
    await click(page, 'Next');
    await until(async () => (await audio(page)).some(a => a.src !== firstSrc && !a.paused), 'queue next');
    await click(page, 'Previous');
    await until(async () => (await audio(page)).some(a => a.src === firstSrc && !a.paused), 'queue previous');
    assert.equal((await audio(page)).length, 1, 'only one media resource remains');
    const seekAgain = await page.getByRole('slider').boundingBox();
    await page.mouse.click(seekAgain.x + seekAgain.width * 0.96, seekAgain.y + seekAgain.height / 2);
    await until(async () => (await audio(page)).some(a => a.src !== firstSrc && !a.paused), 'completion advances queue');
    assert.ok(ranges.length > 0, 'browser received real HTTP 206 audio');
    console.log('PASS real audio progress, pause/resume, seek, next/previous and completion advance');

    await page.route('**/api/tracks/*/audio', route => route.fulfill({status:404,body:''}));
    await click(page, 'Retry track');
    await until(async () => /Cannot play this track\.|Playback interrupted\./.test(await page.locator('body').innerText()), 'audio failure visible');
    await page.unroute('**/api/tracks/*/audio');
    await click(page, 'Retry track');
    await until(async () => (await audio(page)).some(a => !a.paused && a.time > 0), 'audio retry');
    await page.route('**/api/tracks?**', route => route.fulfill({status:503,body:''}));
    await click(page, 'Reset');
    await text(page, 'Library unavailable');
    await page.unroute('**/api/tracks?**');
    await click(page, 'Retry library');
    await text(page, '1–25 of 28');
    console.log('PASS audio failure/retry and catalogue failure/retry');
    await click(page, 'Queue (2)', 0, 'radio');
    await until(() => page.getByRole('radio', {name:'Queue (2)', exact:true}).getAttribute('aria-checked').then(value => value === 'true'), 'queue tab selected');
    await click(page, 'Clear queue');
    await text(page, 'Queue (0)');
    await until(async () => (await audio(page)).length === 0, 'clear releases audio');
    await click(page, 'Library', 0, 'radio');
    await until(() => page.getByRole('radio', {name:'Library', exact:true}).getAttribute('aria-checked').then(value => value === 'true'), 'library tab selected');
    await until(async () => {
        const bounds = await page.getByRole('button', {name:'Play Synthetic track 00', exact:true}).first().boundingBox();
        return bounds && bounds.x > 0 && bounds.x + bounds.width < 1180;
    }, 'library tab transition complete');
    // Allow the canvas repaint to finish after the tab animation.
    await delay(600);
    await page.screenshot({path:path.join(repo,'client/target/browser-smoke.png')});
    await page.setViewportSize({width:390,height:844});
    await until(() => page.getByRole('radio', {name:'Now Playing', exact:true}).count().then(n => n === 1), 'compact player tab');
    await delay(600);
    await click(page, '+ Queue', 0);
    await text(page, 'Queue (1)');
    await click(page, 'Play Synthetic track 00');
    await until(async () => (await audio(page)).some(a => !a.paused && a.time > 0.2), 'compact playback');
    await click(page, 'Now Playing', 0, 'radio');
    await until(async () => {
        const box = await page.getByRole('button', {name:'Back to library', exact:true}).boundingBox();
        return box && box.x >= 0 && box.y >= 0 && box.y < 100;
    }, 'compact player opens without browsing chrome');
    await delay(600);
    // A focused control or scroll near the wheel must not scroll away navigation.
    await page.setViewportSize({width:390,height:600});
    await delay(300);
    const seekBeforeScroll = await page.getByRole('slider').boundingBox();
    const headerBefore = await page.getByRole('button', {name:'Back to library', exact:true}).boundingBox();
    await page.mouse.move(180, 450);
    await page.mouse.down();
    await page.mouse.move(180, 180, {steps:15});
    await page.mouse.up();
    await delay(300);
    const headerAfter = await page.getByRole('button', {name:'Back to library', exact:true}).boundingBox();
    assert.ok(Math.abs(headerBefore.y - headerAfter.y) < 2, 'player header stays pinned while face scrolls');
    const seekAfterScroll = await page.getByRole('slider').boundingBox();
    assert.ok(seekAfterScroll.y < seekBeforeScroll.y - 20, 'short player body actually scrolled');
    await page.screenshot({path:path.join(repo,'client/target/player-header-short.png')});
    await click(page, 'Player information');
    await page.getByRole('button', {name:'Close', exact:true}).waitFor();
    await click(page, 'Close');
    await click(page, 'Back to library');
    await page.setViewportSize({width:390,height:844});
    await page.getByRole('button', {name:'All songs', exact:true}).waitFor();
    await click(page, 'Now Playing', 0, 'radio');
    await click(page, 'Pause');
    await until(async () => (await audio(page))[0].paused, 'compact pause');
    const compactSeek = await page.getByRole('slider').boundingBox();
    await page.mouse.click(compactSeek.x + compactSeek.width * 0.55, compactSeek.y + compactSeek.height / 2);
    await until(async () => Math.abs((await audio(page))[0].time - 6.6) < 0.8, 'paused compact seek changes position promptly', 2000);
    assert.equal((await audio(page))[0].paused, true, 'seeking does not resume paused playback');
    await page.screenshot({path:path.join(repo,'client/target/browser-compact.png')});
    await click(page, 'Open queue');
    await until(() => page.getByRole('radio', {name:'Queue (1)', exact:true}).getAttribute('aria-checked').then(v => v === 'true'), 'ellipsis opens queue directly');
    assert.equal(await page.getByRole('dialog').count(), 0, 'ellipsis does not open a popup');
    await click(page, 'Now Playing', 0, 'radio');
    await until(async () => {
        const box = await page.getByRole('button', {name:'Back to library', exact:true}).boundingBox();
        return box && box.x >= 0 && box.y >= 0 && box.y < 100;
    }, 'player restored after ellipsis navigation');
    await delay(600);
    await click(page, 'Queue', 0);
    await until(() => page.getByRole('radio', {name:'Queue (1)', exact:true}).getAttribute('aria-checked').then(v => v === 'true'), 'wheel opens queue');
    await click(page, 'Clear queue');
    await text(page, 'Queue (0)');
    assert.equal(await page.getByRole('button', {name:'Search', exact:true}).count(), 0, 'compact Queue has no search');
    await click(page, 'Library', 0, 'radio');
    await search(page, 'track 00');
    await text(page, '1–1 of 1');
    await click(page, 'Reset');
    await text(page, '1–25 of 28');
    await click(page, 'Artists');
    await click(page, 'Open Normal Bias tests');
    await click(page, 'Open Browser fixtures');
    await text(page, '1–25 of 27');
    await page.screenshot({path:path.join(repo,'client/target/library-compact.png')});
    await click(page, 'All songs');
    await page.route('**/api/library/albums?*', route => route.fulfill({status:503, body:'unavailable'}));
    await click(page, 'Albums');
    await text(page, 'Library unavailable');
    await page.unroute('**/api/library/albums?*');
    await click(page, 'Retry library');
    await text(page, '1–2 of 2');
    await click(page, 'All songs');
    console.log('PASS compact grouped navigation and grouped request failure/retry');
    await page.setViewportSize({width:1180,height:780});
    await until(() => page.getByRole('radio', {name:'Now Playing', exact:true}).count().then(n => n === 0), 'wide player restored');
    await click(page, 'Library', 0, 'radio');
    await delay(600);
    await click(page, '+ Queue', 0);
    await text(page, 'Queue (1)');
    console.log('PASS compact/wide resize, queue actions and wheel navigation');
    assert.deepEqual(errors, [], 'no uncaught page errors');
    console.log('PASS clear releases audio; no uncaught page errors');
    if (stopCapture) await stopCapture();
    console.log('Browser smoke PASS');
})().catch(async error => {
    console.error(error);
    process.exitCode = 1;
    if (currentPage) {
        console.error((await currentPage.locator('body').innerText()).slice(-2200));
        await currentPage.screenshot({path:path.join(repo,'client/target/browser-smoke-failure.png')});
    }
}).finally(async () => {
    if (browser) await browser.close();
    if (server) {
        server.kill('SIGTERM');
        await Promise.race([new Promise(resolve => server.once('exit',resolve)),delay(5000)]);
        if (server.exitCode === null) server.kill('SIGKILL');
    }
    // Keep failed-run evidence; successful disposable libraries can be removed.
    if (!process.exitCode) fs.rmSync(work,{recursive:true,force:true});
});
