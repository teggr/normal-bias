/* Real generated Java client + disposable HTTPS API. Never reads the retained library. */
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const {chromium}=require('playwright-core');
const out=path.resolve(__dirname,'../../target/auth-client');
const fixture=JSON.parse(fs.readFileSync(path.join(out,'fixture.json')));
const wait=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function until(check,label,timeout=25000){const end=Date.now()+timeout;while(Date.now()<end){if(await check())return;await wait(100)}throw Error('Timeout: '+label)}
async function click(page,name,role='button'){
 const item=page.getByRole(role,{name,exact:true}).first();await item.waitFor();const box=await item.boundingBox();assert.ok(box,name);
 await page.mouse.click(box.x+box.width/2,box.y+box.height/2);await wait(450);
}
async function login(page){
 await page.getByRole('button',{name:'Sign in',exact:true}).waitFor();
 // Login initially focuses Username's native editor. Password remains a canvas mirror.
 const username=page.locator('input[aria-label="Username"]');await username.waitFor();await username.fill(fixture.username);await page.keyboard.press('Tab');await wait(500);
 const password=page.locator('div[role="textbox"]').filter({hasText:''});
 const boxes=await password.evaluateAll(items=>items.map(el=>({label:el.getAttribute('aria-label'),box:el.getBoundingClientRect().toJSON()})));
 const box=boxes.filter(x=>x.box.height>20).sort((a,b)=>b.box.y-a.box.y)[0]?.box;assert.ok(box,'Password canvas input');
 await page.mouse.click(box.x+20,box.y+box.height/2);
 const editor=page.locator('input[type=password]:focus');await editor.waitFor();await editor.fill(fixture.password);await page.keyboard.press('Tab');
 await click(page,'Sign in');await page.getByRole('button',{name:'Play Morning Circuit',exact:true}).waitFor();
}
async function audio(page){return page.locator('audio').evaluateAll(items=>items.map(el=>({src:el.currentSrc||el.src,time:el.currentTime,paused:el.paused,ready:el.readyState,error:el.error?.code,ended:el.ended})));}
(async()=>{
 const cert=new crypto.X509Certificate(fs.readFileSync(path.join(out,'tls.crt')));
 const pin=crypto.createHash('sha256').update(cert.publicKey.export({type:'spki',format:'der'})).digest('base64');
 const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true,args:['--ignore-certificate-errors-spki-list='+pin]});
 console.log('Starting real-client browser checks');
 const context=await browser.newContext({viewport:{width:1180,height:844}}),page=await context.newPage();
 page.setDefaultTimeout(20000);
 let grants=0,stop;
 const checks=[];page.on('request',request=>{if(request.method()==='POST'&&new URL(request.url()).pathname.endsWith('/playback-grants'))grants++});
 try{
  await page.goto(fixture.origin+'/');await login(page);checks.push('real browser cookie/CSRF login');
  const cookies=await context.cookies();const cookie=cookies.find(c=>c.name==='__Host-NormalBias-Session');
  assert.ok(cookie?.secure&&cookie.httpOnly&&cookie.expires>Date.now()/1000+86400);checks.push('persistent Secure HttpOnly cookie');
  await page.reload();await page.getByRole('button',{name:'Play Morning Circuit',exact:true}).waitFor();checks.push('reload restores server session without password');
  await click(page,'Sign out');await page.getByRole('button',{name:'Sign in',exact:true}).waitFor();
  if(process.env.RECORD_DEMO)stop=await require('../capture-demo.cjs').start(page,path.join(out,'browser-recording'));
  await login(page);
  await click(page,'Play Morning Circuit');await until(async()=>{const a=await audio(page);return a[0]?.time>1&&!a[0].paused},'authenticated playback');
  let state=(await audio(page))[0];assert.ok(new URL(state.src).searchParams.get('grant'));assert.ok(!state.src.includes('Bearer'));checks.push('audio uses scoped URL, never parent credential');
  await wait(3000);await click(page,'Pause');state=(await audio(page))[0];const paused=state.time,firstUrl=state.src;
  await wait(1500);assert.ok(Math.abs((await audio(page))[0].time-paused)<.3);checks.push('pause stops actual audio');
  console.log('Waiting for real two-minute grant expiry while paused');
  const started=Date.now();while(Date.now()-started<121000)await wait(Math.min(1000,121000-(Date.now()-started)));
  // A real pointer-driven scrub after expiry must acquire a new grant and stay paused.
  const slider=page.getByRole('slider').first();await slider.waitFor();const box=await slider.boundingBox();assert.ok(box);
  const before=grants;await page.mouse.click(box.x+box.width*.36,box.y+box.height/2);
  await until(async()=>{const a=(await audio(page))[0];return a&&a.src!==firstUrl&&a.time>40&&a.time<100&&a.paused&&a.ready>=3},'expired paused seek renews and stays paused');
  assert.equal(grants,before+1);checks.push('expired paused seek: new grant, preserved seek position, no autoplay');
  const renewed=(await audio(page))[0];
  await until(()=>slider.getAttribute('aria-disabled').then(value=>value!=='true'),'Java seek controls ready');
  await wait(750); // Finish the Java EDT/canvas frame; media metadata can precede this.
  await click(page,'Play');
  await until(async()=>{const a=(await audio(page))[0];return a.time>renewed.time+1&&!a.paused},'resume renewed playback');checks.push('renewed stream resumes at seek position');
  await wait(2500);await click(page,'Sign out');await page.getByRole('button',{name:'Sign in',exact:true}).waitFor();
  assert.equal((await audio(page)).length,0);assert.equal(await page.evaluate(url=>fetch(url).then(response=>response.status),renewed.src),401);
  checks.push('logout releases audio, clears library/queue and revokes existing grant');
  const storage=await page.evaluate(()=>({local:Object.values(localStorage),session:Object.values(sessionStorage)}));
  assert.ok(!JSON.stringify(storage).includes(fixture.password));assert.ok(!JSON.stringify(storage).includes(new URL(renewed.src).searchParams.get('grant')));checks.push('no password or playback grant in browser storage');
  const cached=await page.evaluate(async()=>{const urls=[];for(const key of await caches.keys()){for(const req of await(await caches.open(key)).keys())urls.push(new URL(req.url).pathname)}return urls});
  assert.ok(!cached.some(url=>url.startsWith('/api')));checks.push('service worker does not cache authenticated API data');
  if(stop){await stop();stop=null;}
  fs.writeFileSync(path.join(out,'browser-result.json'),JSON.stringify({checks,grants,serviceWorker:'enabled',trust:'fixture public-key pin only'},null,2));console.log(JSON.stringify({checks,grants},null,2));
 }catch(error){console.error('Audio state',(await audio(page)).map(({src,...state})=>state));await page.screenshot({path:path.join(out,'browser-failure.png')});console.error((await page.locator('body').innerText()).slice(-2500));throw error}
 finally{if(stop)await stop().catch(()=>{});await browser.close()}
})().catch(error=>{console.error(error);process.exitCode=1});
