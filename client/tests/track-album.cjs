/* Actual generated-client All songs album shortcut acceptance; synthetic disposable media only. */
const assert = require('node:assert/strict');
const {spawn, spawnSync} = require('node:child_process');
const fs = require('node:fs'), os = require('node:os'), path = require('node:path'), net = require('node:net');
const {chromium} = require('playwright-core');
const root = path.resolve(__dirname,'../..'), out = path.join(root,'client/target/recordings',process.env.DEMO_NAME||'contextual-navigation');
const work = fs.mkdtempSync(path.join(os.tmpdir(),'nb-track-album-'));
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
 for(const [i,title,artist,name,cover] of [[0,'First light','Example Artist',album,true],[1,'Evening walk','Example Artist',album,true],[2,'Quiet room','Another Artist','No Cover Session',false],[3,'Sunset','Another Artist','No Cover Session',false],...Array.from({length:28},(_,i)=>[4+i,'Z fixture '+String(i+1).padStart(2,'0'),'Scrolling fixture','Scrolling session',false])]) {
  const file=path.join(work,i+'.mp3');
  command('ffmpeg',['-v','error','-i',path.join(work,'base.mp3'),...(cover?['-i',path.join(work,'cover.png'),'-map','0:a','-map','1:v','-disposition:v','attached_pic']:[]),'-c','copy','-metadata','title='+title,'-metadata','artist='+artist,'-metadata','album='+name,file]);
  const res=await fetch(origin+'/api/tracks?filename='+i+'.mp3',{method:'POST',headers:{'Content-Type':'audio/mpeg'},body:fs.readFileSync(file)});assert.equal(res.status,201);
 }
 browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
 context=await browser.newContext({viewport:{width:390,height:844},serviceWorkers:'block'});
 let page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(String(e)));
 await page.goto(origin);await page.getByRole('button',{name:'Albums',exact:true}).waitFor();
 const audio=()=>page.locator('audio').evaluateAll(es=>es.map(e=>({src:e.currentSrc,time:e.currentTime,paused:e.paused})));
 const stop=await require('./capture-demo.cjs').start(page,out);const events=[];const mark=name=>events.push({name,at:Date.now()/1000});
 await page.getByRole('button',{name:'Open album for First light',exact:true}).waitFor();
 const albumButton=await page.getByRole('button',{name:'Open album for First light',exact:true}).boundingBox(),plus=await page.getByRole('button',{name:'+ Queue',exact:true}).nth(1).boundingBox();
 assert.ok(albumButton.x+albumButton.width<=plus.x+1&&plus.x+plus.width<=390,'album and + fit side by side');
 await click(page,'Play First light');await until(async()=>(await audio())[0]?.time>.4,'actual playback');mark('playing');const before=(await audio())[0];
 await click(page,'Show search');await page.locator('input:not([readonly]):focus,textarea:not([readonly]):focus').waitFor();await page.locator('input:not([readonly]):focus,textarea:not([readonly]):focus').fill('First');await page.keyboard.press('Tab');await click(page,'Search');await click(page,'Hide search');
 await click(page,'Open album for First light');await text(page,album);await text(page,'2 songs');
 assert.equal(await page.getByRole('button',{name:/^Open album for /}).count(),0,'no redundant album shortcuts inside album');
 await page.getByRole('button',{name:'Play Evening walk',exact:true}).waitFor();assert.equal((await audio())[0].src,before.src);assert.ok((await audio())[0].time>before.time);mark('album-open');
 await page.getByRole('button',{name:'Back to All songs',exact:true}).waitFor();
 for(const name of ['All songs','Albums','Artists','Show search','Hide search','Close album','Library views']) assert.equal(await page.getByRole('button',{name,exact:true}).count(),0,'child hides '+name);
 await page.screenshot({path:path.join(out,'mobile-album.png')});await delay(1300);await click(page,'Close album');
 await page.getByRole('button',{name:'Open album for First light',exact:true}).waitFor();assert.equal(await page.getByRole('button',{name:'Play Evening walk',exact:true}).count(),0,'Back restores filtered songs');
 await click(page,'Show search');await page.locator('input:not([readonly]):focus,textarea:not([readonly]):focus').waitFor();assert.equal(await page.locator('input:not([readonly]):focus,textarea:not([readonly]):focus').inputValue(),'First');
 await require('./search-input.cjs').clear(page,name=>click(page,name));await click(page,'Hide search');await page.screenshot({path:path.join(out,'mobile-actions.png')});mark('returned');
 // The category row becomes one contextual back action at both drill-down depths.
 await click(page,'Albums');await click(page,'Show search');
 await page.locator('input:not([readonly]):focus,textarea:not([readonly]):focus').fill('Long');await page.keyboard.press('Tab');await click(page,'Search');
 await click(page,'Open '+album);await page.getByRole('button',{name:'Back to Albums',exact:true}).waitFor();
 assert.equal(await page.getByRole('button',{name:'Search',exact:true}).count(),0,'child hides expanded parent search action');
 await page.screenshot({path:path.join(out,'albums-child.png')});
 await delay(1200);await click(page,'Back to Albums');await page.getByRole('button',{name:'Hide search',exact:true}).waitFor();
 await click(page,'Hide search');await click(page,'Artists');await click(page,'Open Example Artist');
 await page.getByRole('button',{name:'Back to Artists',exact:true}).waitFor();await text(page,'Example Artist');await text(page,'2 songs');
 const heading=await page.getByText('Example Artist',{exact:true}).first().boundingBox();const firstAlbum=await page.getByRole('button',{name:'Open '+album,exact:true}).boundingBox();
 assert.ok(heading.y+heading.height<firstAlbum.y,'artist identity sits above album list');
 assert.equal(await page.getByRole('button',{name:'Queue album',exact:true}).count(),0,'artist identity has no album queue action');
 await delay(2000);await page.screenshot({path:path.join(out,'artist.png')});
 await click(page,'Open '+album);await page.getByRole('button',{name:'Back to Example Artist',exact:true}).waitFor();await delay(1400);await page.screenshot({path:path.join(out,'artist-album.png')});
 const continuity=(await audio())[0];assert.equal(continuity.src,before.src);assert.ok(!continuity.paused&&continuity.time>before.time);
 await click(page,'Back to Example Artist');await text(page,'Example Artist');await text(page,'2 songs');await click(page,'Back to Artists');await click(page,'All songs');
 mark('contextual-paths');await delay(1000);await click(page,'Pause');mark('paused');await delay(1700);await stop();fs.writeFileSync(path.join(out,'events.json'),JSON.stringify(events,null,2));
 await page.setViewportSize({width:1440,height:960});await delay(800);await click(page,'Open album for First light');await text(page,album);await text(page,'2 songs');
 assert.equal((await audio())[0].src,before.src);
 await click(page,'Queue');await click(page,'Close queue');await page.getByRole('button',{name:'Back to All songs',exact:true}).waitFor();
 await page.setViewportSize({width:390,height:844});await delay(700);await click(page,'Now Playing');await click(page,'Library');
 await page.getByRole('button',{name:'Back to All songs',exact:true}).waitFor();
 await page.setViewportSize({width:1440,height:960});await delay(700);await click(page,'Close album');
 await page.screenshot({path:path.join(out,'wide-actions.png')});
 // Failure leaves the list and playback intent intact, and the same icon retries.
 const routePattern='**/api/library/albums?**';
 await page.route(routePattern,r=>r.fulfill({status:503,body:''}));await click(page,'Open album for First light');
 await until(async()=>(await page.locator('body').innerText()).includes('Could not open album.'),'inline album error');
 await page.getByRole('button',{name:'Play First light',exact:true}).waitFor();assert.equal((await audio())[0].src,before.src);assert.ok((await audio())[0].paused);
 await page.unroute(routePattern);await click(page,'Open album for First light');await text(page,'2 songs');await click(page,'Close album');
 // A late album response cannot replace a later category selection.
 let release,intercepted=false;const gate=new Promise(r=>release=r);
 await page.route(routePattern,async r=>{intercepted=true;await gate;await r.continue()});
 await click(page,'Open album for First light');await until(async()=>intercepted,'pending album lookup');await click(page,'Artists');release();await delay(1000);await page.unroute(routePattern);
 await page.getByRole('button',{name:'Open Example Artist',exact:true}).waitFor();assert.equal(await page.getByRole('button',{name:'Close album',exact:true}).count(),0);
 await click(page,'All songs');await click(page,'Jump to Z');await delay(600);
 await page.mouse.move(170,650);await page.mouse.down();await page.mouse.move(170,280,{steps:18});await page.mouse.up();await delay(3500);
 const visible=()=>page.getByRole('button',{name:/^Play Z fixture /}).evaluateAll(es=>es.map(e=>({name:e.getAttribute('aria-label'),y:e.getBoundingClientRect().y})).filter(e=>e.y>180&&e.y<700));
 const anchor=(await visible())[0];assert.ok(anchor,'scrolled song anchor');const chosen=anchor.name.replace('Play ','');
 await click(page,'Open album for '+chosen);await text(page,'Scrolling session');await text(page,'28 songs');await click(page,'Close album');await delay(800);
 const restored=(await visible())[0];assert.equal(restored.name,anchor.name);assert.ok(Math.abs(restored.y-anchor.y)<3,'Back restores scroll anchor');
 // Older services without the advertised relation retain playback/+ and omit the action.
 await page.route('**/api/tracks?**',async r=>{const response=await r.fetch();const data=await response.json();for(const item of data.items||[])if(item._links)delete item._links.album;await r.fulfill({response,json:data})});
 await click(page,'All songs');await page.getByRole('button',{name:'Play First light',exact:true}).waitFor();assert.equal(await page.getByRole('button',{name:/^Open album for /}).count(),0);
 // Long and missing artist identities keep a bounded, correctly labelled Back target.
 const longArtist='A Very Long Artist Name That Must Fit Inside A Compact Navigation Row Without Clipping Its Touch Target';
 for (const [i,edgeArtist] of [[90,longArtist],[91,'']]) {
  const file=path.join(work,i+'.mp3');command('ffmpeg',['-v','error','-i',path.join(work,'base.mp3'),'-c','copy','-metadata','title=Edge '+i,'-metadata','artist='+edgeArtist,'-metadata','album=Edge album '+i,file]);
  assert.equal((await fetch(origin+'/api/tracks?filename='+i+'.mp3',{method:'POST',headers:{'Content-Type':'audio/mpeg'},body:fs.readFileSync(file)})).status,201);
 }
 await page.setViewportSize({width:390,height:844});await delay(700);await click(page,'Artists');
 for(const [i,name] of [[90,longArtist],[91,'Unknown artist']]) {
  await click(page,'Open '+name);await page.getByRole('button',{name:'Back to Artists',exact:true}).waitFor();await text(page,name);await text(page,'1 song');
  for(const [width,height] of [[390,600],[1440,960]]) {
   await page.setViewportSize({width,height});await delay(600);
   const titleBox=await page.getByText(name,{exact:true}).first().boundingBox();const entryBox=await page.getByRole('button',{name:'Open Edge album '+i,exact:true}).boundingBox();
   assert.ok(titleBox.x>=0&&titleBox.x+titleBox.width<=width&&titleBox.y+titleBox.height<entryBox.y,'wrapping artist header fits and clears list');
   await click(page,'Jump to E');await page.getByRole('button',{name:'Open Edge album '+i,exact:true}).waitFor();
   await page.screenshot({path:path.join(out,'artist-edge-'+i+'-'+width+'.png')});
  }
  await page.setViewportSize({width:390,height:844});await delay(600);await click(page,'Open Edge album '+i);
  const back=page.getByRole('button',{name:'Back to '+name,exact:true});await back.waitFor();const bounds=await back.boundingBox();
  assert.ok(bounds.x>=0&&bounds.x+bounds.width<=390,'long/missing artist Back fits compact width');
  await page.screenshot({path:path.join(out,'edge-'+i+'.png')});await click(page,'Back to '+name);await click(page,'Back to Artists');
 }
 assert.deepEqual(errors,[]);await context.close();context=null;
 console.log('PASS contextual Albums/Artists/artist/All songs Back, search visibility, long/missing labels, responsive/Queue continuity; All songs-only album actions; full album despite search, compact/wide bounds, preserved audio, Back restores query/scroll, failure/retry, stale cancellation and missing-affordance compatibility');
})().catch(e=>{console.error(e);process.exitCode=1}).finally(async()=>{if(context)await context.close();if(browser)await browser.close();if(server)server.kill('SIGTERM');if(!process.exitCode)fs.rmSync(work,{recursive:true,force:true});});
