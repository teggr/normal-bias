/* Generated-client pointer-state regression: wheel corners must stay transparent. */
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),os=require('node:os'),net=require('node:net');
const {spawn,spawnSync}=require('node:child_process'),{chromium}=require('playwright-core');
const root=path.resolve(__dirname,'../..'),out=path.join(root,'client/target/recordings/player-button-states'),work=fs.mkdtempSync(path.join(os.tmpdir(),'nb-player-buttons-'));
const wait=ms=>new Promise(r=>setTimeout(r,ms));let server,browser,page,stop;const checks=[],errors=[];
async function until(fn,label){for(let i=0;i<200;i++){if(await fn())return;await wait(100)}throw Error('Timeout: '+label)}
async function bounds(name){const b=await page.getByRole('button',{name,exact:true}).first().boundingBox();assert.ok(b,name);return b}
async function click(name){const b=await bounds(name);await page.mouse.click(b.x+b.width/2,b.y+b.height/2);await wait(500)}
async function pixel(png,x,y){return page.evaluate(async ({data,x,y})=>{const img=new Image();img.src='data:image/png;base64,'+data;await img.decode();const c=document.createElement('canvas');c.width=img.width;c.height=img.height;const g=c.getContext('2d');g.drawImage(img,0,0);return [...g.getImageData(Math.floor(x),Math.floor(y),1,1).data]}, {data:png.toString('base64'),x,y})}
(async()=>{
 fs.mkdirSync(out,{recursive:true});
 const port=await new Promise(resolve=>{const s=net.createServer();s.listen(0,'127.0.0.1',()=>{const p=s.address().port;s.close(()=>resolve(p))})}),origin='http://127.0.0.1:'+port;
 const log=fs.openSync(path.join(out,'api.log'),'w');server=spawn('java',['-jar',path.join(root,'api/target/normal-bias-api-0.1.0-SNAPSHOT.jar'),'--server.address=127.0.0.1','--server.port='+port,'--normalbias.auth.mode=demo','--normalbias.artwork.background-enabled=false','--normalbias.data-dir='+path.join(work,'data'),'--spring.web.resources.static-locations=file:'+path.join(root,'client/target/web/')],{stdio:['ignore',log,log]});
 await until(async()=>{try{return(await fetch(origin+'/api/tracks')).ok}catch{return false}},'API');
 for(let i=0;i<3;i++){const file=path.join(work,i+'.mp3');assert.equal(spawnSync('ffmpeg',['-v','error','-f','lavfi','-i','anullsrc=r=44100:cl=mono','-t','30','-c:a','libmp3lame','-metadata','title=Button trial '+i,file]).status,0);assert.equal((await fetch(origin+'/api/tracks?filename='+i+'.mp3',{method:'POST',headers:{'Content-Type':'audio/mpeg'},body:fs.readFileSync(file)})).status,201)}
 browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
 const context=await browser.newContext({viewport:{width:1440,height:960},serviceWorkers:'block'});page=await context.newPage();page.on('pageerror',e=>errors.push(String(e)));await page.goto(origin);await page.getByRole('button',{name:'+ Queue',exact:true}).first().waitFor();
 for(let i=0;i<3;i++){const b=await page.getByRole('button',{name:'+ Queue',exact:true}).nth(i).boundingBox();await page.mouse.click(b.x+b.width/2,b.y+b.height/2);await wait(500)}
 await until(()=>page.getByRole('button',{name:'Play',exact:true}).isEnabled(),'prepared paused track');
 if(process.env.RECORD_DEMO)stop=await require('./capture-demo.cjs').start(page,out);
 for(const [label,size] of [['desktop',{width:1440,height:960}],['tablet',{width:1024,height:1366}],['compact',{width:390,height:1000}]]){
  await page.setViewportSize(size);await wait(1000);if(label==='compact')await click('Now Playing');
  for(const name of ['Play','Previous','Next','Player information','Queue','Restart track','Shuffle queue']){
   await page.mouse.move(2,2);await wait(400);const b=await bounds(name),before=await page.screenshot();
   await page.mouse.move(b.x+b.width/2,b.y+b.height/2);await wait(700);const hovered=await page.screenshot({path:path.join(out,`${label}-${name.replaceAll(' ','-')}-hover.png`)});
   if(name==='Play'){const shade=(await pixel(before,b.x+b.width/2,b.y-8))[0];assert.ok(shade>=19&&shade<35,`${label} wheel gradient is present: ${shade}`);}
   // Corners outside the circular hardware face (and the unframed skip glyphs)
   // must show the same wheel/body pixels, even with the pointer over the control.
   for(const [dx,dy] of [[3,3],[b.width-4,3],[3,b.height-4],[b.width-4,b.height-4]])assert.deepEqual(await pixel(hovered,b.x+dx,b.y+dy),await pixel(before,b.x+dx,b.y+dy),`${label} ${name} hover corner`);
   if(name==='Restart track'){await page.mouse.down();await wait(400);await page.screenshot({path:path.join(out,`${label}-pressed.png`)});await page.mouse.up();await page.mouse.move(2,2);await wait(600);const after=await page.screenshot();assert.deepEqual(await pixel(after,b.x+3,b.y+3),await pixel(before,b.x+3,b.y+3),'released corner stays clear')}
  }
  await click('Player information');await page.getByRole('button',{name:'Show artwork',exact:true}).waitFor();await click('Show artwork');checks.push(label+': seven wheel controls retain clear corners on hover; pressed/released Restart and information toggle work');
 }
 await page.setViewportSize({width:1440,height:960});await wait(800);await page.mouse.move(2,2);await page.keyboard.press('Tab');await wait(400);await page.screenshot({path:path.join(out,'keyboard-focus.png')});
 assert.ok((await page.locator('audio').evaluateAll(es=>es.every(e=>e.paused))),'visual check never starts audio');assert.deepEqual(errors,[]);if(stop){await stop();stop=null}fs.writeFileSync(path.join(out,'result.json'),JSON.stringify({checks,errors,serviceWorkers:'blocked'},null,2));console.log('PASS',checks);
})().catch(async e=>{console.error(e);process.exitCode=1;if(page)await page.screenshot({path:path.join(out,'failure.png')}).catch(()=>{})}).finally(async()=>{if(stop)await stop().catch(()=>{});if(browser)await browser.close();if(server&&server.exitCode===null){server.kill('SIGTERM');await new Promise(r=>server.once('exit',r))}if(!process.exitCode)fs.rmSync(work,{recursive:true,force:true})});
