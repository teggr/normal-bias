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
let server,browser,page;
async function until(check,label,timeout=20000) {
    const end=Date.now()+timeout;
    while(Date.now()<end) { if(await check())return;await delay(100); }
    throw new Error('Timed out: '+label);
}
async function click(name,index=0,role='button') {
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
        '--normalbias.data-dir='+path.join(work,'data'),'--spring.web.resources.static-locations=file:'+path.join(repo,'client/target/web/')],{stdio:['ignore',log,log]});
    await until(async()=>{try{return(await fetch(origin+'/api/tracks')).ok;}catch{return false;}},'fixture API startup');
    const ids=await seed(origin);
    const originals=await Promise.all(ids.map(async id=>Buffer.from(await(await fetch(origin+'/api/tracks/'+id+'/audio')).arrayBuffer())));
    const embedded=Buffer.from(await(await fetch(origin+'/api/tracks/'+ids[2]+'/artwork')).arrayBuffer());
    browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
    const context=await browser.newContext({viewport:{width:1180,height:844},serviceWorkers:'block'});page=await context.newPage();
    const errors=[];page.on('pageerror',e=>errors.push(String(e)));
    const requests=[];page.on('request',r=>requests.push(r.url()));
    await page.goto(origin);await text('1–3 of 3');
    const stop=process.env.RECORD_DEMO?await require('./capture-demo.cjs').start(page,path.resolve(process.env.RECORD_DEMO)):null;
    await click('+ Queue',1);await click('Play Morning Circuit');
    await until(async()=>{const a=await audio();return a.some(x=>!x.paused&&x.time>1);},'actual audio playback');
    await delay(3000);
    const playing=(await audio()).find(x=>!x.paused);const opened=requests.filter(x=>x.includes('/audio')).length;
    await click('Find album artwork',0);await text('Suggested album: Artwork Trial');
    await page.screenshot({path:path.join(repo,'client/target/album-suggestions.png')});
    await delay(4000);await click('Find candidate releases');
    await until(()=>page.getByRole('button',{name:'Preview cover',exact:true}).count().then(n=>n===2),'ambiguous editions');
    await click('Preview cover',0);
    await until(()=>page.getByRole('button',{name:'Accept cover for 3 tracks',exact:true}).nth(0).isEnabled(),'preview decoded');
    await page.screenshot({path:path.join(repo,'client/target/album-preview.png')});
    await delay(6500);await click('Accept cover for 3 tracks',0);
    await text('Cover accepted. Embedded images preserved. Playback and queue continue.');
    const after=(await audio()).find(x=>!x.paused);
    assert.equal(after.src,playing.src);assert.ok(after.time>playing.time+3);assert.equal(requests.filter(x=>x.includes('/audio')).length,opened,'no audio reopened');
    await click('Return to library');await text('1–3 of 3');await text('Queue (2)');
    await until(()=>requests.some(x=>x.includes('/artwork?v=')&&!x.endsWith('v=embedded')),'revisioned artwork request');
    await page.screenshot({path:path.join(repo,'client/target/album-accepted.png')});
    await delay(5500);
    await page.setViewportSize({width:390,height:844});await delay(1500);
    await page.screenshot({path:path.join(repo,'client/target/album-mini.png')});
    await click('Now Playing',0,'radio');await delay(1300);
    await page.screenshot({path:path.join(repo,'client/target/album-player.png')});
    await click('Pause');const paused=(await audio()).find(x=>x.src===playing.src);await delay(1500);
    assert.ok(Math.abs((await audio()).find(x=>x.src===playing.src).time-paused.time)<0.2);
    if(stop)await stop();
    for(let i=0;i<ids.length;i++)assert.deepEqual(Buffer.from(await(await fetch(origin+'/api/tracks/'+ids[i]+'/audio')).arrayBuffer()),originals[i]);
    assert.deepEqual(Buffer.from(await(await fetch(origin+'/api/tracks/'+ids[2]+'/artwork')).arrayBuffer()),embedded,'embedded cover preserved');
    assert.equal((await fetch(origin+'/api/tracks/'+ids[0]+'/artwork')).status,200);
    console.log('PASS ambiguous releases, explicit preview/accept, real playback continuity, queue and missing-cache refresh; embedded/audio bytes preserved');
    await click('Back to library');
    for (const [album,message] of [['No match','No matching releases. Retry later or correct the tags.'],
            ['Offline','Provider unavailable or throttled. Your artwork is unchanged; retry later.']]) {
        await api(origin,'/api/tracks/'+ids[0],{album},'PATCH');await click('Reset');await text('1–3 of 3');
        await click('Find album artwork',0);await text('Suggested album: '+album);await click('Find candidate releases');
        await text(message);await click('Back to library');
    }
    await api(origin,'/api/tracks/'+ids[0],{album:'Artwork Trial'},'PATCH');await click('Reset');await text('1–3 of 3');
    await click('Find album artwork',0);await click('Find candidate releases');await click('Preview cover',0);
    await until(()=>page.getByRole('button',{name:'Accept cover for 3 tracks',exact:true}).nth(0).isEnabled(),'fresh preview');
    await api(origin,'/api/tracks/'+ids[0],{title:'Edited while choosing'},'PATCH');
    await click('Accept cover for 3 tracks',0);await text('Tracks or artwork changed, or lookup expired. Start a fresh match.');
    await click('Back to library');
    await api(origin,'/api/tracks/'+ids[0],{title:'Morning Circuit'},'PATCH');await click('Reset');await text('1–3 of 3');
    await page.setViewportSize({width:1180,height:844});await delay(600);
    await click('Queue (2)',0,'radio');await click('Clear queue');await click('Library',0,'radio');
    await click('Play Morning Circuit');await click('+ Queue',1);
    await until(async()=>(await audio()).some(x=>!x.paused),'play for matching-screen queue completion');
    const slider=await page.getByRole('slider').boundingBox();
    await page.mouse.click(slider.x+slider.width*0.97,slider.y+slider.height/2);
    await until(async()=>(await audio()).some(x=>x.time>95),'seek near completion',2500);
    await click('Find album artwork',0);await click('Find candidate releases');
    await until(async()=>(await audio()).some(x=>x.src.includes(ids[1])&&!x.paused),'completion while matching is visible',10000);
    await click('Back to library');await click('Queue (2)',0,'radio');await click('Clear queue');
    console.log('PASS visible no-match/outage/stale acceptance and queue completion while matching');
    // A real process restart must preserve the accepted cover and provenance via HTTP.
    const saved=await api(origin,'/api/album-artwork/tracks/'+ids[0]);
    const savedCover=Buffer.from(await(await fetch(origin+'/api/tracks/'+ids[0]+'/artwork')).arrayBuffer());
    const launchArgs=server.spawnargs.slice(1);
    await new Promise(resolve=>{server.once('exit',resolve);server.kill('SIGTERM');});
    server=spawn('java',launchArgs,{stdio:['ignore',log,log]});
    await until(async()=>{try{return(await fetch(origin+'/api/tracks')).ok;}catch{return false;}},'API restart');
    assert.deepEqual(await api(origin,'/api/album-artwork/tracks/'+ids[0]),saved);
    assert.deepEqual(Buffer.from(await(await fetch(origin+'/api/tracks/'+ids[0]+'/artwork')).arrayBuffer()),savedCover);
    console.log('PASS real API process restart preserves accepted image/provenance');
    assert.deepEqual(errors,[]);console.log('Album browser PASS; service workers blocked; no physical-device claim');
})().catch(async error=>{console.error(error);if(page){try{await page.screenshot({path:path.join(repo,'client/target/album-failure.png')});}catch{}}process.exitCode=1;}).finally(async()=>{if(browser)await browser.close();if(server)server.kill('SIGTERM');console.log('Disposable evidence/logs:',work);});
