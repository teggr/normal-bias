/* Real generated Java/CN1 UI + disposable API; provider responses exist only on test classpath. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const net = require('node:net');
const {spawn,spawnSync} = require('node:child_process');
const {chromium} = require('playwright-core');
const repo=path.resolve(__dirname,'../..');
const work=fs.mkdtempSync(path.join(os.tmpdir(),'normalbias-match-browser-'));
const delay=ms=>new Promise(r=>setTimeout(r,ms));
let server,browser,page,stopRecording;
async function until(check,label,timeout=20000) {
    const end=Date.now()+timeout;
    while(Date.now()<end) { if(await check())return;await delay(100); }
    throw new Error('Timed out: '+label);
}
async function click(name,index=0,role='button') {
    if (name === 'Library' && await page.getByRole('button',{name:'Back to Now Playing',exact:true}).count()) await click('Back to Now Playing');
    const item=page.getByRole(role,{name,exact:true}).nth(index);await item.waitFor();
    let box;
    for(let i=0;i<20;i++) {
        box=await item.boundingBox();
        if(box && box.y>25 && box.y+box.height<844)break;
        await page.mouse.move(600,650);await page.mouse.wheel(0,box?.y<25?-300:300);await delay(180);
    }
    assert.ok(box && box.y>0 && box.y<844,'visible control '+name);
    await page.mouse.click(box.x+box.width/2,box.y+box.height/2);await delay(400);
}
async function text(value) { await until(()=>page.getByText(value,{exact:true}).count().then(n=>n>0),value); }
async function audio() { return page.locator('audio').evaluateAll(xs=>xs.map(x=>({src:x.currentSrc,time:x.currentTime,paused:x.paused}))); }
async function api(origin,url,body,method=body?'POST':'GET') {
    const r=await fetch(origin+url,{method,headers:{'Content-Type':'application/json'},body:body?JSON.stringify(body):undefined});
    assert.ok(r.ok,'API '+url+' HTTP '+r.status);return r.json();
}
async function seed(origin) {
    const cover=path.join(work,'embedded.png');
    assert.equal(spawnSync('ffmpeg',['-v','error','-f','lavfi','-i','color=c=0xdd3366:s=320x320','-frames:v','1','-y',cover]).status,0);
    const ids=[];
    for(let i=0;i<3;i++) {
        const file=path.join(work,'track-'+i+'.mp3');
        const args=['-v','error','-f','lavfi','-i',
            `aevalsrc=0.10*sin(2*PI*${220+i*30}*t)+0.06*sin(2*PI*330*t)+0.04*sin(2*PI*440*t):s=44100:d=100`];
        if(i===2)args.push('-i',cover,'-map','0:a','-map','1:v','-c:v','copy','-disposition:v','attached_pic');
        args.push('-c:a','libmp3lame','-b:a','64k','-metadata','title='+['Morning Circuit','Evening Signal','Embedded Example'][i],
            '-metadata','artist=Normal Bias Demo','-metadata','album=Artwork Trial','-y',file);
        assert.equal(spawnSync('ffmpeg',args).status,0);
        const r=await fetch(origin+'/api/tracks',{method:'POST',headers:{'Content-Type':'audio/mpeg'},body:fs.readFileSync(file)});
        assert.equal(r.status,201);ids.push((await r.json()).id);
    }
    return ids;
}
(async()=>{
    const port=await new Promise(resolve=>{const s=net.createServer();s.listen(0,'127.0.0.1',()=>{const p=s.address().port;s.close(()=>resolve(p));});});
    const origin='http://127.0.0.1:'+port;
    const cp=[path.join(repo,'api/target/test-classes'),path.join(repo,'api/target/classes'),fs.readFileSync(path.join(repo,'api/target/test-classpath.txt'),'utf8').trim()].join(path.delimiter);
    const log=fs.openSync(path.join(work,'api.log'),'w');
    server=spawn('java',['-cp',cp,'dev.normalbias.api.ArtworkFixtureApplication','--server.port='+port,
        '--normalbias.data-dir='+path.join(work,'data'), '--normalbias.auth.mode=demo', '--normalbias.artwork.background-enabled=true',
        '--normalbias.artwork.initial-delay-ms=20000', '--normalbias.artwork.scan-delay-ms=1000','--spring.web.resources.static-locations=file:'+path.join(repo,'client/target/web/')],{stdio:['ignore',log,log]});
    await until(async()=>{try{return(await fetch(origin+'/api/tracks')).ok;}catch{return false;}},'fixture API startup');
    const ids=await seed(origin);
    const originals=await Promise.all(ids.map(async id=>Buffer.from(await(await fetch(origin+'/api/tracks/'+id+'/audio')).arrayBuffer())));
    const embedded=Buffer.from(await(await fetch(origin+'/api/tracks/'+ids[2]+'/artwork')).arrayBuffer());
    browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
    const context=await browser.newContext({viewport:{width:1000,height:844},serviceWorkers:'block'});page=await context.newPage();
    if(process.env.BLOCK_ARTWORK_REVISION) await page.route('**/api/album-artwork/revision',route=>route.fulfill({status:503,body:'revision check unavailable'}));
    const errors=[];page.on('pageerror',e=>errors.push(String(e)));
    const requests=[];page.on('request',r=>requests.push(r.url()));
    await page.goto(origin);await page.getByRole('button',{name:'Play Morning Circuit',exact:true}).waitFor();
    stopRecording=process.env.RECORD_DEMO?await require('./capture-demo.cjs').start(page,path.resolve(process.env.RECORD_DEMO)):null;
    await click('+ Queue',1);await click('Play Morning Circuit');await click('Now Playing');
    await until(async()=>{const a=await audio();return a.some(x=>!x.paused&&x.time>1);},'actual audio playback');
    await delay(3000);
    const playing=(await audio()).find(x=>!x.paused);const opened=requests.filter(x=>x.includes('/audio')).length;
    assert.equal(await page.getByRole('button',{name:'Find album artwork',exact:true}).count(),0,'no artwork management control');
    await until(async()=> (await fetch(origin+'/api/tracks/'+ids[0]+'/artwork')).status===200,
        'service fills artwork without a client request',40000);
    if(!process.env.BLOCK_ARTWORK_REVISION)
        await until(()=>requests.some(x=>x.includes('/artwork?v=1')),'passive cover revision refresh',20000);
    // Inspect the actual canvas pixels: a request alone does not prove the player repainted.
    async function blueRegion(x,y,width,height,minimum) {
        const screenshot=path.join(work,'cover-check.png');
        await page.screenshot({path:screenshot});
        const decoded=spawnSync('ffmpeg',['-v','error','-i',screenshot,
            '-vf',`crop=${width}:${height}:${x}:${y}`,'-frames:v','1','-f','rawvideo','-pix_fmt','rgb24','pipe:1'],{timeout:10000});
        assert.equal(decoded.status,0,'decode screenshot pixels');
        let blue=0;
        for(let i=0;i<decoded.stdout.length;i+=3) {
            if(decoded.stdout[i]<70 && decoded.stdout[i+1]<110 && decoded.stdout[i+2]>150)blue++;
        }
        return blue>minimum;
    }
    const seekBox=await page.getByRole('slider').boundingBox();
    const headingBox=await page.getByRole('button',{name:'Library',exact:true}).first().boundingBox();
    const centre=seekBox.x+seekBox.width/2;
    await until(()=>blueRegion(Math.round(centre-120),Math.round(headingBox.y+headingBox.height+40),240,240,3000),'visible blue fetched cover on player',process.env.BLOCK_ARTWORK_REVISION?40000:15000);
    const titleBox=await page.getByText('Morning Circuit',{exact:true}).first().boundingBox();
    await until(()=>blueRegion(Math.round(seekBox.x),Math.round(titleBox.y-10),48,54,150),'visible fetched cover on metadata thumbnail',10000);
    const after=(await audio()).find(x=>!x.paused);
    assert.ok(after,'playback remains active after background refresh: '+JSON.stringify(await audio()));
    assert.equal(after.src,playing.src);assert.ok(after.time>playing.time+3);
    assert.equal(requests.filter(x=>x.includes('/audio')).length,opened,'background refresh does not reopen audio');
    assert.equal(requests.filter(x=>x.includes('/matches')||x.includes('/suggestions')).length,0,'no client matching workflow');
    await click('Queue');await text('2 tracks');await click('Back to Now Playing');
    await page.screenshot({path:path.join(repo,'client/target/album-background-library.png')});
    await delay(3500);
    await click('Pause');const paused=(await audio()).find(x=>x.src===playing.src);await delay(2000);
    assert.ok(Math.abs((await audio()).find(x=>x.src===playing.src).time-paused.time)<0.2);
    if(stopRecording){await stopRecording();stopRecording=null;}
    for(let i=0;i<ids.length;i++)assert.deepEqual(Buffer.from(await(await fetch(origin+'/api/tracks/'+ids[i]+'/audio')).arrayBuffer()),originals[i]);
    assert.deepEqual(Buffer.from(await(await fetch(origin+'/api/tracks/'+ids[2]+'/artwork')).arrayBuffer()),embedded,'embedded cover preserved');
    console.log(process.env.BLOCK_ARTWORK_REVISION?'PASS revision endpoint blocked: missing-cover TTL recovered without restart':'PASS revision notification refresh');
    console.log('PASS autonomous background fill, no matching controls/requests, real playback continuity, queue and passive cache refresh; embedded/audio bytes preserved');
    // A real process restart must preserve the accepted cover and provenance via HTTP.
    const saved=await api(origin,'/api/album-artwork/tracks/'+ids[0]);
    const savedCover=Buffer.from(await(await fetch(origin+'/api/tracks/'+ids[0]+'/artwork')).arrayBuffer());
    const launchArgs=server.spawnargs.slice(1);
    await new Promise(resolve=>{server.once('exit',resolve);server.kill('SIGTERM');});
    server=spawn('java',launchArgs,{stdio:['ignore',log,log]});
    await until(async()=>{try{return(await fetch(origin+'/api/tracks')).ok;}catch{return false;}},'API restart');
    assert.deepEqual(await api(origin,'/api/album-artwork/tracks/'+ids[0]),saved);
    assert.deepEqual(Buffer.from(await(await fetch(origin+'/api/tracks/'+ids[0]+'/artwork')).arrayBuffer()),savedCover);
    console.log('PASS real API process restart preserves automatically fetched image/provenance');
    assert.deepEqual(errors,[]);console.log('Album browser PASS; service workers blocked; no physical-device claim');
})().catch(async error=>{console.error(error);if(page){try{await page.screenshot({path:path.join(repo,'client/target/album-failure.png')});}catch{}}process.exitCode=1;}).finally(async()=>{if(stopRecording){try{await stopRecording();}catch{}}if(browser)await browser.close();if(server)server.kill('SIGTERM');console.log('Disposable evidence/logs:',work);});
