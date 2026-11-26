/* Acceptance/optional actual-audio recording of an explicitly selected private HTTP demo. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {chromium} = require('playwright-core');
const origin = process.env.NORMALBIAS_HTTP_DEMO_ORIGIN;
assert.ok(origin, 'Select the authorised private demo with NORMALBIAS_HTTP_DEMO_ORIGIN');
const url = new URL(origin);
const octets = url.hostname.split('.').map(Number);
assert.ok(url.protocol === 'http:' && octets.length === 4 && octets.every(n => Number.isInteger(n) && n >= 0 && n <= 255)
    && (octets[0] === 10 || octets[0] === 172 && octets[1] >= 16 && octets[1] <= 31 || octets[0] === 192 && octets[1] === 168)
    && url.origin === origin, 'Use an explicit RFC1918 HTTP origin');
const output = path.resolve(__dirname, '../target/recordings/private-http-demo');
const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
let browser, page, stop;
async function until(check, label) {
    const end = Date.now() + 15000;
    while (Date.now() < end) { if (await check()) return; await wait(100); }
    throw new Error('Timed out: ' + label);
}
async function click(name) {
    const item = page.getByRole('button', {name, exact:true});
    await item.waitFor();
    const b = await item.boundingBox(); assert.ok(b, name);
    await page.mouse.click(b.x + b.width / 2, b.y + b.height / 2);
    await wait(400);
}
async function audio() {
    return page.locator('audio').evaluateAll(xs => xs.map(x => ({time:x.currentTime, paused:x.paused})));
}
(async () => {
    const discovery = await (await fetch(origin + '/api')).json();
    assert.equal(discovery.authRequired, false);
    assert.equal(discovery._links['device-login'], undefined);
    browser = await chromium.launch({executablePath:process.env.CHROMIUM_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless:true});
    const context = await browser.newContext({viewport:{width:390,height:844}, serviceWorkers:'block'});
    page = await context.newPage();
    const errors = [], requests = [], events = [];
    page.on('pageerror', e => errors.push(String(e)));
    page.on('request', r => { if (new URL(r.url()).pathname.startsWith('/api')) requests.push({method:r.method(), credential:!!r.headers().authorization}); });
    await page.goto(origin);
    await page.getByRole('button', {name:'All songs',exact:true}).waitFor();
    assert.equal(await page.getByRole('button', {name:'Sign in',exact:true}).count(), 0);
    await click('Show search');
    const editor = page.locator('input:focus,textarea:focus'); await editor.waitFor();
    await editor.fill('Morning Circuit'); await page.keyboard.press('Tab'); await click('Search');
    await page.getByRole('button', {name:'Play Morning Circuit',exact:true}).waitFor();
    // Filter before capture so only the original synthetic clip appears in evidence.
    await until(() => page.getByRole('button', {name:/^Play /}).count().then(n => n === 1), 'synthetic-only results');
    fs.mkdirSync(output, {recursive:true});
    if (process.env.RECORD_DEMO) stop = await require('./capture-demo.cjs').start(page, output);
    const event = name => events.push({name, at:Date.now()/1000});
    event('filtered-library'); await wait(1500);
    await click('Play Morning Circuit'); await until(async () => (await audio())[0]?.time > 1, 'audible stream progress');
    await click('Now Playing'); event('playing'); await wait(4000);
    await click('Pause'); event('paused');
    const paused = (await audio())[0]; assert.ok(paused.paused); await wait(2500);
    assert.ok(Math.abs((await audio())[0].time - paused.time) < .2);
    await click('Play'); event('resumed'); await until(async () => (await audio())[0].time > paused.time + 1, 'resume'); await wait(3000);
    await click('Pause'); event('ending'); await wait(2000);
    assert.deepEqual(errors, []);
    assert.ok(requests.every(r => r.method === 'GET' && !r.credential), 'listening without credentials or account mutations');
    if (stop) { await stop(); stop = null; }
    fs.writeFileSync(path.join(output, 'result.json'), JSON.stringify({origin, events, errors, requests, checks:['automatic demo discovery', 'synthetic-only filter', 'playing progress', 'paused position retained', 'resume', 'no credentials or API mutations']}, null, 2));
    console.log('PASS: HTTP demo discovery, filtered browsing, play/pause/resume, no API credentials/mutations');
})().catch(async error => {
    console.error(error); process.exitCode = 1;
    if (page) { fs.mkdirSync(output, {recursive:true}); await page.screenshot({path:path.join(output,'failure.png')}); }
}).finally(async () => { if (stop) await stop().catch(() => {}); if (browser) await browser.close(); });
