/* Compact navigation regression and optional real-client audio/video evidence. Synthetic library only. */
const assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path'), os = require('node:os'), net = require('node:net');
const {spawn, spawnSync} = require('node:child_process');
const {chromium} = require('playwright-core');
const root = path.resolve(__dirname, '../..');
const work = fs.mkdtempSync(path.join(os.tmpdir(), 'normalbias-compact-'));
const output = path.resolve(process.env.RECORDING_DIR || path.join(root, 'client/target/recordings/compact-navigation'));
const wait = ms => new Promise(r => setTimeout(r, ms));
let server, browser, page, stop;
const events = [];
async function until(check, label) {
    for (let i=0;i<200;i++) { if (await check()) return; await wait(100); }
    throw Error('Timeout: '+label);
}
async function click(name) {
    if (name === 'Library' && await page.getByRole('button',{name:'Back to Now Playing',exact:true}).count()) await click('Back to Now Playing');
    if (name === 'Search' && !(await page.getByRole('button',{name,exact:true}).count())) await click('Show search');
    const item=page.getByRole('button',{name,exact:true}).first(); await item.waitFor();
    let b=await item.boundingBox();
    if(b.y+b.height>844) {
        await page.mouse.move(195,650);await page.mouse.wheel(0,350);await wait(500);b=await item.boundingBox();
    }
    assert.ok(b && b.y>=0 && b.y+b.height<=844,'reachable '+name);
    const right=Math.min(page.viewportSize().width,b.x+b.width);
    assert.ok(right>b.x, "visible horizontal target "+name);
    await page.mouse.click((Math.max(0,b.x)+right)/2,b.y+b.height/2);await wait(500);
}
async function audio() { return page.locator('audio').evaluateAll(xs=>xs.map(x=>({src:x.currentSrc,time:x.currentTime,paused:x.paused}))); }
function event(name) { events.push({name,at:Date.now()/1000}); }
(async()=>{
    fs.mkdirSync(output,{recursive:true});
    const port=await new Promise(resolve=>{const s=net.createServer();s.listen(0,'127.0.0.1',()=>{const p=s.address().port;s.close(()=>resolve(p))})});
    const origin='http://127.0.0.1:'+port;
    const log=fs.openSync(path.join(work,'api.log'),'w');
    server=spawn('java',['-jar',path.join(root,'api/target/normal-bias-api-0.1.0-SNAPSHOT.jar'),'--server.port='+port,
        '--normalbias.auth.mode=demo','--normalbias.artwork.background-enabled=false','--normalbias.data-dir='+path.join(work,'data'),
        '--spring.web.resources.static-locations=file:'+path.join(root,'client/target/web/')],{stdio:['ignore',log,log]});
    await until(async()=>{try{return(await fetch(origin+'/api/tracks')).ok}catch{return false}},'API');
    for (const [i,title] of ['Morning Circuit','Evening Signal'].entries()) {
        const file=path.join(work,i+'.mp3');
        const result=spawnSync('ffmpeg',['-v','error','-f','lavfi','-i',`aevalsrc=0.10*sin(2*PI*${220+i*55}*t)+0.06*sin(2*PI*330*t)+0.04*sin(2*PI*440*t):s=44100:d=90`,
            '-c:a','libmp3lame','-b:a','128k','-metadata','title='+title,'-metadata','artist=Normal Bias synthetic trial',file]);
        assert.equal(result.status,0);
        assert.equal((await fetch(origin+'/api/tracks?filename='+i+'.mp3',{method:'POST',headers:{'Content-Type':'audio/mpeg'},body:fs.readFileSync(file)})).status,201);
    }
    browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
    const context=await browser.newContext({viewport:{width:390,height:844},serviceWorkers:'block'});
    page=await context.newPage();page.setDefaultTimeout(15000);
    let opens=0;const errors=[];
    page.on('request',r=>{if(new URL(r.url()).pathname.endsWith('/audio'))opens++});page.on('pageerror',e=>errors.push(String(e)));
    await page.goto(origin);await page.getByRole('button',{name:'Play Morning Circuit',exact:true}).waitFor();
    assert.equal(await page.getByRole('radio').count(),0);
    assert.equal(await page.getByRole('button',{name:'Queue',exact:true}).count(),0);
    assert.equal(await page.getByRole('button',{name:'Profile',exact:true}).count(),0,'demo has no account menu');
    if(process.env.RECORD_DEMO)stop=await require('./capture-demo.cjs').start(page,output);
    assert.equal(await page.getByRole('textbox').count(),0,'search starts collapsed');
    const category=await page.getByRole('button',{name:'Artists',exact:true}).boundingBox();
    const toggle=await page.getByRole('button',{name:'Show search',exact:true}).boundingBox();
    assert.ok(Math.abs(category.y+category.height/2-toggle.y-toggle.height/2)<12,'search toggle shares category row');
    event('library');await wait(1000);
    await click('Show search');await page.locator('input:focus,textarea:focus').waitFor();
    await page.locator('input:focus,textarea:focus').fill('Morning');await page.keyboard.press('Tab');await click('Search');
    await page.getByRole('button',{name:'Play Morning Circuit',exact:true}).waitFor();assert.equal(await page.getByRole('button',{name:'Play Evening Signal',exact:true}).count(),0);
    await click('Hide search');assert.equal(await page.getByRole('textbox').count(),0,'collapse hides editor');
    await page.getByRole('button',{name:'Play Morning Circuit',exact:true}).waitFor();assert.equal(await page.getByRole('button',{name:'Play Evening Signal',exact:true}).count(),0);
    await click('Show search');await page.locator('input:focus,textarea:focus').waitFor();
    assert.equal(await page.locator('input:focus,textarea:focus').inputValue(),'Morning','query survives collapse');
    await page.keyboard.press('Tab');await require('./search-input.cjs').clear(page, click);await click('Hide search');
    await page.getByRole('button',{name:'Play Evening Signal',exact:true}).waitFor();
    await click('+ Queue');await click('Play Morning Circuit');
    // Enqueue the second track without replacing the first.
    const plus=await page.getByRole('button',{name:'+ Queue',exact:true}).nth(1).boundingBox();
    await page.mouse.click(plus.x+plus.width/2,plus.y+plus.height/2);
    await until(async()=>(await audio())[0]?.time>1,'audible first track');event('playing');await wait(2500);
    const initial=(await audio())[0],opened=opens;
    await click('Show search');await click('Hide search');assert.equal(opens,opened,'search toggle does not reopen media');
    await click('Now Playing');event('now-playing');await wait(2500);
    assert.equal(await page.getByRole('button',{name:'Search',exact:true}).count(),0);
    assert.equal(await page.getByRole('button',{name:'Open Now Playing',exact:true}).count(),0,'no duplicate mini-player');
    await page.screenshot({path:path.join(output,'now-playing.png')});
    await click('Queue');event('queue');await page.getByText('2 tracks',{exact:true}).first().waitFor();await wait(2500);
    await page.screenshot({path:path.join(output,'queue.png')});
    await page.setViewportSize({width:800,height:844});await wait(500);
    await page.getByRole('button',{name:'Back to Now Playing',exact:true}).waitFor();
    await click('Back to Now Playing');await page.getByRole('slider').waitFor();
    const slider=await page.getByRole('slider').boundingBox();assert.ok(slider.width<500,'wide player stays bounded');
    await page.setViewportSize({width:390,height:844});
    await until(async()=>{const b=await page.getByRole('button',{name:'Pause',exact:true}).first().boundingBox();return b&&b.x>=0&&b.x+b.width<=390&&b.y>100&&b.y+b.height<=844},'compact player relayout');
    event('returned-player');assert.equal(opens,opened,'navigation/resizing does not reopen media');
    assert.equal((await audio())[0].src,initial.src);assert.ok((await audio())[0].time>initial.time);
    await click('Pause');event('paused');await until(async()=>(await audio())[0].paused,'pause');
    const paused=(await audio())[0];await wait(3000);
    await click('Library');await click('Open Now Playing');
    assert.equal((await audio())[0].src,paused.src);assert.ok(Math.abs((await audio())[0].time-paused.time)<.2);
    assert.equal((await audio())[0].paused,true);assert.equal(opens,opened);
    await click('Play');event('resumed');await until(async()=>(await audio())[0].time>paused.time+1,'resume');await wait(3000);
    await click('Player information');await click('Show artwork');
    await click('Library');event('library-return');await wait(2000);await page.screenshot({path:path.join(output,'library.png')});
    await click('Now Playing');await click('Queue');await click('Clear queue');event('cleared');
    await page.getByText('0 tracks',{exact:true}).first().waitFor();assert.equal((await audio()).length,0);
    await wait(2500);
    if(stop){await stop();stop=null;}
    // Exercise long catalogue metadata after the clean demonstration is saved.
    const catalogue=await(await fetch(origin+'/api/tracks')).json();
    const longTitle='Morning Circuit — a long synthetic title for checking compact list and information accessibility';
    const longArtist='Normal Bias synthetic trial with deliberately extended artist metadata';
    assert.equal((await fetch(origin+'/api/tracks/'+catalogue.items[0].id,{method:'PATCH',headers:{'Content-Type':'application/json'},body:JSON.stringify({title:longTitle,artist:longArtist})})).status,200);
    await click('Library');await require('./search-input.cjs').clear(page, click);
    await page.getByRole('button',{name:'Play '+longTitle,exact:true}).waitFor();
    const addBounds=await page.getByRole('button',{name:'+ Queue',exact:true}).first().boundingBox();
    assert.ok(addBounds.x>=0&&addBounds.x+addBounds.width<=390,'long row retains reachable queue action');
    await page.keyboard.press('Tab');await page.keyboard.press('Tab');
    await click('Play '+longTitle);await until(async()=>(await audio())[0]?.time>0,'long metadata playback');
    await click('Now Playing');await click('Player information');
    assert.ok((await page.locator('body').innerText()).includes(longTitle),'full title available in information');
    await click('Show artwork');await click('Queue');
    await page.getByRole('button',{name:'Play queued '+longTitle,exact:true}).waitFor();
    await page.screenshot({path:path.join(output,'long-metadata-queue.png')});
    await click('Clear queue');
    assert.deepEqual(errors,[]);
    fs.writeFileSync(path.join(output,process.env.RECORD_DEMO?'result.json':'checks-result.json'),JSON.stringify({checks:['collapsed search shares category row and preserves applied query when hidden','search toggle preserves live audio','two top-level destinations, no Queue exposure on Library','existing player controls, inline information and dedicated Queue entry','nested Queue and Back','wide/compact resize preserves child destination','bounded player on desktop','same stream and playback progress across navigation','paused position/intent retained','mini-player opens player','clear releases audio and retains empty Queue','long metadata keeps row actions reachable and full accessible title/information','keyboard Tab traversal does not break navigation'],navigationOpens:opened,opens,events,errors},null,2));
    console.log('Compact navigation PASS; recording:',process.env.RECORD_DEMO?output:'not requested');
})().catch(async e=>{console.error(e);process.exitCode=1;if(page){await page.screenshot({path:path.join(output,'failure.png')});console.error((await page.locator('body').innerText()).slice(-1500))}})
.finally(async()=>{if(stop)await stop().catch(()=>{});if(browser)await browser.close();if(server){server.kill('SIGTERM');await new Promise(r=>server.once('exit',r))}if(!process.exitCode)fs.rmSync(work,{recursive:true,force:true})});
