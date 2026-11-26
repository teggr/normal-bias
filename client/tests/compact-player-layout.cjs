/* Compact-player viewport geometry, including the taller iPhone Plus; isolated synthetic API. */
const assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path'),net=require('node:net');
const {spawn,spawnSync}=require('node:child_process'),{chromium}=require('playwright-core');
const root=path.resolve(__dirname,'../..'),baseline=!!process.env.CAPTURE_BASELINE;
const out=path.join(root,'client/target/recordings/compact-player-spacing',baseline?'before':'after');
const work=fs.mkdtempSync(path.join(os.tmpdir(),'nb-compact-player-'));
const sizes=[['plus',{width:430,height:932}],['pro',{width:393,height:852}],['small',{width:375,height:667}]];
const wait=ms=>new Promise(r=>setTimeout(r,ms));let server,browser,page,stop;const checks=[],metrics=[],errors=[];
async function until(fn,label){for(let i=0;i<160;i++){if(await fn())return;await wait(100)}throw Error('Timeout: '+label)}
async function click(name){const el=page.getByRole('button',{name,exact:true}).first();await el.waitFor();let b=await el.boundingBox();assert.ok(b,name);if(b.y<0||b.y+b.height>page.viewportSize().height){await page.mouse.move(200,350);await page.mouse.wheel(0,b.y<0?-500:400);await wait(500);b=await el.boundingBox()}await page.mouse.click(b.x+b.width/2,b.y+b.height/2);await wait(450)}
async function geometry(label){
 await until(async()=>{const b=await page.locator('[aria-label="Now Playing content"]').first().boundingBox();return b&&b.width<=page.viewportSize().width+1},label+' fits current viewport');
 const viewport=await page.locator('[aria-label="Now Playing content"]').first().boundingBox();
 const face=await page.locator('[aria-label="Player"]').first().boundingBox();
 assert.ok(viewport&&face,'named accessible player groups');
 const top=face.y-viewport.y,bottom=viewport.y+viewport.height-face.y-face.height;
 assert.ok(face.x>=viewport.x-1&&face.x+face.width<=viewport.x+viewport.width+1,'face fits horizontal viewport');
 if(face.height<=viewport.height+1){assert.ok(Math.abs(top-bottom)<=3,`${label}: balanced vertical space ${top}/${bottom}`);assert.ok(top>=-1&&bottom>=-1,'face inside viewport')}
 else assert.ok(Math.abs(top)<=1,'short screens start at the top and scroll');
 assert.equal(await page.getByRole('button',{name:'Open Now Playing',exact:true}).count(),0,'Now Playing has no mini-player');
 const info=await page.getByRole('button',{name:'Player information',exact:true}).boundingBox();
 const queue=await page.getByRole('button',{name:'Queue',exact:true}).first().boundingBox();
 assert.ok(info&&queue&&info.x<queue.x,'both upper hardware controls retained');
 metrics.push({label,viewport,face,top,bottom});return {viewport,face};
}
(async()=>{
 fs.mkdirSync(out,{recursive:true});const port=await new Promise(r=>{const s=net.createServer();s.listen(0,'127.0.0.1',()=>{const p=s.address().port;s.close(()=>r(p))})});const origin='http://127.0.0.1:'+port;
 const log=fs.openSync(path.join(out,'api.log'),'w');server=spawn('java',['-jar',path.join(root,'api/target/normal-bias-api-0.1.0-SNAPSHOT.jar'),'--server.address=127.0.0.1','--server.port='+port,'--normalbias.auth.mode=demo','--normalbias.artwork.background-enabled=false','--normalbias.data-dir='+path.join(work,'data'),'--spring.web.resources.static-locations=file:'+path.join(root,'client/target/web/')],{stdio:['ignore',log,log]});
 await until(async()=>{try{return(await fetch(origin+'/api/tracks')).ok}catch{return false}},'API');
 browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
 const context=await browser.newContext({viewport:sizes[0][1],serviceWorkers:'block'});page=await context.newPage();page.on('pageerror',e=>errors.push(String(e)));await page.goto(origin);await click('Now Playing');
 for(const [label,size] of sizes){await page.setViewportSize(size);await wait(650);if(!baseline)await geometry(label);await page.screenshot({path:path.join(out,label+'-empty.png')})}
 if(baseline){fs.writeFileSync(path.join(out,'result.json'),JSON.stringify({baseline:true,sizes},null,2));console.log('Captured baseline compact players');return}
 checks.push('Plus, Pro and small viewports retain proportional player and balanced unused space');
 await page.setViewportSize(sizes[0][1]);await wait(600);const before=await geometry('plus-before-information');await click('Player information');await click('Show artwork');const after=await geometry('plus-after-information');assert.deepEqual(after.face,before.face,'information toggle preserves layout');checks.push('information/artwork swap preserves player bounds');
 // Short landscape must scroll instead of shrinking the wheel below its existing comfort width.
 await page.setViewportSize({width:844,height:390});await wait(600);const short=await geometry('short-landscape');assert.ok(short.face.width>=metrics.find(m=>m.label==='pro').face.width*.95,'short landscape retains its comfort width');await page.mouse.move(400,250);await page.mouse.wheel(0,700);await wait(600);const restart=await page.getByRole('button',{name:'Restart track',exact:true}).boundingBox();assert.ok(restart.y+restart.height<390,'lower controls reachable by scrolling');checks.push('short landscape retains wheel size and scrolls to lower controls');
 await page.setViewportSize(sizes[0][1]);await wait(600);await page.mouse.move(200,300);await page.mouse.wheel(0,-2000);await wait(500);await geometry('plus-restored');
 if(process.env.RECORD_DEMO)stop=await require('./capture-silent.cjs').start(page,out);
 await wait(1400);await click('Player information');await wait(1000);await click('Show artwork');
 await page.setViewportSize(sizes[1][1]);await wait(1400);await geometry('pro-recorded');
 await page.setViewportSize({width:1440,height:960});await until(async()=>{const s=await page.getByRole('slider').boundingBox(),p=await page.getByRole('button',{name:'Previous',exact:true}).boundingBox();return s&&p&&s.x+s.width<p.x},'wide console reflow');const seek=await page.getByRole('slider').boundingBox(),previous=await page.getByRole('button',{name:'Previous',exact:true}).boundingBox();assert.ok(seek.x+seek.width<previous.x,'wide console remains horizontal');assert.equal(await page.getByRole('button',{name:'Open Now Playing',exact:true}).count(),0);checks.push('wide console composition and one-player rule preserved');await wait(1400);
 await page.setViewportSize(sizes[0][1]);await wait(1000);await geometry('plus-after-wide');await page.screenshot({path:path.join(out,'plus-final.png')});await wait(1400);
 if(stop){await stop();stop=null}assert.deepEqual(errors,[]);fs.writeFileSync(path.join(out,'result.json'),JSON.stringify({checks,metrics,errors,recording:'silent layout-only flow',serviceWorkers:'blocked'},null,2));console.log('PASS',checks.join('; '));
})().catch(async e=>{console.error(e);process.exitCode=1;if(page){await page.screenshot({path:path.join(out,'failure.png')});console.error((await page.locator('body').innerText()).slice(-1000))}}).finally(async()=>{if(stop)await stop('failed-take.mp4').catch(()=>{});if(browser)await browser.close();if(server&&server.exitCode===null){server.kill('SIGTERM');await new Promise(r=>server.once('exit',r))}if(!process.exitCode)fs.rmSync(work,{recursive:true,force:true})});
