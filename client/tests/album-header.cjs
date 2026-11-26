/* Actual generated-client album-header acceptance; synthetic disposable media only. */
const assert = require('node:assert/strict');
const {spawn, spawnSync} = require('node:child_process');
const fs = require('node:fs'), os = require('node:os'), path = require('node:path'), net = require('node:net');
const {chromium} = require('playwright-core');
const root = path.resolve(__dirname,'../..'), out = path.join(root,'client/target/recordings/mini-next');
const work = fs.mkdtempSync(path.join(os.tmpdir(),'nb-album-header-'));
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
 for(const [i,title,artist,name,cover] of [[0,'First light','Example Artist',album,true],[1,'Evening walk','Example Artist',album,true],[2,'Quiet room','Another Artist','No Cover Session',false]]) {
  const file=path.join(work,i+'.mp3');
  command('ffmpeg',['-v','error','-i',path.join(work,'base.mp3'),...(cover?['-i',path.join(work,'cover.png'),'-map','0:a','-map','1:v','-disposition:v','attached_pic']:[]),'-c','copy','-metadata','title='+title,'-metadata','artist='+artist,'-metadata','album='+name,file]);
  const res=await fetch(origin+'/api/tracks?filename='+i+'.mp3',{method:'POST',headers:{'Content-Type':'audio/mpeg'},body:fs.readFileSync(file)});assert.equal(res.status,201);
 }
 browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
 context=await browser.newContext({viewport:{width:390,height:844},serviceWorkers:'block'});
 let page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(String(e)));
 await page.goto(origin);await page.getByRole('button',{name:'Albums',exact:true}).waitFor();
 const stopCapture=await require('./capture-demo.cjs').start(page,out);
 await click(page,'Albums');await click(page,'Open '+album);
 await text(page,album);await text(page,'2 songs');await text(page,'Example Artist');
 assert.equal(await page.getByRole('button',{name:/Jump to/}).count(),0,'album contents omit rail');
 await delay(1200);await page.screenshot({path:path.join(out,'albums.png')});
 assert.equal(await page.getByRole('button',{name:'Close album',exact:true}).count(),0,'contextual Back replaces Close');
 await click(page,'Queue album');await text(page,'Album queued: 2 tracks added.');
 assert.equal(await page.locator('audio').evaluateAll(es=>es.some(e=>!e.paused)),false,'queueing does not start playback');
 await until(()=>page.locator('audio').evaluateAll(es=>es.some(e=>e.paused&&e.readyState>=1&&e.currentTime<.1)),'first album track prepared paused');
 await until(()=>page.getByRole('button',{name:'Play',exact:true}).isEnabled(),'prepared Play enabled');
 await page.screenshot({path:path.join(out,'prepared-album.png')});
 const prepared=await page.locator('audio').evaluateAll(es=>es.map(e=>e.currentSrc));
 await click(page,'Play');await until(()=>page.locator('audio').evaluateAll(es=>es.some(e=>!e.paused&&e.currentTime>.5)),'playback');
 const first=Date.now()/1000;assert.equal((await page.locator('audio').evaluateAll(es=>es.map(e=>e.currentSrc)))[0],prepared[0],'Play starts the prepared resource');await delay(1300);
 const before=await page.locator('audio').evaluateAll(es=>es.map(e=>({src:e.currentSrc,time:e.currentTime})));
 await click(page,'Queue album');await text(page,'Album already in queue.');
 const after=await page.locator('audio').evaluateAll(es=>es.map(e=>({src:e.currentSrc,time:e.currentTime,paused:e.paused})));
 assert.equal(after[0].src,before[0].src);assert.ok(after[0].time>=before[0].time&&!after[0].paused,'queue album preserves live stream');
 await click(page,'Pause');const paused=Date.now()/1000;await delay(1600);
 assert.ok(await page.locator('audio').evaluateAll(es=>es.every(e=>e.paused)));
 await page.screenshot({path:path.join(out,'mini-paused.png')});
 await click(page,'Play');const renewed=Date.now()/1000;await delay(1500);
 const beforeSkip=(await page.locator('audio').evaluateAll(es=>es.map(e=>e.currentSrc)))[0];
 await click(page,'Next track');
 await until(()=>page.locator('audio').evaluateAll((es,previous)=>es.some(e=>e.currentSrc!==previous&&!e.paused&&e.currentTime>.5),beforeSkip),'mini-player advances to next track');
 assert.ok(await page.getByRole('button',{name:'Next track',exact:true}).isDisabled(),'Next disabled at queue end');
 const lastSrc=(await page.locator('audio').evaluateAll(es=>es.map(e=>e.currentSrc)))[0];
 await click(page,'Next track');assert.equal((await page.locator('audio').evaluateAll(es=>es.map(e=>e.currentSrc)))[0],lastSrc,'last-track skip does nothing');
 await click(page,'Open Now Playing');await click(page,'Library');
 await page.screenshot({path:path.join(out,'mini-next.png')});
 const titleBounds=await page.getByRole('button',{name:'Open Now Playing',exact:true}).boundingBox();
 const titlePixels=spawnSync('ffmpeg',['-v','error','-i',path.join(out,'mini-next.png'),'-vf',`crop=${Math.floor(titleBounds.width)}:${Math.floor(titleBounds.height)}:${Math.floor(titleBounds.x)}:${Math.floor(titleBounds.y)}`,'-f','rawvideo','-pix_fmt','rgb24','pipe:1']);
 assert.equal(titlePixels.status,0);let amber=0;for(let i=0;i<titlePixels.stdout.length;i+=3)if(titlePixels.stdout[i]>190&&titlePixels.stdout[i+1]>130&&titlePixels.stdout[i+2]<110)amber++;
 assert.equal(amber,0,'mini-player title has no orange highlight after opening/returning');


 const pixels=spawnSync('ffmpeg',['-v','error','-i',path.join(out,'albums.png'),'-vf','crop=70:70:35:180','-f','rawvideo','-pix_fmt','rgb24','pipe:1']);
 assert.equal(pixels.status,0);let blue=0;for(let i=0;i<pixels.stdout.length;i+=3)if(pixels.stdout[i+2]>120&&pixels.stdout[i]<75)blue++;
 assert.ok(blue>4000,'header renders actual embedded square cover');
 await click(page,'Library views');await click(page,'Artists');await click(page,'Open Example Artist');await click(page,'Open '+album);
 await text(page,album);await text(page,'2 songs');
 await click(page,'Queue album');await text(page,'Album already in queue.');
 await delay(1200);await page.screenshot({path:path.join(out,'artist-album.png')});
 await click(page,'Library views');await page.getByRole('button',{name:'Open '+album,exact:true}).waitFor();
 await click(page,'Library views');await page.getByRole('button',{name:'Open Example Artist',exact:true}).waitFor();
 await click(page,'Albums');await click(page,'Open No Cover Session');await text(page,'1 song');await delay(900);await page.screenshot({path:path.join(out,'fallback.png')});
 await click(page,'Library views');await click(page,'Open '+album);await delay(1500);
 await click(page,'Now Playing');await click(page,'Queue');await click(page,'Clear queue');
 const ending=Date.now()/1000;
 await text(page,'0 tracks');assert.equal(await page.locator('audio').count(),0,'Clear unloads active audio element');
 await click(page,'Back to Now Playing');assert.ok(await page.getByRole('button',{name:'Play',exact:true}).isDisabled());
 await click(page,'Library');await text(page,'Choose a track');await delay(1300);
 await page.screenshot({path:path.join(out,'cleared.png')});
 await stopCapture();
 const timing=JSON.parse(fs.readFileSync(path.join(out,'timing.json')));
 fs.writeFileSync(path.join(out,'audio-times.json'),JSON.stringify({first:first-timing.start+.3,paused:paused-timing.start+.4,renewed:renewed-timing.start+.3,ending:ending-timing.start+.4}));
 await context.close();
 context=await browser.newContext({viewport:{width:390,height:844},serviceWorkers:'block'});
 page=await context.newPage();page.on('pageerror',e=>errors.push(String(e)));await page.goto(origin);
 await click(page,'Albums');await click(page,'Open '+album);
 for(const [width,height] of [[390,600],[800,780]]) {
  await page.setViewportSize({width,height});await delay(900);await text(page,album);
  const track=await page.getByRole('button',{name:'Play First light',exact:true}).boundingBox();
  const back=await page.getByRole('button',{name:'Back to Albums',exact:true}).boundingBox();
  assert.ok(track.y>back.y+back.height,'header does not overlap first track');
  const skip=await page.getByRole('button',{name:'Next track',exact:true}).boundingBox();
  const titleBox=await page.getByRole('button',{name:'Open Now Playing',exact:true}).boundingBox();
  const playBox=await page.getByRole('button',{name:'Play',exact:true}).boundingBox();
  assert.ok(titleBox.x+titleBox.width<=playBox.x+1&&playBox.x+playBox.width<=skip.x+1&&skip.x+skip.width<=width,'metadata and both mini-player actions fit');
  await page.screenshot({path:path.join(out,`${width}x${height}.png`)});
 }
 // A single row addition also prepares a track; clearing a paused preload unloads it.
 await click(page,'Close album');await click(page,'All songs');await click(page,'+ Queue');
 await until(()=>page.locator('audio').evaluateAll(es=>es.some(e=>e.paused&&e.readyState>=1&&e.currentTime<.1)),'single queued track prepared');
 await until(()=>page.getByRole('button',{name:'Play',exact:true}).isEnabled(),'single track Play enabled');
 await click(page,'Now Playing');await click(page,'Queue');await text(page,'1 track');await click(page,'Clear queue');
 assert.equal(await page.locator('audio').count(),0,'Clear unloads prepared paused audio');
 await click(page,'Back to Now Playing');await click(page,'Library');await click(page,'Albums');await click(page,'Open '+album);
 // Album queueing must fetch all advertised pages, with no partial commit on failure.
 await click(page,'Close album');
 for(let i=1;i<=28;i++) {
  const file=path.join(work,'page-'+i+'.mp3');
  command('ffmpeg',['-v','error','-i',path.join(work,'base.mp3'),'-c','copy','-metadata','title=Page track '+String(i).padStart(2,'0'),'-metadata','artist=Pagination Fixture','-metadata','album=Paged Album','-metadata','track='+i,file]);
  assert.equal((await fetch(origin+'/api/tracks?filename=page-'+i+'.mp3',{method:'POST',headers:{'Content-Type':'audio/mpeg'},body:fs.readFileSync(file)})).status,201);
 }
 await click(page,'Albums');await click(page,'Open Paged Album');await text(page,'28 songs');
 const pattern='**/api/tracks?**';
 await page.route(pattern,route=>Number(new URL(route.request().url()).searchParams.get('offset')||0)>=25?route.fulfill({status:503,body:''}):route.continue());
 await click(page,'Queue album');await until(async()=>(await page.locator('body').innerText()).includes('Album was not queued.'),'second-page failure');
 await click(page,'Now Playing');await click(page,'Queue');await text(page,'0 tracks');
 await click(page,'Back to Now Playing');await click(page,'Library');await page.unroute(pattern);
 await click(page,'Queue album');await text(page,'Album queued: 28 tracks added.');
 await click(page,'Now Playing');await click(page,'Queue');
 await click(page,'Next track');await until(()=>page.locator('audio').evaluateAll(es=>es.some(e=>!e.paused&&e.currentTime>.5)),'Queue mini-player Next');
 await click(page,'Pause');await click(page,'Back to Now Playing');await click(page,'Library');
 await click(page,'Queue album');await text(page,'Album already in queue.');
 await click(page,'Now Playing');await click(page,'Queue');await text(page,'28 tracks');
 assert.equal(await page.getByRole('button',{name:/^Play queued Page track /}).count(),28,'all pages queued once');
 const queued=await page.getByRole('button',{name:/^Play queued Page track /}).evaluateAll(es=>es.map(e=>e.getAttribute('aria-label')));
 const expected=[];let next=(await(await fetch(origin+'/api/library/albums?limit=200')).json()).items.find(g=>g.album==='Paged Album')._links.contents.href;
 while(next){const data=await(await fetch(origin+next)).json();expected.push(...data.items.map(t=>'Play queued '+t.title));next=data._links?.next?.href;}
 assert.deepEqual(queued,expected,'album preserves server catalogue order');
 await click(page,'Clear queue');await text(page,'0 tracks');await click(page,'Back to Now Playing');await click(page,'Library');
 let release;const gate=new Promise(r=>release=r);let intercepted=false,continued=false;
 await page.route(pattern,async route=>{intercepted=true;await gate;await route.continue();continued=true});
 await click(page,'Queue album');await until(async()=>intercepted,'pending album request');
 await click(page,'Close album');release();await until(async()=>continued,'released pending request');await page.unroute(pattern);await delay(700);
 await click(page,'Now Playing');await click(page,'Queue');await text(page,'0 tracks');
 assert.deepEqual(errors,[]);await context.close();context=null;
 fs.writeFileSync(path.join(out,'results.json'),JSON.stringify({checks:['Albums header/title/artist/count and embedded square cover','Artist → album header','Contextual Back restores both parents','Queue album preserves playing stream and deduplicates','Queue album prepares first track paused for immediate Play','Single row prepares first track paused','Clear unloads both playing and prepared audio','Hardware mini-player pause/resume','Missing-cover fallback','No album alphabet rail','Short and wide header separation','Multi-page album commits in catalogue order','Second-page failure leaves queue empty','Retry and repeated album queue deduplicate','Back cancels pending album queue','Mini-player Next advances on Library and Queue','Next disables at queue end','Title has no orange focus highlight after navigation','Mini-player controls fit compact and wide widths','No uncaught browser errors'],serviceWorkers:'blocked',audio:'actual synthetic client playback and pause/resume'},null,2));
 console.log('PASS album actions from Albums and Artists; contextual Back, deduplication, live audio, mini-player, fallback, short/wide layouts');
})().catch(e=>{console.error(e);process.exitCode=1}).finally(async()=>{if(context)await context.close();if(browser)await browser.close();if(server)server.kill('SIGTERM');if(!process.exitCode)fs.rmSync(work,{recursive:true,force:true});});
