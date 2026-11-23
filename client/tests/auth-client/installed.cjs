/* Real installed Chrome app in an isolated, disposable browser profile. */
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const {chromium}=require('playwright-core'),{spawn,spawnSync}=require('node:child_process'),https=require('node:https');
const out=path.resolve(__dirname,'../../target/auth-client'),fixture=JSON.parse(fs.readFileSync(path.join(out,'fixture.json')));
const wait=ms=>new Promise(r=>setTimeout(r,ms));
async function click(p,name){const e=p.getByRole('button',{name,exact:true}).first();await e.waitFor();const b=await e.boundingBox();await p.mouse.click(b.x+b.width/2,b.y+b.height/2);await wait(400)}
async function login(p){
 const boxes=await p.locator('div[role="textbox"][aria-readonly="false"]').evaluateAll(es=>es.map(e=>e.getBoundingClientRect().toJSON()).filter(b=>b.height>20).sort((a,b)=>a.y-b.y));
 for(const [i,value] of [fixture.username,fixture.password].entries()){const b=boxes[i];await p.mouse.click(b.x+20,b.y+b.height/2);await p.locator('input:focus').fill(value);await p.keyboard.press('Tab');await wait(150)}await click(p,'Sign in');await p.getByRole('button',{name:'Play Morning Circuit',exact:true}).waitFor();
}
(async()=>{
 const cert=new crypto.X509Certificate(fs.readFileSync(path.join(out,'tls.crt'))),pin=crypto.createHash('sha256').update(cert.publicKey.export({type:'spki',format:'der'})).digest('base64');
 const context=await chromium.launchPersistentContext(path.join(out,'installed-profile'),{executablePath:process.env.CHROMIUM_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:false,viewport:{width:1180,height:844},args:['--ignore-certificate-errors-spki-list='+pin]});
 let cdp,installed=false,replacement,log;const checks=[];
 try {
  let p=context.pages()[0];p.setDefaultTimeout(25000);await p.goto(fixture.origin+'/');await p.getByRole('button',{name:'Sign in',exact:true}).waitFor();
  await p.evaluate(()=>navigator.serviceWorker.ready);await p.reload();await p.getByRole('button',{name:'Sign in',exact:true}).waitFor();
  cdp=await context.newCDPSession(p);
  const manifestId=fixture.origin+'/index.html';
  await cdp.send('PWA.install',{manifestId,installUrl:fixture.origin+'/index.html'});installed=true;
  // CDP install does not simulate the dialog's window preference. Set it explicitly.
  await cdp.send('PWA.changeAppUserSettings',{manifestId,displayMode:'standalone'});checks.push('Chrome installed actual PWA into isolated profile');
  async function launch(){const before=new Set(context.pages());await cdp.send('PWA.launch',{manifestId});for(let i=0;i<100;i++){const page=context.pages().find(p=>!before.has(p));if(page){await page.waitForLoadState('domcontentloaded');page.setDefaultTimeout(25000);return page}await wait(100)}throw Error('Installed PWA did not launch')}
  p=await launch();await p.getByRole('button',{name:'Sign in',exact:true}).waitFor();assert.equal(await p.evaluate(()=>matchMedia('(display-mode: standalone)').matches),true);checks.push('launch uses standalone installed app window');
  await login(p);await p.close();p=await launch();await p.getByRole('button',{name:'Play Morning Circuit',exact:true}).waitFor();checks.push('close/reopen restores persistent session');
  if(process.env.RESTART_FIXTURE==='1'){
   const pid=Number(fs.readFileSync(path.join(out,'api.pid'),'utf8'));
   const config=path.join(out,'server.properties'),command=spawnSync('ps',['-p',String(pid),'-o','command='],{encoding:'utf8'}).stdout;
   assert.ok(command.includes('--spring.config.additional-location=file:'+config),'Refuse to stop any unrelated process');
   process.kill(pid,'SIGTERM');
   for(let i=0;i<100;i++){try{process.kill(pid,0);await wait(100)}catch{break}}
   log=fs.openSync(path.join(out,'installed-restart.log'),'a');
   replacement=spawn('java',['-jar',path.resolve(__dirname,'../../../api/target/normal-bias-api-0.1.0-SNAPSHOT.jar'),'--spring.config.additional-location=file:'+config],{stdio:['ignore',log,log]});
   fs.writeFileSync(path.join(out,'api.pid'),String(replacement.pid));
   async function ready(){return new Promise(resolve=>{const r=https.get(fixture.origin+'/api',{ca:fs.readFileSync(path.join(out,'tls.crt'))},response=>{response.resume();resolve(response.statusCode===200)});r.on('error',()=>resolve(false));r.setTimeout(1000,()=>{r.destroy();resolve(false)})})}
   let healthy=false;for(let i=0;i<150;i++){if(await ready()){healthy=true;break}await wait(100)}assert.ok(healthy,'Restarted fixture became healthy');
   await p.reload();await p.getByRole('button',{name:'Play Morning Circuit',exact:true}).waitFor();checks.push('real API restart restores installed app session from H2');
  }
  await context.setOffline(true);await p.reload();await p.getByRole('button',{name:'Retry connection',exact:true}).waitFor();await context.setOffline(false);await click(p,'Retry connection');await p.getByRole('button',{name:'Play Morning Circuit',exact:true}).waitFor();checks.push('offline cached shell and retry preserve session');
  const urls=await p.evaluate(async()=>{const result=[];for(const name of await caches.keys())for(const req of await(await caches.open(name)).keys())result.push(new URL(req.url).pathname);return result});assert.ok(!urls.some(url=>url.startsWith('/api')));assert.ok(urls.includes('/account-link.js'));checks.push('cache contains fragment handler and no private API/audio responses');
  await click(p,'Sign out');await p.getByRole('button',{name:'Sign in',exact:true}).waitFor();await p.close();p=await launch();await p.getByRole('button',{name:'Sign in',exact:true}).waitFor();checks.push('logout survives installed app close/reopen');
  await p.screenshot({path:path.join(out,'installed-standalone.png')});
  fs.writeFileSync(path.join(out,'installed-result.json'),JSON.stringify({checks},null,2));console.log(JSON.stringify({checks},null,2));
 }finally {if(installed)await cdp.send('PWA.uninstall',{manifestId:fixture.origin+'/index.html'}).catch(()=>{});await context.close();if(replacement&&replacement.exitCode===null){replacement.kill('SIGTERM');await new Promise(r=>replacement.once('exit',r))}if(log!==undefined)fs.closeSync(log)}
})().catch(e=>{console.error(e);process.exitCode=1});
