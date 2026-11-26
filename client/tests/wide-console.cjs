/* Generated-client console/grid acceptance. Uses only a disposable synthetic catalogue. */
const assert = require('node:assert/strict');
const {spawn,spawnSync}=require('node:child_process');
const fs=require('node:fs'),os=require('node:os'),path=require('node:path'),net=require('node:net');
const {chromium}=require('playwright-core');
const root=path.resolve(__dirname,'../..'),out=path.join(root,'client/target/recordings/wide-console');
const work=fs.mkdtempSync(path.join(os.tmpdir(),'nb-console-'));
let server,browser,page,stopCapture; const errors=[],checks=[],events=[];const mark=name=>events.push({name,time:Date.now()/1000});
const wait=ms=>new Promise(r=>setTimeout(r,ms));
async function until(fn,label){for(let i=0;i<150;i++){if(await fn())return;await wait(100)}throw Error('Timeout: '+label)}
function command(p,args){const r=spawnSync(p,args,{encoding:'utf8'});assert.equal(r.status,0,r.stderr)}
async function click(name){const c=page.getByRole('button',{name,exact:true}).first();await c.waitFor();let b=await c.boundingBox();assert.ok(b,name);if(b.y<0||b.y+b.height>page.viewportSize().height){await page.mouse.move(150,350);await page.mouse.wheel(0,b.y<0?-500:500);await wait(700);b=await c.boundingBox();}await page.mouse.click(b.x+b.width/2,b.y+b.height/2);await wait(500)}
const box=name=>page.getByRole('button',{name,exact:true}).first().boundingBox();
const audio=()=>page.locator('audio').evaluateAll(es=>es.map(e=>({src:e.currentSrc,time:e.currentTime,paused:e.paused})));
async function consoleBounds(){
 await until(async()=>{
  const bounds=await Promise.all(['Previous','Next','Queue','Player information'].map(box));
  const seek=await page.getByRole('slider').boundingBox();
  return bounds.every(b=>b&&b.x>=0&&b.x+b.width<=page.viewportSize().width+1)&&seek&&seek.x+seek.width<bounds[0].x&&await page.getByRole('button',{name:'Open Now Playing',exact:true}).count()===0;
 },'console fits the current viewport after resize');
 const p=await box('Previous'),n=await box('Next'),q=await box('Queue'),info=await box('Player information');
 const seek=await page.getByRole('slider').boundingBox();
 assert.ok(p&&n&&q&&info&&seek,'all console controls present');
 assert.ok(seek.x+seek.width<p.x,'timeline beside intact wheel');
 assert.ok(q.y<p.y&&info.y<p.y,'upper outer controls retained');
 assert.equal(await page.getByRole('button',{name:'Previous',exact:true}).count(),1,'one transport');
 assert.equal(await page.getByRole('button',{name:'Open Now Playing',exact:true}).count(),0,'no mini-player');
 for(const b of [p,n,q,info,seek])assert.ok(b.x>=0&&b.x+b.width<=page.viewportSize().width+1,'control inside viewport');
}
async function gridColumns(expected){
 await until(async()=>await page.getByRole('button',{name:/^Open .* Album/}).count()>=expected,'group tile accessibility after reattachment');
 await until(async()=>{const bs=await page.getByRole('button',{name:/^Open .* Album/}).evaluateAll(es=>es.map(e=>e.getBoundingClientRect().y));return bs.length&&bs.filter(y=>Math.abs(y-bs[0])<2).length===expected;},'tile grid finishes reflow');
 const bounds=await page.getByRole('button',{name:/^Open .* Album/}).evaluateAll(es=>es.map(e=>{const r=e.getBoundingClientRect();return{x:r.x,y:r.y,w:r.width,h:r.height}}));
 assert.ok(bounds.length>=expected,'loaded group tiles');
 const first=bounds[0];const row=bounds.filter(b=>Math.abs(b.y-first.y)<2);
 assert.equal(row.length,expected,'adaptive tile column count');
 assert.ok(first.h>first.w,'art plus wrapping labels, rather than compact rows');
 assert.ok(first.y>(await box('Restart track')).y,'Library below console');
 for(const b of row)assert.ok(b.x+b.w<page.viewportSize().width,'tile leaves alphabet gutter');
}
(async()=>{
 fs.mkdirSync(out,{recursive:true});fs.writeFileSync(path.join(out,'client-console.log'),'');
 const port=await new Promise(r=>{const s=net.createServer();s.listen(0,'127.0.0.1',()=>{const p=s.address().port;s.close(()=>r(p))})});
 const origin='http://127.0.0.1:'+port;
 const log=fs.openSync(path.join(out,'api.log'),'w');
 server=spawn('java',['-jar',path.join(root,'api/target/normal-bias-api-0.1.0-SNAPSHOT.jar'),'--server.address=127.0.0.1','--server.port='+port,'--normalbias.auth.mode=demo','--normalbias.artwork.background-enabled=false','--normalbias.data-dir='+path.join(work,'data'),'--spring.web.resources.static-locations=file:'+path.join(root,'client/target/web/')],{stdio:['ignore',log,log]});
 await until(async()=>{try{return(await fetch(origin+'/api/tracks')).ok}catch{return false}},'API');
 command('ffmpeg',['-v','error','-f','lavfi','-i','aevalsrc=0.07*sin(2*PI*220*t)+0.04*sin(2*PI*330*t)+0.025*sin(2*PI*(440+55*sin(t))*t):s=44100:d=90','-c:a','libmp3lame',path.join(work,'base.mp3')]);
 for(let i=0;i<38;i++){
  const id=String(i).padStart(2,'0'),file=path.join(work,id+'.mp3');
  command('ffmpeg',['-v','error','-i',path.join(work,'base.mp3'),'-c','copy','-metadata','title='+id+' Song','-metadata','artist='+id+' Artist','-metadata','album='+id+' Album'+(i===3?' With A Deliberately Long Wrapping Title':''),file]);
  const r=await fetch(origin+'/api/tracks?filename='+id+'.mp3',{method:'POST',headers:{'Content-Type':'audio/mpeg'},body:fs.readFileSync(file)});assert.equal(r.status,201);
 }
 browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
 const context=await browser.newContext({viewport:{width:1440,height:960},serviceWorkers:'block'});
 page=await context.newPage();page.on('pageerror',e=>errors.push(String(e)));page.on('console',m=>{fs.appendFileSync(path.join(out,'client-console.log'),m.type()+': '+m.text()+'\n');if(m.text().includes('Exception:')){errors.push(m.text());console.log('CLIENT',m.text())}});
 await page.goto(origin);await page.getByRole('button',{name:'Albums',exact:true}).waitFor();
 await consoleBounds();assert.equal(await page.getByRole('button',{name:'Clear queue',exact:true}).count(),0,'queue initially closed');
 await click('Albums');await page.getByRole('button',{name:'Open 00 Album',exact:true}).waitFor();await wait(600);await gridColumns(5);
 await page.screenshot({path:path.join(out,'landscape-tiles.png')});checks.push('landscape five-column album grid and intact console');
 stopCapture=await require('./capture-demo.cjs').start(page,out);await wait(1200);
 await click('Open 00 Album');await page.getByRole('button',{name:'Queue album',exact:true}).waitFor();
 await click('Queue album');mark('playing');await click('Play');await until(async()=>(await audio())[0]?.time>.4,'actual audio');
 const live=(await audio())[0];
 await click('Back to Albums');await page.getByRole('button',{name:'Open 00 Album',exact:true}).waitFor();await wait(1000);await click('Queue');await page.getByRole('button',{name:'Close queue',exact:true}).waitFor();
 assert.equal(await page.getByRole('button',{name:'Albums',exact:true}).count(),0,'Queue replaces Library');
 assert.equal(await page.getByRole('button',{name:'Clear queue',exact:true}).count(),1);
 await consoleBounds();await wait(1200);await click('Close queue');await gridColumns(5);
 assert.equal((await audio())[0].src,live.src,'Queue preserves media resource');
 assert.ok((await audio())[0].time>live.time,'playback continues');checks.push('queue open/close restores Library and actual stream');
 await page.setViewportSize({width:1024,height:1366});await wait(1000);await consoleBounds();await gridColumns(4);
 await page.screenshot({path:path.join(out,'portrait-tiles.png')});await wait(1200);
 await click('Artists');await page.getByRole('button',{name:'Open 00 Artist',exact:true}).waitFor();
 await click('Open 00 Artist');await page.getByRole('button',{name:'Open 00 Album',exact:true}).waitFor();
 const a=await box('Open 00 Album');assert.ok(a.height>a.width,'artist albums retain tile presentation');
 await click('Back to Artists');await page.getByRole('button',{name:'Open 00 Artist',exact:true}).waitFor();await click('Albums');await gridColumns(4);checks.push('portrait four-column grid, Artists and artist albums');
 await click('Player information');await wait(900);await click('Show artwork');
 await click('Pause');mark('paused');const paused=(await audio())[0];await click('Queue');await page.getByRole('button',{name:'Close queue',exact:true}).waitFor();await click('Queue');await gridColumns(4);
 assert.ok((await audio())[0].paused);assert.ok(Math.abs((await audio())[0].time-paused.time)<.3);checks.push('information and paused queue toggle retain media');
 await page.setViewportSize({width:390,height:844});await until(async()=>await page.getByRole('button',{name:'Now Playing',exact:true}).count()===1&&await page.getByRole('button',{name:'Queue',exact:true}).count()===0,'compact Library restores its navigation');
 assert.equal(await page.getByRole('button',{name:'Queue',exact:true}).count(),0,'compact Library unchanged');
 assert.equal(await page.getByRole('button',{name:'Open Now Playing',exact:true}).count(),1);
 await click('Now Playing');await click('Queue');await click('Back to Now Playing');checks.push('mobile keeps destinations, mini-player and Queue Back');
 await page.setViewportSize({width:1440,height:960});await wait(900);await consoleBounds();await gridColumns(5);
 await click('Queue');await page.getByRole('button',{name:'Clear queue',exact:true}).waitFor();await click('Clear queue');assert.equal((await audio()).filter(e=>e.src).length,0,'Clear releases audio');
 assert.equal(await page.getByRole('button',{name:'Close queue',exact:true}).count(),1,'clear keeps Queue open');
 await page.screenshot({path:path.join(out,'queue-empty.png')});await wait(1200);await click('Close queue');await gridColumns(5);
 // Returning from Queue restarts bounded artwork work. End on the actual completed raster.
 await until(async()=>{
  const b=await box('Open 00 Album'),png=await page.screenshot({path:path.join(out,'final-library.png')});
  const pixels=spawnSync('ffmpeg',['-v','error','-i','pipe:0','-vf',`crop=${Math.floor(b.width)}:${Math.floor(b.width)}:${Math.floor(b.x)}:${Math.floor(b.y)}`,'-frames:v','1','-f','rawvideo','-pix_fmt','rgb24','pipe:1'],{input:png,maxBuffer:2*1024*1024});
  if(pixels.status!==0)return false;
  let coloured=0;for(let i=0;i<pixels.stdout.length;i+=3){const p=[pixels.stdout[i],pixels.stdout[i+1],pixels.stdout[i+2]];if(Math.max(...p)-Math.min(...p)>25)coloured++;}
  return coloured>200;
 },'restored Library artwork is painted');await wait(1200);
 await stopCapture();stopCapture=null;assert.deepEqual(errors,[]);
 fs.writeFileSync(path.join(out,'result.json'),JSON.stringify({checks,errors,events,serviceWorkers:'blocked',fixture:'38 original synthetic tracks, albums and artists'},null,2));
 console.log('PASS',checks.join('; '));
})().catch(async e=>{console.error(e);if(stopCapture){await stopCapture().catch(()=>{});stopCapture=null;}if(page)await page.screenshot({path:path.join(out,'failure.png')}).catch(()=>{});process.exitCode=1})
.finally(async()=>{if(browser)await browser.close();if(server)server.kill('SIGTERM');});
