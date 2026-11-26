/* Actual generated-client artist queue acceptance; synthetic disposable media only. */
const assert = require('node:assert/strict');
const {spawn, spawnSync} = require('node:child_process');
const fs = require('node:fs'), os = require('node:os'), path = require('node:path'), net = require('node:net');
const {chromium} = require('playwright-core');
const root = path.resolve(__dirname,'../..'), out = path.join(root,'client/target/recordings/artist-queue');
const work = fs.mkdtempSync(path.join(os.tmpdir(),'nb-artist-queue-'));
const album = 'A Long Album Title That Wraps Across Several Lines';
let server, browser, context;
const delay = ms => new Promise(r=>setTimeout(r,ms));
async function until(check, label) { for(let i=0;i<200;i++){if(await check())return;await delay(100)} throw Error('Timeout: '+label); }
async function click(page,name) {
 if (name === 'Library views' || name === 'Close album') name = await page.getByRole('button', {name: /^Back to /}).first().getAttribute('aria-label');
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
 for(const [i,title,artist,name,cover] of [[0,'First light','Example Artist',album,true],[1,'Evening walk','Example Artist','Second Album',true],[2,'Quiet room','Another Artist','No Cover Session',false]]) {
  const file=path.join(work,i+'.mp3');
  command('ffmpeg',['-v','error','-i',path.join(work,'base.mp3'),...(cover?['-i',path.join(work,'cover.png'),'-map','0:a','-map','1:v','-disposition:v','attached_pic']:[]),'-c','copy','-metadata','title='+title,'-metadata','artist='+artist,'-metadata','album='+name,file]);
  const res=await fetch(origin+'/api/tracks?filename='+i+'.mp3',{method:'POST',headers:{'Content-Type':'audio/mpeg'},body:fs.readFileSync(file)});assert.equal(res.status,201);
 }
 browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
 context=await browser.newContext({viewport:{width:390,height:844},serviceWorkers:'block'});
 let page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(String(e)));
 await page.goto(origin);await page.getByRole('button',{name:'Albums',exact:true}).waitFor();
 const stopCapture=await require('./capture-demo.cjs').start(page,out);
 await click(page,'Artists');await click(page,'Open Example Artist');await text(page,'2 songs');
 await click(page,'Queue artist');await text(page,'Artist queued: 2 tracks added.');
 await until(()=>page.locator('audio').evaluateAll(es=>es.some(e=>e.paused&&e.readyState>=1&&e.currentTime<.1)),'artist prepared paused');
 await delay(1500);await page.screenshot({path:path.join(out,'artist-prepared.png')});
 await click(page,'Play');await until(()=>page.locator('audio').evaluateAll(es=>es.some(e=>!e.paused&&e.currentTime>.5)),'actual playback');
 await delay(1800);const before=await page.locator('audio').evaluateAll(es=>es.map(e=>({src:e.currentSrc,time:e.currentTime})));
 await click(page,'Queue artist');await text(page,'Artist already in queue.');
 const after=await page.locator('audio').evaluateAll(es=>es.map(e=>({src:e.currentSrc,time:e.currentTime,paused:e.paused})));
 assert.equal(after[0].src,before[0].src);assert.ok(after[0].time>before[0].time&&!after[0].paused);
 await click(page,'Open '+album);await page.getByRole('button',{name:'Queue album',exact:true}).waitFor();
 await click(page,'Back to Example Artist');await click(page,'Queue artist');await text(page,'Artist already in queue.');
 await click(page,'Now Playing');await click(page,'Queue');await text(page,'2 tracks');
 assert.equal(await page.getByRole('button',{name:/^Play queued /}).count(),2);
 await delay(2000);await page.screenshot({path:path.join(out,'queued.png')});await click(page,'Pause');await delay(2200);
 await click(page,'Clear queue');await text(page,'0 tracks');await click(page,'Back to Now Playing');await click(page,'Library');
 await delay(1300);await stopCapture();
 for(const [width,height] of [[390,600],[1440,960]]){
  await page.setViewportSize({width,height});await delay(800);
  const action=await page.getByRole('button',{name:'Queue artist',exact:true}).boundingBox();
  const heading=await page.getByText('Example Artist',{exact:true}).first().boundingBox();
  assert.ok(action.x>=heading.x+heading.width&&action.x+action.width<=width,'artist action fits beside identity');
  await page.screenshot({path:path.join(out,width+'-artist.png')});
 }
 await page.setViewportSize({width:390,height:844});await delay(500);await click(page,'Back to Artists');
 // Multi-page artist spanning albums, failed pages, retry, order and cancellation.
 for(let i=1;i<=28;i++) {
  const file=path.join(work,'page-'+i+'.mp3');
  command('ffmpeg',['-v','error','-i',path.join(work,'base.mp3'),'-c','copy','-metadata','title=Page track '+String(i).padStart(2,'0'),'-metadata','artist=Pagination Fixture','-metadata','album=Album '+(i%2),file]);
  assert.equal((await fetch(origin+'/api/tracks?filename=page-'+i+'.mp3',{method:'POST',headers:{'Content-Type':'audio/mpeg'},body:fs.readFileSync(file)})).status,201);
 }
 await click(page,'All songs');await click(page,'Artists');await click(page,'Open Pagination Fixture');await text(page,'28 songs');
 const pattern='**/api/tracks?**';
 await page.route(pattern,r=>Number(new URL(r.request().url()).searchParams.get('offset')||0)>=25?r.fulfill({status:503,body:''}):r.continue());
 await click(page,'Queue artist');await until(async()=>(await page.locator('body').innerText()).includes('Artist was not queued.'),'failed page');
 await click(page,'Now Playing');await click(page,'Queue');await text(page,'0 tracks');await click(page,'Back to Now Playing');await click(page,'Library');await page.unroute(pattern);
 await click(page,'Queue artist');await text(page,'Artist queued: 28 tracks added.');
 await click(page,'Now Playing');await click(page,'Queue');await text(page,'28 tracks');
 const queued=await page.getByRole('button',{name:/^Play queued Page track /}).evaluateAll(es=>es.map(e=>e.getAttribute('aria-label')));
 const expected=[];let next=(await(await fetch(origin+'/api/library/artists?limit=200')).json()).items.find(g=>g.artist==='Pagination Fixture')._links.tracks.href;
 while(next){const data=await(await fetch(origin+next)).json();expected.push(...data.items.map(t=>'Play queued '+t.title));next=data._links?.next?.href;}
 assert.deepEqual(queued,expected,'artist follows server catalogue order');
 await click(page,'Clear queue');await click(page,'Back to Now Playing');await click(page,'Library');
 let release,intercepted=false;const gate=new Promise(r=>release=r);
 await page.route(pattern,async r=>{intercepted=true;await gate;await r.continue()});
 await click(page,'Queue artist');await until(async()=>intercepted,'pending artist request');await click(page,'Back to Artists');release();await delay(1000);await page.unroute(pattern);
 await click(page,'Now Playing');await click(page,'Queue');await text(page,'0 tracks');await click(page,'Back to Now Playing');await click(page,'Library');
 // Clearing through Queue also cancels a staged addition without leaving late tracks.
 await click(page,'Open Pagination Fixture');
 let releaseClear,clearIntercepted=false;const clearGate=new Promise(r=>releaseClear=r);
 await page.route(pattern,async r=>{clearIntercepted=true;await clearGate;await r.continue()});
 await click(page,'Queue artist');await until(async()=>clearIntercepted,'pending before clear');
 await click(page,'Now Playing');await click(page,'Queue');await click(page,'Clear queue');releaseClear();await delay(1000);await page.unroute(pattern);await text(page,'0 tracks');
 await click(page,'Back to Now Playing');await click(page,'Library');await click(page,'Back to Artists');
 // Reject an over-capacity artist before requesting its tracks.
 await page.route('**/api/library/artists?**',async r=>{const response=await r.fetch(),data=await response.json();for(const item of data.items||[])if(item.artist==='Example Artist')item.trackCount=201;await r.fulfill({response,json:data})});
 await click(page,'All songs');await click(page,'Artists');await click(page,'Open Example Artist');await click(page,'Queue artist');await text(page,'Artist does not fit in the 200-track queue.');
 assert.equal(await page.locator('audio').count(),0);await click(page,'Back to Artists');await page.unroute('**/api/library/artists?**');
 // Old services without the additive relation keep browsing but disable queueing.
 await page.route('**/api/library/artists?**',async r=>{const response=await r.fetch(),data=await response.json();for(const item of data.items||[])delete item._links.tracks;await r.fulfill({response,json:data})});
 await click(page,'All songs');await click(page,'Artists');await click(page,'Open Example Artist');
 assert.ok(await page.getByRole('button',{name:'Queue artist',exact:true}).isDisabled());
 assert.deepEqual(errors,[]);
 fs.writeFileSync(path.join(out,'results.json'),JSON.stringify({checks:['artist queue spans albums','empty queue prepares paused','deduplicates and preserves active media','album and parent navigation restores action','compact and wide bounds','all pages in server catalogue order','failed page atomicity and retry','Back and Clear cancel pending additions','over-capacity artist rejected','older service disables unavailable action'],serviceWorkers:'blocked',audio:'actual synthetic client audio'},null,2));
 console.log('PASS artist queue, audio continuity, pagination, failure/retry, cancellation and compatibility');
})().catch(e=>{console.error(e);process.exitCode=1}).finally(async()=>{if(context)await context.close();if(browser)await browser.close();if(server)server.kill('SIGTERM');if(!process.exitCode)fs.rmSync(work,{recursive:true,force:true});});
