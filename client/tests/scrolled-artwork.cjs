/* Generated-client regression: embedded row covers survive scrolling beyond page one. */
const assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path'),net=require('node:net');
const {spawn,spawnSync}=require('node:child_process'),{chromium}=require('playwright-core');
const root=path.resolve(__dirname,'../..'),out=path.join(root,'client/target/recordings/scrolled-artwork');
const work=fs.mkdtempSync(path.join(os.tmpdir(),'nb-scrolled-art-'));let server,browser,page,stop;const loads=[];
const wait=ms=>new Promise(r=>setTimeout(r,ms));
async function until(fn,label){for(let i=0;i<240;i++){if(await fn())return;await wait(100)}throw Error('Timeout: '+label)}
function run(program,args){const r=spawnSync(program,args,{encoding:'utf8'});assert.equal(r.status,0,r.stderr)}
async function click(name){const el=page.getByRole('button',{name,exact:true}).first();await el.waitFor();const b=await el.boundingBox();await page.mouse.click(b.x+b.width/2,b.y+b.height/2);await wait(500)}
async function covers(label){
 await wait(1800);const file=path.join(out,label+'.png');await page.screenshot({path:file});
 const rgb=spawnSync('ffmpeg',['-v','error','-i',file,'-f','rawvideo','-pix_fmt','rgb24','-'],{maxBuffer:4e6});assert.equal(rgb.status,0);
 const measured=[];
 for(const el of await page.getByRole('button',{name:/^Play Scroll track /}).all()){
  const b=await el.boundingBox();if(!b||b.y<170||b.y+b.height>740)continue;
  let cyan=0;for(let y=Math.max(0,Math.floor(b.y-8));y<Math.min(844,b.y+b.height+20);y++)for(let x=8;x<Math.min(58,b.x);x++){
   const p=(y*390+x)*3,r=rgb.stdout[p],g=rgb.stdout[p+1],blue=rgb.stdout[p+2];if(blue>110&&g>75&&r<85&&blue>r*1.5)cyan++;
  }
  measured.push({name:await el.getAttribute('aria-label'),bounds:b,cyan});assert.ok(cyan>60,label+' cover visible for '+measured.at(-1).name+' ('+cyan+' cyan pixels)');
 }
 assert.ok(measured.length>=3,label+' has enough visible rows');return measured;
}
(async()=>{
 fs.mkdirSync(out,{recursive:true});const port=await new Promise(r=>{const s=net.createServer();s.listen(0,'127.0.0.1',()=>{const p=s.address().port;s.close(()=>r(p))})});const origin='http://127.0.0.1:'+port;
 const log=fs.openSync(path.join(out,'api.log'),'w');server=spawn('java',['-jar',path.join(root,'api/target/normal-bias-api-0.1.0-SNAPSHOT.jar'),'--server.address=127.0.0.1','--server.port='+port,'--normalbias.auth.mode=demo','--normalbias.artwork.background-enabled=false','--normalbias.data-dir='+path.join(work,'data'),'--spring.web.resources.static-locations=file:'+path.join(root,'client/target/web/')],{stdio:['ignore',log,log]});
 await until(async()=>{try{return(await fetch(origin+'/api/tracks')).ok}catch{return false}},'API');
 run('ffmpeg',['-v','error','-f','lavfi','-i','sine=frequency=330:duration=1','-c:a','libmp3lame',path.join(work,'base.mp3')]);
 run('ffmpeg',['-v','error','-f','lavfi','-i','color=c=0x287fa8:s=80x80','-frames:v','1',path.join(work,'cover.png')]);
 for(let i=0;i<36;i++){const file=path.join(work,'track.mp3');run('ffmpeg',['-v','error','-y','-i',path.join(work,'base.mp3'),'-i',path.join(work,'cover.png'),'-map','0:a','-map','1:v','-disposition:v','attached_pic','-c','copy','-metadata','title=Scroll track '+String(i).padStart(2,'0'),'-metadata','artist=Normal Bias fixtures','-metadata','album=Scroll cover trial',file]);const res=await fetch(origin+'/api/tracks?filename=track-'+i+'.mp3',{method:'POST',headers:{'Content-Type':'audio/mpeg'},body:fs.readFileSync(file)});assert.equal(res.status,201)}
 browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});const context=await browser.newContext({viewport:{width:390,height:844},serviceWorkers:'block'});page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(String(e)));page.on('request',r=>{if(r.url().includes('/tracks?')&&r.method()==='GET')loads.push(r.url())});await page.goto(origin);await page.getByRole('button',{name:'Play Scroll track 00',exact:true}).waitFor();
 await click('Albums');await click('Open Scroll cover trial');
 const first=await covers('first');if(process.env.RECORD_DEMO)stop=await require('./capture-silent.cjs').start(page,out);
 await page.mouse.move(200,550);await page.mouse.wheel(0,600);await wait(700);const scrolled=await covers('scrolled');
 for(let i=0;i<4;i++){await page.mouse.wheel(0,560);await wait(700)}
 const later=await covers('later-page');assert.ok(later.some(x=>/track (2[5-9]|3[0-5])/.test(x.name)),'visible covers beyond the first 25 tracks');
 assert.ok(loads.some(u=>Number(new URL(u).searchParams.get('offset'))>=25),'later API page requested');
 await page.mouse.wheel(0,-3000);await wait(800);const restored=await covers('restored');
 // Fixed chrome must not be painted over by thumbnails crossing the scroll edges.
 for(const crop of ['390:110:0:0','390:65:0:779']){
  const strips=['first','scrolled','later-page'].map(label=>{const r=spawnSync('ffmpeg',['-v','error','-i',path.join(out,label+'.png'),'-vf','crop='+crop,'-f','rawvideo','-pix_fmt','rgb24','-']);assert.equal(r.status,0);return r.stdout});
  for(const strip of strips.slice(1))assert.deepEqual(strip,strips[0],'fixed header/mini-player stays clear of scrolled covers');
 }
 if(stop){await stop();stop=null}assert.deepEqual(errors,[]);fs.writeFileSync(path.join(out,'result.json'),JSON.stringify({first,scrolled,later,restored,loads,errors,serviceWorkers:'blocked'},null,2));console.log('PASS embedded covers on first/scrolled/later-page/restored rows');
})().catch(async e=>{console.error(e);process.exitCode=1;if(page)await page.screenshot({path:path.join(out,'failure.png')})}).finally(async()=>{if(stop)await stop('failed-take.mp4');if(browser)await browser.close();if(server&&server.exitCode===null){server.kill();await new Promise(r=>server.once('exit',r))}if(!process.exitCode)fs.rmSync(work,{recursive:true,force:true})});
