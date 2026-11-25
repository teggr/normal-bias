/* Actual generated-client three-pane responsive acceptance; synthetic disposable media only. */
const assert = require('node:assert/strict');
const {spawn, spawnSync} = require('node:child_process');
const fs = require('node:fs'), os = require('node:os'), path = require('node:path'), net = require('node:net');
const {chromium} = require('playwright-core');
const root = path.resolve(__dirname,'../..'), out = path.join(root,'client/target/recordings/wide-listening');
const work = fs.mkdtempSync(path.join(os.tmpdir(),'nb-wide-listening-'));
const album = 'A Long Album Title That Wraps Across Several Lines';
let server, browser, context;
const delay = ms => new Promise(r=>setTimeout(r,ms));
async function until(check, label) { for(let i=0;i<200;i++){if(await check())return;await delay(100)} throw Error('Timeout: '+label); }
async function click(page,name) {
 if(name==='Library views'&&await page.getByRole('button',{name:'Close album',exact:true}).count())name='Close album';
 const control=page.getByRole('button',{name,exact:true}).first();await control.waitFor();
 const b=await control.boundingBox();assert.ok(b,name);await page.mouse.click(b.x+b.width/2,b.y+b.height/2);await delay(550);
}
async function text(page,value) { await until(async()=>await page.getByText(value,{exact:true}).count()>0,value); }
function command(program,args) {const r=spawnSync(program,args,{encoding:'utf8'});assert.equal(r.status,0,r.stderr);}
(async()=>{
 fs.mkdirSync(out,{recursive:true});
 const port=await new Promise(r=>{const s=net.createServer();s.listen(0,'127.0.0.1',()=>{const p=s.address().port;s.close(()=>r(p))})});
 const origin='http://127.0.0.1:'+port;
 const log=fs.openSync(path.join(out,'api.log'),'w');
 server=spawn('java',['-jar',path.join(root,'api/target/normal-bias-api-0.1.0-SNAPSHOT.jar'),'--server.address=127.0.0.1','--server.port='+port,'--normalbias.auth.mode=demo','--normalbias.artwork.background-enabled=false','--normalbias.data-dir='+path.join(work,'data'),'--spring.web.resources.static-locations=file:'+path.join(root,'client/target/web/')],{stdio:['ignore',log,log]});
 await until(async()=>{try{return(await fetch(origin+'/api/tracks')).ok}catch{return false}},'service');
 command('ffmpeg',['-v','error','-f','lavfi','-i','aevalsrc=0.08*sin(2*PI*220*t)+0.04*sin(2*PI*330*t)+0.03*sin(2*PI*440*t):s=44100:d=90','-c:a','libmp3lame',path.join(work,'base.mp3')]);
 command('ffmpeg',['-v','error','-f','lavfi','-i','color=c=0x287fa8:s=240x240','-frames:v','1',path.join(work,'cover.png')]);
 for(const [i,title,artist,name,cover] of [[0,'First light','Example Artist',album,true],[1,'Evening walk','Example Artist',album,true],[2,'Quiet room','Another Artist','No Cover Session',false],[3,'Sunset','Another Artist','No Cover Session',false],...Array.from({length:28},(_,i)=>[4+i,'Z fixture '+String(i+1).padStart(2,'0'),'Scrolling fixture','Scrolling session',false])]) {
  const file=path.join(work,i+'.mp3');
  command('ffmpeg',['-v','error','-i',path.join(work,'base.mp3'),...(cover?['-i',path.join(work,'cover.png'),'-map','0:a','-map','1:v','-disposition:v','attached_pic']:[]),'-c','copy','-metadata','title='+title,'-metadata','artist='+artist,'-metadata','album='+name,file]);
  const res=await fetch(origin+'/api/tracks?filename='+i+'.mp3',{method:'POST',headers:{'Content-Type':'audio/mpeg'},body:fs.readFileSync(file)});assert.equal(res.status,201);
 }
 browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
 context=await browser.newContext({viewport:{width:1440,height:960},serviceWorkers:'block'});
 let page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(String(e)));
 await page.goto(origin);await page.getByRole('button',{name:'Albums',exact:true}).waitFor();
 const events=[];const mark=name=>events.push({name,at:Date.now()/1000});
 const audio=()=>page.locator('audio').evaluateAll(es=>es.map(e=>({src:e.currentSrc,time:e.currentTime,paused:e.paused})));
 const plus=async index=>{const b=await page.getByRole('button',{name:'+ Queue',exact:true}).nth(index).boundingBox();await page.mouse.click(b.x+b.width/2,b.y+b.height/2);await delay(550)};
 const queued=()=>page.getByRole('button',{name:/^Play queued /}).evaluateAll(es=>es.map(e=>e.getAttribute('aria-label')));
 async function panes(){
  await page.getByRole('button',{name:'Clear queue',exact:true}).waitFor();
  const browse=await page.getByRole('button',{name:'All songs',exact:true}).boundingBox(),seek=await page.getByRole('slider').boundingBox(),clear=await page.getByRole('button',{name:'Clear queue',exact:true}).boundingBox();
  assert.ok(browse.x+browse.width<seek.x&&seek.x+seek.width<clear.x,'three ordered nonoverlapping panes');
  assert.ok(clear.x+clear.width<=page.viewportSize().width,'Queue remains inside the viewport after shrinking');
  assert.equal(await page.getByRole('button',{name:'Open Now Playing',exact:true}).count(),0,'no duplicate mini-player');
  assert.equal(await page.getByRole('button',{name:'Previous',exact:true}).count(),1,'single transport');
 }
 await panes();await delay(700);await page.screenshot({path:path.join(out,'empty-wide.png')});
 const stopCapture=await require('./capture-demo.cjs').start(page,out);
 await plus(0);await plus(2);await click(page,'Play First light');
 await until(async()=>(await audio())[0]?.time>.5&&!(await audio())[0]?.paused,'wide playback');mark('playing');
 assert.deepEqual(await queued(),['Play queued Evening walk','Play queued First light','Play queued Quiet room']);
 const original=(await audio())[0];await delay(1200);
 await click(page,'Show search');await page.locator('input:focus,textarea:focus').waitFor();await page.locator('input:focus,textarea:focus').fill('First');await page.keyboard.press('Tab');await click(page,'Search');
 assert.equal(await page.getByRole('button',{name:/^Play (First light|Evening walk|Quiet room|Sunset)$/}).count(),1);
 await click(page,'Hide search');assert.equal((await audio())[0].src,original.src);assert.ok((await audio())[0].time>original.time);
 await page.screenshot({path:path.join(out,'wide-playing.png')});mark('filtered');
 await click(page,'Player information');await page.getByRole('button',{name:'Show artwork',exact:true}).waitFor();await click(page,'Show artwork');
 await click(page,'Queue');await panes();assert.deepEqual(await queued(),['Play queued Evening walk','Play queued First light','Play queued Quiet room']);
 // Narrowing restores the requested Queue destination, preserving media and Library query.
 await page.setViewportSize({width:390,height:844});await delay(800);
 await page.getByRole('button',{name:'Back to Now Playing',exact:true}).waitFor();assert.equal(await page.getByRole('button',{name:'All songs',exact:true}).count(),0);
 assert.equal((await audio())[0].src,original.src);await click(page,'Back to Now Playing');await click(page,'Library');
 assert.equal(await page.getByRole('slider').count(),0);assert.equal(await page.getByRole('button',{name:'Clear queue',exact:true}).count(),0);
 assert.equal(await page.getByRole('button',{name:'Open Now Playing',exact:true}).count(),1);
 await page.getByRole('button',{name:'Play First light',exact:true}).waitFor();await page.screenshot({path:path.join(out,'mobile-unchanged.png')});mark('mobile');
 await page.setViewportSize({width:1440,height:960});await delay(800);await panes();assert.equal((await audio())[0].src,original.src);
 await require('./search-input.cjs').clear(page,name=>click(page,name));await click(page,'Hide search');
 await click(page,'Albums');await click(page,'Open '+album);await text(page,album);
 const heading=await page.getByText(album,{exact:true}).first().boundingBox(),rail=await page.getByRole('slider').boundingBox();assert.ok(heading.x+heading.width<=rail.x,'album header stays inside Library pane');
 await page.screenshot({path:path.join(out,'wide-album.png')});await click(page,'Close album');await click(page,'All songs');
 await click(page,'Play queued Quiet room');await until(async()=>(await audio())[0]?.src!==original.src&&!(await audio())[0]?.paused,'queue selection');mark('queue-selected');
 const current=(await audio())[0];
 const shuffle=await page.getByRole('button',{name:'Shuffle queue',exact:true}).last().boundingBox();await page.mouse.click(shuffle.x+shuffle.width/2,shuffle.y+shuffle.height/2);await delay(600);
 assert.equal((await audio())[0].src,current.src);assert.deepEqual((await queued()).sort(),['Play queued Evening walk','Play queued First light','Play queued Quiet room'].sort());
 await click(page,'Pause');await delay(1300);mark('paused');
 const paused=(await audio())[0];
 for(const [width,height] of [[1180,780],[1024,768],[1440,600],[2024,1200]]){
  await page.setViewportSize({width,height});await delay(800);
  if(await page.getByRole('button',{name:'Clear queue',exact:true}).count())await panes();
  else assert.equal(await page.getByRole('button',{name:'Open Now Playing',exact:true}).count(),1,'narrow tablet fallback');
  assert.equal((await audio())[0].src,paused.src);assert.ok((await audio())[0].paused&&Math.abs((await audio())[0].time-paused.time)<.3);
 }
 await page.setViewportSize({width:1440,height:960});await delay(700);await panes();await page.screenshot({path:path.join(out,'wide-paused.png')});
 await click(page,'Clear queue');await text(page,'0 tracks');assert.equal(await page.locator('audio').count(),0);mark('cleared');await delay(1300);
 await stopCapture();fs.writeFileSync(path.join(out,'events.json'),JSON.stringify(events,null,2));
 // Exercise both independent scroll containers with enough real rows to overflow.
 await click(page,'Albums');await click(page,'Open Scrolling session');await click(page,'Queue album');await text(page,'Album queued: 28 tracks added.');
 await click(page,'Close album');await click(page,'All songs');await delay(1600);
 function difference(a,b){assert.equal(a.length,b.length);let sum=0;for(let i=0;i<a.length;i++)sum+=Math.abs(a[i]-b[i]);return sum/a.length}
 function pixels(file,crop){const r=spawnSync('ffmpeg',['-v','error','-i',file,'-vf','crop='+crop,'-frames:v','1','-f','rawvideo','-pix_fmt','rgb24','pipe:1'],{maxBuffer:3*1024*1024});assert.equal(r.status,0);return r.stdout}
 async function settled(file,crop){let previous,stable=0;await until(async()=>{await page.screenshot({path:file});const current=pixels(file,crop);if(previous&&difference(previous,current)<.01){if(!stable)stable=Date.now()}else stable=0;previous=current;await delay(200);return stable&&Date.now()-stable>600},'pane kinetic scrolling settled')}
 const initial=path.join(out,'scroll-before.png'),queueScroll=path.join(out,'scroll-queue.png'),libraryScroll=path.join(out,'scroll-library.png');
 await page.screenshot({path:initial});
 await page.mouse.move(1250,650);await page.mouse.down();await page.mouse.move(1250,250,{steps:18});await page.mouse.up();await settled(queueScroll,'220:650:1205:180');
 assert.ok(difference(pixels(initial,'650:650:10:180'),pixels(queueScroll,'650:650:10:180'))<.01,'queue scroll leaves Library unchanged');
 assert.ok(difference(pixels(initial,'220:650:1205:180'),pixels(queueScroll,'220:650:1205:180'))>.1,'queue actually scrolls');
 await page.mouse.move(200,650);await page.mouse.down();await page.mouse.move(200,250,{steps:18});await page.mouse.up();await settled(libraryScroll,'650:650:10:180');
 assert.ok(difference(pixels(queueScroll,'650:650:10:180'),pixels(libraryScroll,'650:650:10:180'))>.1,'Library actually scrolls');
 assert.ok(difference(pixels(queueScroll,'220:650:1205:180'),pixels(libraryScroll,'220:650:1205:180'))<.01,'Library scroll leaves Queue unchanged');
 assert.deepEqual(errors,[]);await context.close();context=null;
 console.log('PASS three panes, one transport, live queue actions, search/group header, information, media/query continuity through resizing, unchanged mobile destinations, paused resize and Clear');
})().catch(e=>{console.error(e);process.exitCode=1}).finally(async()=>{if(context)await context.close();if(browser)await browser.close();if(server)server.kill('SIGTERM');if(!process.exitCode)fs.rmSync(work,{recursive:true,force:true});});
