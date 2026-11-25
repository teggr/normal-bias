/* Actual generated-client Library play insertion acceptance; synthetic disposable media only. */
const assert = require('node:assert/strict');
const {spawn, spawnSync} = require('node:child_process');
const fs = require('node:fs'), os = require('node:os'), path = require('node:path'), net = require('node:net');
const {chromium} = require('playwright-core');
const root = path.resolve(__dirname,'../..'), out = path.join(root,'client/target/recordings/library-play');
const work = fs.mkdtempSync(path.join(os.tmpdir(),'nb-library-play-'));
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
 for(const [i,title,artist,name,cover] of [[0,'First light','Example Artist',album,true],[1,'Evening walk','Example Artist',album,true],[2,'Quiet room','Another Artist','No Cover Session',false],[3,'Sunset','Another Artist','No Cover Session',false]]) {
  const file=path.join(work,i+'.mp3');
  command('ffmpeg',['-v','error','-i',path.join(work,'base.mp3'),...(cover?['-i',path.join(work,'cover.png'),'-map','0:a','-map','1:v','-disposition:v','attached_pic']:[]),'-c','copy','-metadata','title='+title,'-metadata','artist='+artist,'-metadata','album='+name,file]);
  const res=await fetch(origin+'/api/tracks?filename='+i+'.mp3',{method:'POST',headers:{'Content-Type':'audio/mpeg'},body:fs.readFileSync(file)});assert.equal(res.status,201);
 }
 browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
 context=await browser.newContext({viewport:{width:390,height:844},serviceWorkers:'block'});
 let page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(String(e)));
 await page.goto(origin);await page.getByRole('button',{name:'Albums',exact:true}).waitFor();
 const stopCapture=await require('./capture-demo.cjs').start(page,out);
 const events=[];const mark=name=>events.push({name,at:Date.now()/1000});
 const audio=()=>page.locator('audio').evaluateAll(es=>es.map(e=>({src:e.currentSrc,time:e.currentTime,paused:e.paused})));
 const plus=async index=>{const b=await page.getByRole('button',{name:'+ Queue',exact:true}).nth(index).boundingBox();await page.mouse.click(b.x+b.width/2,b.y+b.height/2);await delay(600)};
 const queue=async()=>{await click(page,'Now Playing');await click(page,'Queue');return page.getByRole('button',{name:/^Play queued /}).evaluateAll(es=>es.map(e=>e.getAttribute('aria-label')))};
 const library=async()=>{await click(page,'Back to Now Playing');await click(page,'Library')};
 await plus(0);await plus(2); // Evening, Quiet; first prepared paused.
 await until(async()=>(await audio())[0]?.paused,'prepared');
 await click(page,'Play First light');mark('inserted-playing');
 await until(async()=>(await audio())[0]?.time>.4&&!(await audio())[0]?.paused,'inserted track playing');
 assert.ok(await page.getByRole('button',{name:'Next track',exact:true}).isEnabled());
 const playing=(await audio())[0];await plus(3);
 assert.equal((await audio())[0].src,playing.src);assert.ok((await audio())[0].time>=playing.time);
 assert.deepEqual(await queue(),['Play queued Evening walk','Play queued First light','Play queued Quiet room','Play queued Sunset']);
 await page.screenshot({path:path.join(out,'inserted-queue.png')});await library();
 await click(page,'Next track');mark('next');
 await until(async()=>(await audio())[0]?.src!==playing.src&&!(await audio())[0]?.paused,'Next follows insertion');
 await click(page,'Play First light');
 assert.deepEqual(await queue(),['Play queued Evening walk','Play queued First light','Play queued Quiet room','Play queued Sunset']);
 await click(page,'Back to Now Playing');
 const rail=await page.getByRole('slider').boundingBox();
 await page.mouse.click(rail.x+rail.width*.99,rail.y+rail.height/2);mark('seek-near-end');
 await until(async()=>(await audio())[0]?.src!==playing.src&&!(await audio())[0]?.paused,'completion follows insertion');mark('completion');
 await click(page,'Library');await click(page,'Show search');
 assert.equal(await page.getByRole('button',{name:'Reset',exact:true}).count(),0,'no search X');
 const editor=page.locator('input:focus,textarea:focus');await editor.waitFor();await editor.fill('First');await page.keyboard.press('Tab');await click(page,'Search');
 assert.equal(await page.getByRole('button',{name:/^Play (First light|Evening walk|Quiet room|Sunset)$/}).count(),1);
 await click(page,'Hide search');assert.equal(await page.getByRole('textbox').count(),0);
 await click(page,'Show search');await editor.waitFor();assert.equal(await editor.inputValue(),'First');
 await require('./search-input.cjs').clear(page,name=>click(page,name));
 await until(async()=>await page.getByRole('button',{name:/^Play (First light|Evening walk|Quiet room|Sunset)$/}).count()===4,'cleared search');
 await page.screenshot({path:path.join(out,'search-without-x.png')});mark('search-cleared');await delay(1500);
 await click(page,'Hide search');await click(page,'Pause');mark('paused');await delay(1800);
 await stopCapture();fs.writeFileSync(path.join(out,'events.json'),JSON.stringify(events,null,2));
 for(const [width,height] of [[390,600],[1000,780]]){await page.setViewportSize({width,height});await delay(700);await click(page,'Show search');const field=await page.getByRole('textbox').first().boundingBox();const find=await page.getByRole('button',{name:'Search',exact:true}).boundingBox();assert.ok(field.width>150&&find.x+find.width<=width);assert.equal(await page.getByRole('button',{name:'Reset',exact:true}).count(),0);await page.screenshot({path:path.join(out,`${width}x${height}.png`)});await click(page,'Hide search')}
 assert.deepEqual(errors,[]);await context.close();context=null;
 console.log('PASS Library insertion/order, existing selection/deduplication, append continuity, Next, real completion, search toggle/query/clear, compact/wide bounds');
})().catch(e=>{console.error(e);process.exitCode=1}).finally(async()=>{if(context)await context.close();if(browser)await browser.close();if(server)server.kill('SIGTERM');if(!process.exitCode)fs.rmSync(work,{recursive:true,force:true});});
