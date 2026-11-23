/* Account lifecycle acceptance through the actual Java canvas client. Disposable fixture only. */
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),https=require('node:https');
const {spawnSync}=require('node:child_process'),{chromium}=require('playwright-core');
const out=path.resolve(__dirname,'../../target/auth-client'),root=path.resolve(__dirname,'../../..');
const fixture=JSON.parse(fs.readFileSync(path.join(out,'fixture.json'))),cert=fs.readFileSync(path.join(out,'tls.crt'));
const wait=ms=>new Promise(r=>setTimeout(r,ms));
function admin(op,...args){const r=spawnSync('java',['-jar',path.join(root,'api/target/normal-bias-api-0.1.0-SNAPSHOT.jar'),'admin',op,...args,'--data-dir='+path.join(out,'data')],{encoding:'utf8'});if(r.status!==0)throw Error('Fixture admin failed: '+r.stderr);return JSON.parse(r.stdout)}
function api(url,body,bearer){return new Promise((resolve,reject)=>{const req=https.request(fixture.origin+url,{ca:cert,method:body?'POST':'GET',headers:{...(body?{'Content-Type':'application/json'}:{}),...(bearer?{Authorization:'Bearer '+bearer}:{})}},res=>{let data='';res.on('data',d=>data+=d);res.on('end',()=>resolve({status:res.statusCode,body:JSON.parse(data)}))});req.on('error',reject);req.end(body?JSON.stringify(body):undefined)})}
async function click(p,name){const e=p.getByRole('button',{name,exact:true}).first();await e.waitFor();const b=await e.boundingBox();assert.ok(b,name);await p.mouse.click(b.x+b.width/2,b.y+b.height/2);await wait(700)}
async function text(p,value){await p.getByText(value,{exact:false}).first().waitFor()}
async function fields(p,values){
 for(let i=0;i<values.length;i++){
  const boxes=await p.locator('div[role="textbox"][aria-readonly="false"]').evaluateAll(es=>es.map(e=>e.getBoundingClientRect().toJSON()).filter(b=>b.height>15&&b.width>10));
  boxes.sort((a,b)=>a.y-b.y);assert.ok(boxes[i],'field '+i);
  const b=boxes[i];await p.mouse.click(b.x+20,b.y+b.height/2);
  const input=p.locator('input:focus,textarea:focus');await input.waitFor();await input.fill(values[i]);await p.keyboard.press('Tab');await wait(250);
 }
}
async function login(p,user,password){await p.getByRole('button',{name:'Sign in',exact:true}).waitFor();await fields(p,[user,password]);await click(p,'Sign in');await p.getByRole('button',{name:'Play Morning Circuit',exact:true}).waitFor()}
async function inspect(p,kind,token){await p.evaluate(h=>{location.hash=h},kind+'='+token);await text(p,'Choose a password of 12');assert.equal(await p.evaluate(()=>location.hash),'')}
(async()=>{
 const x=new crypto.X509Certificate(cert),pin=crypto.createHash('sha256').update(x.publicKey.export({type:'spki',format:'der'})).digest('base64');
 const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true,args:['--ignore-certificate-errors-spki-list='+pin]});
 const ctx=await browser.newContext({viewport:{width:1180,height:844}}),p=await ctx.newPage();p.setDefaultTimeout(20000);
 const checks=[];let stop;
 const username='account'+Date.now(),password='Synthetic account only 2468',resetPassword='Synthetic reset only 8642',changedPassword='Synthetic changed only 9753';
 try {
  const invite=admin('invite','--app-origin='+fixture.origin);assert.equal(invite.shareLink,fixture.origin+'/#invite='+invite.token);
  await p.goto(invite.shareLink);await text(p,'Choose a password of 12');assert.equal(await p.evaluate(()=>location.hash),'');checks.push('CLI fragment link opens inspected creation form and is removed from history');
  if(process.env.RECORD_DEMO)stop=await require('../capture-demo.cjs').start(p,path.join(out,'account-recording'));
  await fields(p,[username,password,'mismatch']);await click(p,'Create account');await text(p,'matching confirmation');checks.push('mismatched confirmation stays on form');
  let creations=0;await p.route('**/api/auth/invitations/accept',async route=>{creations++;await wait(600);await route.continue()});
  await fields(p,[username,password,password]);const createBox=await p.getByRole('button',{name:'Create account',exact:true}).boundingBox();
  await p.mouse.click(createBox.x+createBox.width/2,createBox.y+createBox.height/2);await p.mouse.click(createBox.x+createBox.width/2,createBox.y+createBox.height/2);
  await text(p,'Account created.');assert.equal(creations,1);await p.unroute('**/api/auth/invitations/accept');checks.push('double click submits invitation once');await click(p,'Continue to sign in');await login(p,username,password);checks.push('invitation creates listener then requires explicit login');
  await click(p,'Play Morning Circuit');await wait(4000);await click(p,'Change password');await click(p,'Cancel');assert.equal(await p.locator('audio').evaluateAll(es=>es.some(e=>!e.paused)),true);checks.push('cancel password change preserves playback');
  const second=await api('/api/auth/device-sessions',{username,password});assert.equal(second.status,200);
  await click(p,'Change password');await fields(p,['wrong password',changedPassword,changedPassword]);await click(p,'Change password');await text(p,'Current password not recognised');checks.push('wrong current password retains session');
  await fields(p,[password,changedPassword,changedPassword]);await click(p,'Change password');await p.getByRole('button',{name:'Sign in',exact:true}).waitFor();assert.equal(await p.locator('audio').count(),0);assert.equal((await api('/api/tracks',null,second.body.token)).status,401);checks.push('password change stops sound and revokes other device session');
  await login(p,username,changedPassword);
  const reset=admin('reset',username,'--app-origin='+fixture.origin);await p.evaluate(h=>location.hash=h,'reset='+reset.token);await text(p,'Sign out before using');await click(p,'Cancel');await p.getByRole('button',{name:'Play Morning Circuit',exact:true}).waitFor();checks.push('signed-in link requires explicit sign-out; cancellation preserves identity');
  await p.evaluate(h=>location.hash=h,'reset='+reset.token);await click(p,'Sign out and continue');await text(p,'Choose a password of 12');await fields(p,[resetPassword,resetPassword]);await click(p,'Reset password');await text(p,'Password reset.');await click(p,'Continue to sign in');await login(p,username,resetPassword);checks.push('reset link signs out explicitly, resets password and permits fresh login');
  await click(p,'Sign out');await p.getByRole('button',{name:'Sign in',exact:true}).waitFor();
  if(stop){await stop();stop=null}
  await p.evaluate(h=>location.hash=h,'reset='+reset.token);await text(p,'invalid, expired, already used or revoked');checks.push('consumed link is unavailable');await click(p,'Cancel');
  const revoked=admin('invite');admin('revoke-link',revoked.id);await p.evaluate(h=>location.hash=h,'invite='+revoked.token);await text(p,'invalid, expired, already used or revoked');checks.push('revoked link is unavailable');await click(p,'Cancel');
  const conflict=admin('invite');await inspect(p,'invite',conflict.token);await fields(p,[username,password,password]);await click(p,'Create account');await text(p,'username or account state changed');await click(p,'Inspect link');await text(p,'Choose a password of 12');checks.push('username conflict does not consume invitation and requires reinspection');await click(p,'Cancel');
  await click(p,'Use invitation or reset link');await fields(p,['https://other.test/#invite='+conflict.token]);await click(p,'Open link');await text(p,'from this service');checks.push('pasted foreign-origin link rejected locally');await click(p,'Cancel');
  let submits=0;await p.route('**/api/auth/invitations/accept',async route=>{submits++;await api('/api/auth/invitations/accept',JSON.parse(route.request().postData()));await route.abort('failed')});
  const ambiguous=admin('invite');await inspect(p,'invite',ambiguous.token);const pending='pending'+Date.now();await fields(p,[pending,password,password]);await click(p,'Create account');await text(p,'result could not be confirmed');await wait(1000);assert.equal(submits,1);await p.unroute('**/api/auth/invitations/accept');await click(p,'Inspect link');await text(p,'invalid, expired, already used or revoked');await click(p,'Cancel');await login(p,pending,password);checks.push('lost completion response is not replayed; sign-in resolves committed outcome');
  await click(p,'Sign out');
  fs.writeFileSync(path.join(out,'accounts-result.json'),JSON.stringify({checks},null,2));console.log(JSON.stringify({checks},null,2));
 }catch(e){await p.screenshot({path:path.join(out,'accounts-failure.png')});console.error((await p.locator('body').innerText()).slice(-1800));throw e}
 finally {if(stop)await stop().catch(()=>{});await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
