/* Actual generated client + disposable 14,000-row server fixture. No personal data. */
const assert=require('node:assert/strict');
const fs=require('node:fs'),os=require('node:os'),path=require('node:path'),net=require('node:net');
const {spawn,spawnSync}=require('node:child_process');
const {chromium}=require('playwright-core');
const repo=path.resolve(__dirname,'../..');
const work=fs.mkdtempSync(path.join(os.tmpdir(),'normalbias-browse-'));
const out=path.join(repo,'client/target/recordings/library-scroll');fs.mkdirSync(out,{recursive:true});
let server,browser,page,stopCapture;
const delay=ms=>new Promise(r=>setTimeout(r,ms));
async function until(check,description,timeout=25000){const end=Date.now()+timeout;while(Date.now()<end){if(await check())return;await delay(150);}throw new Error('Timed out: '+description);}
async function click(name){if(name==='Library views'&&await page.getByRole('button',{name:'Close album',exact:true}).count())name='Close album';const b=page.getByRole('button',{name,exact:true}).first();await b.waitFor();const box=await b.boundingBox();assert.ok(box,name);await page.mouse.click(box.x+box.width/2,box.y+box.height/2);await delay(450);}
async function song(name){await page.getByRole('button',{name:'Play '+name,exact:true}).waitFor();}
async function jump(letter){await until(async()=>{const b=page.getByRole('button',{name:'Jump to '+letter,exact:true});return await b.count()&&await b.isEnabled();},'alphabet ready for '+letter);await click('Jump to '+letter);await delay(500);}
async function drag(up=true){await page.mouse.move(160,up?600:250);await page.mouse.down();await page.mouse.move(160,up?230:620,{steps:18});await page.mouse.up();await delay(450);}
async function search(query){if(!await page.getByRole('textbox').count())await click('Show search');const box=await page.getByRole('textbox').first().boundingBox();await page.mouse.click(box.x+30,box.y+box.height/2);const editor=page.locator('input:focus,textarea:focus');await editor.waitFor();await editor.fill(query);await page.keyboard.press('Tab');await click('Search');}
async function audio(){return page.locator('audio').evaluateAll(es=>es.map(e=>({src:e.currentSrc,time:e.currentTime,paused:e.paused})));}
(async()=>{
 const port=await new Promise(resolve=>{const s=net.createServer();s.listen(0,'127.0.0.1',()=>{const p=s.address().port;s.close(()=>resolve(p));});});
 const origin='http://127.0.0.1:'+port;
 const cp=[path.join(repo,'api/target/test-classes'),path.join(repo,'api/target/classes'),fs.readFileSync(path.join(repo,'api/target/test-classpath.txt'),'utf8').trim()].join(path.delimiter);
 const log=fs.openSync(path.join(work,'api.log'),'w');
 server=spawn('java',['-cp',cp,'dev.normalbias.api.BrowseFixtureApplication','--server.port='+port,'--normalbias.auth.mode=demo','--normalbias.artwork.background-enabled=false','--normalbias.data-dir='+path.join(work,'data'),'--spring.web.resources.static-locations=file:'+path.join(repo,'client/target/web/')],{stdio:['ignore',log,log]});
 await until(async()=>{try{return(await(await fetch(origin+'/api/tracks')).json()).total===14000;}catch{return false;}},'14,000-track fixture',60000);
 // Only this uploaded track is played. Other rows intentionally model catalogue scale without audio files.
 const file=path.join(work,'demo.mp3');
 assert.equal(spawnSync('ffmpeg',['-v','error','-f','lavfi','-i','aevalsrc=0.07*sin(2*PI*(220+55*mod(floor(t*2)\\,4))*t)+0.04*sin(2*PI*330*t):s=44100:d=180','-ac','2','-c:a','libmp3lame','-b:a','96k','-metadata','title=A listening demo','-metadata','artist=Normal Bias synthetic trial','-metadata','album=A demonstration','-y',file]).status,0);
 const upload=await fetch(origin+'/api/tracks?filename=demo.mp3',{method:'POST',headers:{'Content-Type':'audio/mpeg'},body:fs.readFileSync(file)});assert.equal(upload.status,201);
 browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
 const context=await browser.newContext({viewport:{width:390,height:844},serviceWorkers:'block'});page=await context.newPage();
 const errors=[],loads=[];page.on('pageerror',e=>errors.push(String(e)));
 page.on('request',r=>{const u=new URL(r.url());if(r.method()==='GET'&&['/api/tracks','/api/library/albums','/api/library/artists','/api/library/index'].includes(u.pathname))loads.push(u.pathname+u.search);});
 await page.goto(origin);await song('A listening demo');await page.getByRole('button',{name:'Jump to Z',exact:true}).waitFor();await delay(900);
 await page.screenshot({path:path.join(out,'initial.png')});
 assert.equal(await page.getByRole('button',{name:'More',exact:true}).count(),0);assert.equal(await page.getByRole('button',{name:'Back',exact:true}).count(),0);
 assert.equal(await page.getByRole('button',{name:/^Jump to /}).count(),27);
 console.log('PASS complete alphabet and no visible pagination');
 await click('Play A listening demo');await until(async()=>(await audio()).some(a=>!a.paused&&a.time>.3),'actual audio advancing');
 const playing=(await audio())[0].src;
 await delay(1400);
 const beforeJump=loads.length;await jump('M');await song('M track 06000');
 assert.ok(loads.slice(beforeJump).some(p=>p.includes('offset=6001')),'server jump goes directly to M');
 assert.ok(loads.slice(beforeJump).filter(p=>p.startsWith('/api/tracks?')).length<5,'no download of A-L');
 await page.screenshot({path:path.join(out,'middle.png')});await delay(1500);
 console.log('PASS direct M jump');
 // Traverse beyond five batches, forcing eviction, and reverse to force reloading.
 let startLoads=loads.length;
 for(let i=0;i<22;i++)await drag(true);
 const forward=loads.slice(startLoads).filter(p=>p.startsWith('/api/tracks?'));
 assert.ok(forward.length>=5,'automatic forward loading across five batches');
 assert.ok(await page.getByRole('button',{name:/^Play [A-Z7].*track/}).count()<=125,'bounded rendered rows after forward eviction');
 await page.screenshot({path:path.join(out,'forward.png')});
 startLoads=loads.length;for(let i=0;i<16;i++)await drag(false);
 assert.ok(loads.slice(startLoads).some(p=>p.startsWith('/api/tracks?')),'backward scrolling reloads evicted rows');
 assert.ok(await page.getByRole('button',{name:/^Play [A-Z7].*track/}).count()<=125);
 console.log('PASS forward/reverse loading with at most 125 rows');
 // Slide on the actual rail, and retain only the most recent destination.
 const a=await page.getByRole('button',{name:'Jump to A',exact:true}).boundingBox();
 const z=await page.getByRole('button',{name:'Jump to Z',exact:true}).boundingBox();
 await page.mouse.move(a.x+a.width/2,a.y+a.height/2);await page.mouse.down();
 await page.mouse.move(z.x+z.width/2,z.y+z.height/2,{steps:18});await delay(350);
 await page.screenshot({path:path.join(out,'sliding.png')});await page.mouse.up();
 await song('Z track 12500');await delay(1000);
 await until(async()=>await page.getByText('Z',{exact:true}).count()<=1,'touch preview dismissed on release');
 await page.screenshot({path:path.join(out,'slide-released.png')});
 assert.ok(loads.filter(p=>p.includes('offset=12501')).length>0);
 await jump('#');await song('7 track 13000');await delay(1000);
 await page.screenshot({path:path.join(out,'nonletters.png')});
 console.log('PASS slide selection and # destination');
 // Visit a group and return to its parent anchor.
 await click('Albums');await jump('M');await page.getByRole('button',{name:'Open M Album 0',exact:true}).waitFor();
 await click('Open M Album 0');assert.equal(await page.getByRole('button',{name:/^Jump to /}).count(),0,'album contents retain catalogue order');
 await click('Library views');await page.getByRole('button',{name:'Open M Album 0',exact:true}).waitFor();
 await click('Artists');await jump('Z');await page.getByRole('button',{name:'Open Z Artist 0',exact:true}).waitFor();
 await click('Open Z Artist 0');await page.getByRole('button',{name:'Jump to Z',exact:true}).waitFor();await click('Library views');
 console.log('PASS grouped alphabet, album contents without rail, parent restoration');
 await click('All songs');await search('M track');await song('M track 06000');
 await jump('A');await song('M track 06000'); // Missing A falls forward to M in the filtered collection.
 await search('absent result');await until(async()=>(await page.locator('body').innerText()).includes('No tracks match your search.'),'empty search');
 await require('./search-input.cjs').clear(page, click);await song('A listening demo');
 console.log('PASS filtered index, missing-letter fallback and empty results');
 // Failed incremental request preserves currently rendered rows and supports retry.
 await page.route('**/api/tracks?**',route=>{const u=new URL(route.request().url());return Number(u.searchParams.get('offset')||0)>0?route.fulfill({status:503,body:''}):route.continue();});
 await jump('M');await until(async()=>(await page.locator('body').innerText()).includes('Library unavailable'),'jump failure');
 await song('A listening demo');await page.unroute('**/api/tracks?**');await click('Retry library');await song('M track 06000');
 assert.ok((await audio()).some(a=>a.src===playing&&!a.paused),'browsing preserves active media');
 const loadCount=loads.length;await click('Now Playing');await click('Library');await song('M track 06000');assert.equal(loads.length,loadCount,'top-level navigation preserves the loaded collection');
 console.log('PASS failure/retry and playback/navigation continuity');
 await page.setViewportSize({width:390,height:600});await delay(700);await page.screenshot({path:path.join(out,'short.png')});
 const first=await page.getByRole('button',{name:'Jump to A',exact:true}).boundingBox(),last=await page.getByRole('button',{name:'Jump to #',exact:true}).boundingBox();
 assert.ok(first.y>=100&&last.y+last.height<540,'full rail fits short viewport above mini-player');
 await page.setViewportSize({width:1000,height:780});await delay(700);await page.screenshot({path:path.join(out,'wide.png')});
 // Keyboard can focus and activate an individual letter, independently of the persistent highlight.
 await page.getByRole('button',{name:'Jump to Z',exact:true}).focus();await page.keyboard.press('Enter');await song('Z track 12500');
 console.log('PASS short/wide layout and keyboard letter activation');
 // Deliberately hold a catalogue response while the user changes context.
 await click('All songs');await song('A listening demo');
 let release,held=false;const gate=new Promise(r=>release=r);
 await page.route('**/api/tracks?**',async route=>{
   const u=new URL(route.request().url());
   if(u.searchParams.get('offset')==='6001'){const response=await route.fetch();held=true;await gate;await route.fulfill({response});}
   else await route.continue();
 });
 await jump('M');await until(()=>held,'held stale M response');await click('Artists');release();
 await page.getByRole('button',{name:'Open A Artist 0',exact:true}).waitFor();await delay(600);
 assert.equal(await page.getByRole('button',{name:'Play M track 06000',exact:true}).count(),0,'obsolete response cannot replace a new category');
 await page.unroute('**/api/tracks?**');
 console.log('PASS stale response discarded after category change');
 if(process.env.RECORD_DEMO){
   await page.setViewportSize({width:390,height:844});await click('All songs');await song('A listening demo');
   if(await page.getByRole('button',{name:'Hide search',exact:true}).count())await click('Hide search');
   await click('Now Playing');await click('Pause');await click('Library');
   stopCapture=await require('./capture-demo.cjs').start(page,out);
   await delay(1000);await click('Play A listening demo');await delay(1600);
   await jump('M');await song('M track 06000');await delay(1400);
   for(let i=0;i<9;i++){await page.mouse.move(170,450);await page.mouse.wheel(0,1800);await delay(600);}
   await delay(1200);
   for(let i=0;i<6;i++){await page.mouse.move(170,450);await page.mouse.wheel(0,-1800);await delay(600);}
   const from=await page.getByRole('button',{name:'Jump to M',exact:true}).boundingBox();
   const to=await page.getByRole('button',{name:'Jump to Z',exact:true}).boundingBox();
   await page.mouse.move(from.x+from.width/2,from.y+from.height/2);await page.mouse.down();
   await page.mouse.move(to.x+to.width/2,to.y+to.height/2,{steps:12});await delay(1000);await page.mouse.up();
   await song('Z track 12500');await delay(1200);await jump('#');await delay(1400);
   await click('Albums');await jump('M');await delay(1000);await click('Open M Album 0');await delay(1400);await click('Library views');await delay(1000);
   await click('Now Playing');await click('Pause');await delay(1400);await click('Library');await delay(1200);
   await stopCapture();stopCapture=null;
 }

 await page.setViewportSize({width:390,height:844});await jump('M');await delay(1200);
 await click('Now Playing');if(!(await audio())[0].paused)await click('Pause');await until(async()=>(await audio())[0].paused,'pause at end');
 await click('Library');await delay(1200);
 assert.deepEqual(errors,[]);
 if(stopCapture){await stopCapture();stopCapture=null;}
 fs.writeFileSync(path.join(out,'results.json'),JSON.stringify({checks:'all passed',requests:loads,errors,work},null,2));
 console.log('Large-library browser PASS; evidence '+out);
})().catch(async e=>{console.error(e);process.exitCode=1;if(page){fs.writeFileSync(path.join(out,'failure.txt'),await page.locator('body').innerText());await page.screenshot({path:path.join(out,'failure.png')});}console.error('Fixture evidence '+work);}).finally(async()=>{if(stopCapture)try{await stopCapture();}catch(e){console.error(e);}if(browser)await browser.close();if(server){server.kill('SIGTERM');await Promise.race([new Promise(r=>server.once('exit',r)),delay(5000)]);if(server.exitCode===null)server.kill('SIGKILL');}if(!process.exitCode)fs.rmSync(work,{recursive:true,force:true});});
