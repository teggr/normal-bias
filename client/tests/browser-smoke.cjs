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
    if (name === 'Library' && await page.getByRole('button',{name:'Back to Now Playing',exact:true}).count()) await click(page,'Back to Now Playing');
    if (name === 'Now Playing' && await page.getByRole('button', {name:'Back to Now Playing',exact:true}).count()) name = 'Back to Now Playing';
    if (name === 'Library' && await page.getByRole('button', {name:'All songs',exact:true}).count()) return;
    if (['Search','Reset'].includes(name) && !(await page.getByRole('button',{name,exact:true}).count())) await click(page,'Show search');
    const item = page.getByRole(role, {name, exact: true}).nth(index);
    await item.waitFor();
    const box = await item.boundingBox();
    assert.ok(box, 'Control bounds: ' + name);
    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
    await delay(400); // Wait for CN1 to reconcile canvas semantics after replacing a view.
    if (process.env.RECORD_DEMO) await delay(650); // Readable evidence pacing, outside asserted seek timing.
}
async function openQueue(page) {
    if (await page.getByRole('button', {name:'Clear queue',exact:true}).count()) return;
    if (await page.getByRole('button', {name:'All songs',exact:true}).count()) await click(page,'Now Playing');
    await click(page,'Queue');
    await page.getByRole('button', {name:'Back to Now Playing',exact:true}).waitFor();
}
async function assertQueue(page, count) {
    const library = await page.getByRole('button', {name:'All songs',exact:true}).count();
    const queue = await page.getByRole('button', {name:'Clear queue',exact:true}).count();
    if (!queue) await openQueue(page);
    await text(page, count + (count === 1 ? ' track' : ' tracks'));
    if (!queue) await click(page, library ? 'Library' : 'Back to Now Playing');
}
async function text(page, value) {
    await until(() => page.getByText(value, {exact:true}).count().then(n => n > 0), value);
}
async function search(page, query, submitWithEnter = false) {
    if (!(await page.getByRole('textbox').count())) await click(page,'Show search');
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
async function scrollToSong(page, title) {
    await until(async () => {
        const button=page.getByRole('button',{name:'Play '+title,exact:true});
        const box=await button.count()?await button.boundingBox():null;
        if(box && box.y>180 && box.y+box.height<page.viewportSize().height-95) return true;
        await page.mouse.move(230,420); await page.mouse.wheel(0,320); await delay(300);
        return false;
    },'scroll to '+title);
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
    await page.getByRole('button',{name:'Play Synthetic track 00',exact:true}).waitFor();
    assert.equal(await page.getByRole('button',{name:'More',exact:true}).count(),0);
    await scrollToSong(page, 'Synthetic track 27');
    await click(page, 'All songs');
    await page.getByRole('button',{name:'Play Synthetic track 00',exact:true}).waitFor();
    assert.equal(await page.getByRole('button', {name:'All songs', exact:true}).count(), 1, 'one root category selector');
    const searchBounds = await page.getByRole('textbox').first().boundingBox();
    const tabBounds = await page.getByRole('button', {name:'Now Playing', exact:true}).first().boundingBox();
    assert.ok(searchBounds.y > tabBounds.y + tabBounds.height, 'search is inside Library beneath compact toolbar');
    await page.screenshot({path:path.join(repo,'client/target/library-layout-root.png')});
    for (const submitWithEnter of [false, true]) {
        await search(page, 'track 00', submitWithEnter);
        await until(async () => await page.getByRole('button',{name:/^(Play Synthetic track 00|Open Browser fixtures)$/}).count() === 1, 'one filtered result');
        await openQueue(page);
        await until(() => page.getByRole('button', {name:'Search', exact:true}).count().then(count => count === 0), 'Queue has no library search');
        await click(page, 'Library');
        await until(async () => await page.getByRole('button',{name:/^(Play Synthetic track 00|Open Browser fixtures)$/}).count() === 1, 'one filtered result');
        await click(page, 'Reset');
        await page.getByRole('button',{name:'Play Synthetic track 00',exact:true}).waitFor();
    }
    console.log('PASS Library-only search, preserved query, Enter/Reset and no duplicate heading');

    const stopCapture = process.env.RECORD_DEMO ? await require('./capture-demo.cjs').start(page, path.resolve(process.env.RECORD_DEMO)) : null;
    await openQueue(page);
    await until(() => page.getByRole('button', {name:'Search', exact:true}).count().then(count => count === 0), 'Queue has no library search');
    await delay(700);
    await page.screenshot({path:path.join(repo,'client/target/library-layout-queue.png')});
    await click(page, 'Library');
    await click(page, 'Albums');
    await page.getByRole('button',{name:'Open Browser fixtures',exact:true}).first().waitFor().catch(() => page.getByRole('button',{name:'Open Normal Bias tests',exact:true}).waitFor());
    await page.screenshot({path:path.join(repo,'client/target/library-albums.png')});
    await click(page, 'Open Browser fixtures');
    await page.getByRole('button',{name:'Play Synthetic track 00',exact:true}).waitFor();
    await scrollToSong(page, 'Synthetic track 26');
    await click(page, 'Library views');
    await page.getByRole('button',{name:'Open Browser fixtures',exact:true}).first().waitFor().catch(() => page.getByRole('button',{name:'Open Normal Bias tests',exact:true}).waitFor());
    await click(page, 'Artists');
    await page.getByRole('button',{name:'Open Browser fixtures',exact:true}).first().waitFor().catch(() => page.getByRole('button',{name:'Open Normal Bias tests',exact:true}).waitFor());
    await page.screenshot({path:path.join(repo,'client/target/library-artists.png')});
    await click(page, 'Open Second artist');
    await until(async () => await page.getByRole('button',{name:/^(Play Synthetic track 00|Open Browser fixtures)$/}).count() === 1, 'one filtered result');
    await click(page, 'Open Browser fixtures');
    await page.getByRole('button', {name:'Play Synthetic track 27', exact:true}).waitFor();
    await click(page, 'Library views');
    await click(page, 'Library views');
    await page.getByRole('button', {name:'Open Normal Bias tests', exact:true}).waitFor();
    await search(page, 'missing artist');
    await text(page, 'No artists found.');
    await click(page, 'Reset');
    await click(page, 'All songs');
    await page.getByRole('button',{name:'Play Synthetic track 00',exact:true}).waitFor();
    console.log('PASS album/artist grouping, drill-down, continuous scrolling, back and empty search');
    await click(page, '+ Queue', 0);
    await assertQueue(page, 1);
    await click(page, '+ Queue', 0);
    await assertQueue(page, 1); // duplicate does not grow the queue
    await click(page, '+ Queue', 1);
    await assertQueue(page, 2);
    await click(page, 'Play Synthetic track 00');
    await until(async () => (await audio(page)).some(a => !a.paused && a.time > 0.3), 'audio advancing');
    const firstSrc = (await audio(page))[0].src;
    await click(page, 'Artists');
    await click(page, 'Open Normal Bias tests');
    await click(page, 'Open Browser fixtures');
    await until(async () => (await audio(page)).some(a => a.src === firstSrc && !a.paused), 'browsing preserves playing track');
    await assertQueue(page, 2);
    await click(page, 'All songs');
    await until(() => Promise.resolve(covers.some(r => r.status() === 200)), 'embedded artwork delivered');
    const embedded = covers.find(r => r.status() === 200);
    assert.equal(embedded.headers()['content-type'], 'image/png');
    assert.ok(covers.some(r => r.status() === 404), 'missing covers use fallback sleeves');
    await delay(400);
    await page.screenshot({path:path.join(repo,'client/target/browser-artwork.png')});
    console.log('PASS embedded cover and missing-art fallback endpoints');
    await click(page, 'Play Synthetic track 00');
    await until(async () => (await audio(page)).some(a => a.src === firstSrc && !a.paused && a.time > 0.2 && a.time < 3), 'fresh track for timed transport checks');
    await click(page, 'Now Playing');
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
    await click(page, 'Previous'); // A new stream fails; Retry is absent during healthy playback.
    await until(async () => /Cannot play this track\.|Playback interrupted\./.test(await page.locator('body').innerText()), 'audio failure visible');
    await page.unroute('**/api/tracks/*/audio');
    await click(page, 'Retry track');
    await until(async () => (await audio(page)).some(a => !a.paused && a.time > 0), 'audio retry');
    await click(page, 'Library');
    await page.route('**/api/tracks?**', route => route.fulfill({status:503,body:''}));
    await click(page, 'Reset');
    await until(async () => (await page.locator('body').innerText()).includes('Library unavailable'), 'library error');
    await page.unroute('**/api/tracks?**');
    await click(page, 'Retry library');
    await page.getByRole('button',{name:'Play Synthetic track 00',exact:true}).waitFor();
    console.log('PASS audio failure/retry and catalogue failure/retry');
    await openQueue(page);
    await page.getByRole('button', {name:'Back to Now Playing', exact:true}).waitFor();
    await click(page, 'Clear queue');
    await assertQueue(page, 0);
    await until(async () => (await audio(page)).length === 0, 'clear releases audio');
    await click(page, 'Library');
    await page.getByRole('button', {name:'Search', exact:true}).waitFor();
    await until(async () => {
        const bounds = await page.getByRole('button', {name:'Play Synthetic track 00', exact:true}).first().boundingBox();
        return bounds && bounds.x > 0 && bounds.x + bounds.width < 1180;
    }, 'library tab transition complete');
    // Allow the canvas repaint to finish after the tab animation.
    await delay(600);
    await page.screenshot({path:path.join(repo,'client/target/browser-smoke.png')});
    await page.setViewportSize({width:390,height:844});
    await page.getByRole('button', {name:'Now Playing', exact:true}).waitFor();
    await delay(600);
    await click(page, '+ Queue', 0);
    await assertQueue(page, 1);
    await click(page, 'Play Synthetic track 00');
    await until(async () => (await audio(page)).some(a => !a.paused && a.time > 0.2), 'compact playback');
    await click(page, 'Now Playing');
    await until(async () => {
        const box = await page.getByRole('button', {name:'Library', exact:true}).first().boundingBox();
        return box && box.x >= 0 && box.y >= 0 && box.y < 100;
    }, 'compact player opens without browsing chrome');
    await delay(600);
    // A focused control or scroll near the wheel must not scroll away navigation.
    await page.setViewportSize({width:390,height:600});
    await delay(700);
    const seekBeforeScroll = await page.getByRole('slider').boundingBox();
    const headerBefore = await page.getByRole('button', {name:'Library', exact:true}).first().boundingBox();
    for (let attempt=0;attempt<3;attempt++) {
        await page.mouse.move(180, 450);
        await page.mouse.down();
        await page.mouse.move(180, 180, {steps:15});
        await page.mouse.up();
        await delay(500);
        const moved=await page.getByRole('slider').boundingBox();
        if (moved.y < seekBeforeScroll.y-20) break;
    }
    const headerAfter = await page.getByRole('button', {name:'Library', exact:true}).first().boundingBox();
    assert.ok(Math.abs(headerBefore.y - headerAfter.y) < 2, 'player header stays pinned while face scrolls');
    const seekAfterScroll = await page.getByRole('slider').boundingBox();
    assert.ok(seekAfterScroll.y < seekBeforeScroll.y - 20, 'short player body actually scrolled');
    await page.screenshot({path:path.join(repo,'client/target/player-header-short.png')});
    // A tap while the canvas drag is decelerating stops the scroll rather than activating a key.
    await until(async () => {
        const a = await page.getByRole('button',{name:'Player information',exact:true}).boundingBox();
        await delay(300);
        const b = await page.getByRole('button',{name:'Player information',exact:true}).boundingBox();
        return a && b && Math.abs(a.y-b.y)<1;
    }, 'player drag settled');
    await click(page, 'Player information');
    if (await page.getByRole('button',{name:'Player information',exact:true}).count()) await click(page,'Player information');
    await page.getByRole('button', {name:'Show artwork', exact:true}).waitFor();
    assert.equal(await page.getByRole('dialog').count(),0,'information swaps inline');
    await click(page, 'Show artwork');
    await click(page, 'Library');
    await page.setViewportSize({width:390,height:844});
    await page.getByRole('button', {name:'All songs', exact:true}).waitFor();
    await click(page, 'Now Playing');
    await click(page, 'Pause');
    await until(async () => (await audio(page))[0].paused, 'compact pause');
    const compactSeek = await page.getByRole('slider').boundingBox();
    await page.mouse.click(compactSeek.x + compactSeek.width * 0.55, compactSeek.y + compactSeek.height / 2);
    await until(async () => Math.abs((await audio(page))[0].time - 6.6) < 0.8, 'paused compact seek changes position promptly', 2000);
    assert.equal((await audio(page))[0].paused, true, 'seeking does not resume paused playback');
    const retainedAudio = await audio(page);
    await click(page,'Library');
    assert.equal(await page.getByRole('button',{name:'Queue',exact:true}).count(),0,'no top-level Queue action');
    assert.equal(await page.getByRole('radio').count(),0,'no navigation tab strip');
    await click(page,'Now Playing');
    const returnedAudio = await audio(page);
    assert.equal(returnedAudio[0].src,retainedAudio[0].src,'navigation retains stream');
    assert.ok(Math.abs(returnedAudio[0].time-retainedAudio[0].time)<0.3,'navigation preserves paused position');
    assert.equal(returnedAudio[0].paused,true,'navigation preserves paused intent');
    await page.screenshot({path:path.join(repo,'client/target/browser-compact.png')});
    await click(page, 'Player information');
    await page.getByRole('button', {name:'Show artwork', exact:true}).waitFor();
    assert.equal(await page.getByRole('dialog').count(), 0, 'information stays inline');
    await click(page, 'Show artwork');
    assert.equal(await page.getByRole('button', {name:'Open queue',exact:true}).count(),0,'duplicate queue action removed');
    await click(page, 'Queue', 0);
    await page.getByRole('button', {name:'Back to Now Playing', exact:true}).waitFor();
    await click(page, 'Clear queue');
    await assertQueue(page, 0);
    assert.equal(await page.getByRole('button', {name:'Search', exact:true}).count(), 0, 'compact Queue has no search');
    await click(page, 'Library');
    await search(page, 'track 00');
    await until(async () => await page.getByRole('button',{name:/^(Play Synthetic track 00|Open Browser fixtures)$/}).count() === 1, 'one filtered result');
    await click(page, 'Reset');
    await page.getByRole('button',{name:'Play Synthetic track 00',exact:true}).waitFor();
    await click(page, 'Artists');
    await click(page, 'Open Normal Bias tests');
    await click(page, 'Open Browser fixtures');
    await page.getByRole('button',{name:'Play Synthetic track 00',exact:true}).waitFor();
    await page.screenshot({path:path.join(repo,'client/target/library-compact.png')});
    await click(page, 'All songs');
    await page.route('**/api/library/albums?*', route => route.fulfill({status:503, body:'unavailable'}));
    await click(page, 'Albums');
    await until(async () => (await page.locator('body').innerText()).includes('Library unavailable'), 'library error');
    await page.unroute('**/api/library/albums?*');
    await click(page, 'Retry library');
    await page.getByRole('button',{name:'Open Browser fixtures',exact:true}).first().waitFor().catch(() => page.getByRole('button',{name:'Open Normal Bias tests',exact:true}).waitFor());
    await click(page, 'All songs');
    console.log('PASS compact grouped navigation and grouped request failure/retry');
    await page.setViewportSize({width:1180,height:780});
    await page.getByRole('button', {name:'Now Playing', exact:true}).waitFor();
    await click(page, 'Library');
    await delay(600);
    await click(page, '+ Queue', 0);
    await assertQueue(page, 1);
    console.log('PASS compact/wide resize, queue actions and wheel navigation');
    async function libraryPixels(filename) {
        const file=path.join(repo,'client/target/'+filename);
        await page.screenshot({path:file});
        const decoded=spawnSync('ffmpeg',['-v','error','-i',file,'-vf','crop=1100:400:25:245','-frames:v','1','-f','rawvideo','-pix_fmt','rgb24','pipe:1'],{maxBuffer:2*1024*1024,timeout:10000});
        assert.equal(decoded.status,0,'decode actual Library canvas');
        return decoded.stdout;
    }
    const initialPixels=await libraryPixels('library-scroll-initial.png');
    await page.mouse.move(500,620);await page.mouse.down();await page.mouse.move(500,280,{steps:18});await page.mouse.up();
    // CN1's accessibility mirror can retain old scroll geometry; compare actual
    // canvas pixels after kinetic motion, rather than its clipped placeholders.
    let scrolledPixels, previousPixels, stableAt=0;
    await until(async()=>{
        scrolledPixels=await libraryPixels('library-scroll-check.png');
        if(previousPixels && scrolledPixels.equals(previousPixels)){if(!stableAt)stableAt=Date.now();}
        else stableAt=0;
        previousPixels=scrolledPixels;
        return stableAt && Date.now()-stableAt>600;
    },'actual Library canvas stops moving after drag');
    assert.ok(!initialPixels.equals(scrolledPixels),'Library actually scrolled');
    let catalogueLoads=0;
    page.on('request',r=>{if(r.method()==='GET'&&new URL(r.url()).pathname==='/api/tracks')catalogueLoads++});
    await click(page,'Now Playing');await click(page,'Library');await delay(500);
    const restoredPixels=await libraryPixels('library-scroll-restored.png');
    // Reattaching a CN1 canvas can round text/scroll geometry by one pixel.
    // Require the same rendered region within two pixels and <1/255 mean RGB error.
    const stride=1100*3;let difference=Infinity;
    for(let shift=-2;shift<=2;shift++){
        let sum=0;const start=2*stride,end=scrolledPixels.length-2*stride;
        for(let i=start;i<end;i++)sum+=Math.abs(scrolledPixels[i]-restoredPixels[i+shift*stride]);
        difference=Math.min(difference,sum/(end-start));
    }
    assert.ok(difference<1,'Library renders the same scrolled rows within two-pixel rasterisation tolerance: '+difference);
    assert.equal(catalogueLoads,0,'navigation does not reload the catalogue');
    console.log('PASS Library scroll preservation without catalogue reload');
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
