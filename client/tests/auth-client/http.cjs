/* Authenticated HTTP with a non-loopback browser origin mapped to our disposable API. */
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {chromium}=require('playwright-core');
const out=path.resolve(process.argv[2]||'client/target/auth-http');
const fixture=JSON.parse(fs.readFileSync(path.join(out,'fixture.json')));
const origin=fixture.origin.replace('127.0.0.1','music.normalbias.test');
const wait=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function click(page,name){
 const item=page.getByRole('button',{name,exact:true}).first();await item.waitFor();
 const b=await item.boundingBox();assert.ok(b,name);await page.mouse.click(b.x+b.width/2,b.y+b.height/2);await wait(450);
}
async function login(page){
 const user=page.locator('input[aria-label="Username"]:focus');await user.waitFor();await user.fill(fixture.username);
 await page.keyboard.press('Tab');await wait(500);
 const boxes=await page.locator('div[role="textbox"]').evaluateAll(items=>items.map(el=>el.getBoundingClientRect().toJSON()));
 const box=boxes.filter(b=>b.height>20).sort((a,b)=>b.y-a.y)[0];assert.ok(box);
 await page.mouse.click(box.x+20,box.y+box.height/2);
 const password=page.locator('input[type=password]:focus');await password.waitFor();await password.fill(fixture.password);
 await page.keyboard.press('Tab');await click(page,'Sign in');await page.getByRole('button',{name:'Play Morning Circuit',exact:true}).waitFor();
}
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true,
  args:['--host-resolver-rules=MAP music.normalbias.test 127.0.0.1','--no-proxy-server']});
 const context=await browser.newContext({viewport:{width:800,height:844},serviceWorkers:'block'}),page=await context.newPage();
 page.setDefaultTimeout(25000);let stop;const errors=[],checks=[];page.on('pageerror',e=>errors.push(e.message));
 try{
  await page.goto(origin);assert.equal(await page.evaluate(()=>isSecureContext),false);
  await login(page);checks.push('real generated client signs in over non-secure HTTP');
  const cookies=await context.cookies();const session=cookies.find(c=>c.name==='NormalBias-Session');
  assert.ok(session?.httpOnly&&!session.secure&&session.expires>Date.now()/1000+86400);
  assert.ok(cookies.some(c=>c.name==='NormalBias-Csrf'&&!c.secure));
  assert.ok(!cookies.some(c=>c.name.startsWith('__Host-')));checks.push('persistent HttpOnly HTTP session and CSRF cookies accepted by Chrome');
  await page.reload();await page.getByRole('button',{name:'Play Morning Circuit',exact:true}).waitFor();checks.push('reload restores session');
  if(process.env.RECORD_DEMO)stop=await require('../capture-demo.cjs').start(page,path.join(out,'recording'));
  await wait(2000);await click(page,'Play Morning Circuit');await click(page,'Now Playing');
  await page.waitForFunction(()=>Array.from(document.querySelectorAll('audio')).some(a=>a.currentTime>1&&!a.paused));
  const audio=await page.locator('audio').first().evaluate(a=>a.currentSrc);
  assert.ok(new URL(audio).searchParams.has('grant'));checks.push('scoped grant starts actual HTTP audio');
  await wait(4500);await click(page,'Pause');await wait(2200);
  assert.equal(await page.locator('audio').first().evaluate(a=>a.paused),true);
  await click(page,'Play');await wait(3000);await click(page,'Profile');await click(page,'Sign out');
  await page.getByRole('button',{name:'Sign in',exact:true}).waitFor();await wait(1800);
  assert.equal(await page.locator('audio').count(),0);
  assert.equal(await page.evaluate(url=>fetch(url).then(r=>r.status),audio),401);checks.push('logout releases audio and revokes grant');
  assert.ok(!(await context.cookies()).some(c=>c.name==='NormalBias-Session'));assert.deepEqual(errors,[]);
  if(stop){await stop();stop=null;}
  fs.writeFileSync(path.join(out,'browser-result.json'),JSON.stringify({checks,errors,origin,serviceWorkers:'blocked',realTailnet:false},null,2));
  console.log(JSON.stringify({checks,errors},null,2));
 }catch(error){await page.screenshot({path:path.join(out,'failure.png')});console.error((await page.locator('body').innerText()).slice(-2500));throw error;}
 finally{if(stop)await stop().catch(()=>{});await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1});
