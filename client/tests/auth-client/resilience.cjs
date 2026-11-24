/* Real API revocation and failed-logout recovery, using the generated client. */
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const {spawnSync}=require('node:child_process');const {chromium}=require('playwright-core');
const out=path.resolve(__dirname,'../../target/auth-client'),fixture=JSON.parse(fs.readFileSync(path.join(out,'fixture.json')));
const wait=ms=>new Promise(r=>setTimeout(r,ms));
async function until(fn,label){const end=Date.now()+20000;while(Date.now()<end){if(await fn())return;await wait(100)}throw Error(label)}
async function click(p,name){if(name==='Library'&&await p.getByRole('button',{name:'Back to Now Playing',exact:true}).count())await click(p,'Back to Now Playing');if(['Sign out','Change password'].includes(name)&&!(await p.getByRole('button',{name,exact:true}).count()))await click(p,'Profile');const e=p.getByRole('button',{name,exact:true}).first();await e.waitFor();const b=await e.boundingBox();await p.mouse.click(b.x+b.width/2,b.y+b.height/2);await wait(500)}
async function login(p){
 await p.locator('input[aria-label="Username"]:focus').waitFor();await p.locator('input[aria-label="Username"]:focus').fill(fixture.username);await p.keyboard.press('Tab');await wait(500);
 const boxes=await p.locator('div[role="textbox"]').evaluateAll(es=>es.map(e=>e.getBoundingClientRect().toJSON()));const b=boxes.filter(b=>b.height>20).sort((a,b)=>b.y-a.y)[0];
 await p.mouse.click(b.x+20,b.y+b.height/2);const editor=p.locator('input[type=password]:focus');await editor.waitFor();await editor.fill(fixture.password);await p.keyboard.press('Tab');await click(p,'Sign in');
 await p.getByRole('button',{name:'Play Morning Circuit',exact:true}).waitFor();
}
(async()=>{
 const c=new crypto.X509Certificate(fs.readFileSync(path.join(out,'tls.crt'))),pin=crypto.createHash('sha256').update(c.publicKey.export({type:'spki',format:'der'})).digest('base64');
 const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true,args:['--ignore-certificate-errors-spki-list='+pin]});
 const context=await browser.newContext({viewport:{width:1180,height:844}}),p=await context.newPage();p.setDefaultTimeout(20000);const checks=[];
 try{
  await p.goto(fixture.origin+'/');await login(p);await click(p,'Play Morning Circuit');await until(()=>p.locator('audio').evaluateAll(es=>es.some(e=>!e.paused&&e.currentTime>1)),'playback');
  await p.route('**/api/library/albums?**',r=>r.fulfill({status:503,contentType:'application/json',body:'{}'}));await click(p,'Albums');
  await p.getByRole('button',{name:'Retry library',exact:true}).waitFor();assert.equal(await p.getByRole('button',{name:'Sign in',exact:true}).count(),0);
  assert.ok(await p.locator('audio').evaluateAll(es=>es.some(e=>!e.paused)));checks.push('503 preserves sign-in, queue and current playback');await p.unroute('**/api/library/albums?**');
  await p.route('**/api/auth/session',r=>r.request().method()==='DELETE'?r.abort('failed'):r.continue());await click(p,'Sign out');
  await p.getByRole('button',{name:'Retry sign out',exact:true}).waitFor();assert.equal(await p.locator('audio').count(),0);checks.push('failed logout stops audio and hides private library');
  await p.reload();await p.getByRole('button',{name:'Retry connection',exact:true}).waitFor();assert.equal(await p.getByRole('button',{name:'Play Morning Circuit',exact:true}).count(),0);checks.push('pending logout survives reload and blocks library restoration');
  await p.unroute('**/api/auth/session');await click(p,'Retry connection');await p.getByRole('button',{name:'Sign in',exact:true}).waitFor();checks.push('retry completes server logout before showing login');
  await login(p);await p.route('**/api',r=>r.abort('failed'));await p.reload();await p.getByRole('button',{name:'Retry connection',exact:true}).waitFor();await p.unroute('**/api');await click(p,'Retry connection');
  await p.getByRole('button',{name:'Play Morning Circuit',exact:true}).waitFor();checks.push('offline bootstrap retains cookie and reconnects without password');
  await click(p,'Play Morning Circuit');await until(()=>p.locator('audio').evaluateAll(es=>es.some(e=>!e.paused)),'playback before revocation');
  const id=await p.evaluate(async()=>((await(await fetch('/api')).json()).session.id));
  const admin=spawnSync('java',['-jar',path.resolve(__dirname,'../../../api/target/normal-bias-api-0.1.0-SNAPSHOT.jar'),'admin','revoke',id,'--data-dir='+path.join(out,'data')],{encoding:'utf8'});assert.equal(admin.status,0,'fixture session revoked');
  await click(p,'Albums');await p.getByRole('button',{name:'Sign in',exact:true}).waitFor();assert.equal(await p.locator('audio').count(),0);checks.push('real server revocation produces 401, stops audio and returns to login');
  await login(p);assert.equal(await p.locator('audio').count(),0);await click(p,'Now Playing');await click(p,'Queue');assert.ok(await p.getByText('0 tracks',{exact:true}).count());await click(p,'Library');checks.push('reauthentication starts with empty queue and no autoplay');
  await click(p,'Sign out');await p.getByRole('button',{name:'Sign in',exact:true}).waitFor();fs.writeFileSync(path.join(out,'resilience-result.json'),JSON.stringify({checks},null,2));console.log(JSON.stringify({checks},null,2));
 }catch(e){await p.screenshot({path:path.join(out,'resilience-failure.png')});throw e}finally{await browser.close()}
})().catch(e=>{console.error(e.message);process.exitCode=1});
