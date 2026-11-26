/* Unified connection/sign-in acceptance against disposable HTTPS services. */
const assert=require('node:assert/strict'), fs=require('node:fs'), path=require('node:path'), crypto=require('node:crypto');
const {spawn,spawnSync}=require('node:child_process');
const {chromium}=require('playwright-core');
const root=path.resolve(__dirname,'../../..'), fixtureDir=path.join(root,'client/target/auth-client');
const fixture=JSON.parse(fs.readFileSync(path.join(fixtureDir,'fixture.json')));
const output=path.join(root,'client/target/recordings/service-connection');
const wait=ms=>new Promise(r=>setTimeout(r,ms));
const capture=page=>require('../capture-silent.cjs').start(page,output);
async function click(page,name){
 const el=page.getByRole('button',{name,exact:true}).first();await el.waitFor();
 let previous;
 for(let i=0;i<20;i++){
  const box=await el.boundingBox();assert.ok(box,name);
  if(box.y<0||box.y+box.height>page.viewportSize().height){
   await page.mouse.move(200,Math.min(350,page.viewportSize().height-80));
   await page.mouse.wheel(0,box.y<0?box.y-30:box.y+box.height-page.viewportSize().height+40);
   previous=null;await wait(650);continue;
  }
  if(previous&&Math.abs(previous.y-box.y)<1){await page.mouse.click(box.x+box.width/2,box.y+box.height/2);await wait(450);return}
  previous=box;await wait(250);
 }
 throw Error('Unstable or unreachable '+name);
}
async function fill(page,name,value){
 await page.keyboard.press('Tab');await wait(250);
 const mirror=name==='Password'?page.locator('div[role="textbox"]').last():page.getByRole('textbox',{name,exact:true}).first();await mirror.waitFor();
 for(let attempt=0;attempt<4;attempt++){
  let b=await mirror.boundingBox();
  if(b.y<0||b.y+b.height>page.viewportSize().height){await page.mouse.move(200,200);await page.mouse.wheel(0,b.y<0?-600:400);await wait(350);b=await mirror.boundingBox()}
  await page.mouse.click(b.x+Math.min(25,b.width/2),b.y+b.height/2);await wait(350);
  const editor=name==='Password'?page.locator('input[type=password]:focus'):page.locator('input:focus,textarea:focus');
  if(await editor.count()){await editor.fill(value);await page.keyboard.press('Tab');await wait(250);return}
 }
 throw Error('Could not focus '+name);
}
async function login(page){await fill(page,'Username',fixture.username);await fill(page,'Password',fixture.password);await click(page,'Sign in');await page.getByRole('button',{name:'Play Morning Circuit',exact:true}).waitFor()}
(async()=>{
 fs.mkdirSync(output,{recursive:true});
 const c=new crypto.X509Certificate(fs.readFileSync(path.join(fixtureDir,'tls.crt'))),pin=crypto.createHash('sha256').update(c.publicKey.export({type:'spki',format:'der'})).digest('base64');
 const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true,args:['--ignore-certificate-errors-spki-list='+pin]});
 const context=await browser.newContext({viewport:{width:390,height:844},serviceWorkers:'block'});
 const page=await context.newPage();page.setDefaultTimeout(20000);page.setDefaultNavigationTimeout(20000);const checks=[];const check=message=>{checks.push(message);console.log('PASS '+message)};let second,stop;
 const secondary=new URL(fixture.origin);secondary.port=String(Number(secondary.port)+1);
 const requests=[];page.on('request',r=>{if(r.method()==='POST')requests.push({url:r.url(),body:r.postData()})});
 try {
  await page.route('**/api',route=>route.abort('failed'));await page.goto(fixture.origin+'/');
  await page.getByRole('button',{name:'Retry connection',exact:true}).waitFor();
  await page.getByText('Cannot reach the service.',{exact:false}).first().waitFor();
  await page.getByRole('textbox',{name:'Service address',exact:true}).first().waitFor();
  assert.ok(await page.getByRole('textbox',{name:'Username',exact:true}).count());
  assert.ok(await page.locator('[aria-label="Password"]').count());
  check('unreachable startup retains all three fields and inline Retry');
  if(process.env.RECORD_DEMO)stop=await capture(page);
  await page.screenshot({path:path.join(output,'unreachable.png')});await wait(1200);
  await page.unroute('**/api');await click(page,'Retry connection');await page.getByText('Sign in to your music.',{exact:false}).first().waitFor();
  check('Retry discovers service without username/password');await wait(1000);
  await fill(page,'Service address','https://user:secret@music.example.com/api');await click(page,'Open service');
  await page.getByText('Enter an HTTPS service address,',{exact:false}).first().waitFor();
  assert.equal(page.url(),fixture.origin+'/');check('invalid address stays on page without sending credentials');
  await fill(page,'Service address',fixture.origin);await click(page,'Sign in');
  await page.getByText('Enter your username and password,',{exact:false}).first().waitFor();
  await fill(page,'Username',fixture.username);await fill(page,'Password','wrong synthetic password');await click(page,'Sign in');
  await page.getByText('Username or password not recognised.',{exact:false}).first().waitFor();check('invalid credentials remain in unified form');
  await login(page);check('API discovery then advertised browser login opens real catalogue');
  await page.route('**/api',route=>route.abort('failed'));await page.reload();await page.getByRole('button',{name:'Retry connection',exact:true}).waitFor();
  await page.screenshot({path:path.join(output,'remembered-offline.png')});await wait(1200);
  await page.unroute('**/api');await click(page,'Retry connection');await page.getByRole('button',{name:'Play Morning Circuit',exact:true}).waitFor();
  check('offline bootstrap and Retry preserve cookie and restore without a password');
  await click(page,'Profile');await click(page,'Sign out');await page.getByRole('textbox',{name:'Service address',exact:true}).first().waitFor();
  await page.screenshot({path:path.join(output,'sign-in.png')});await wait(1000);
  await page.keyboard.press('Tab');await wait(300);await page.setViewportSize({width:1180,height:844});await wait(500);const b=await page.getByRole('textbox',{name:'Service address',exact:true}).first().boundingBox();assert.ok(b.width<700&&b.x>200);await page.screenshot({path:path.join(output,'wide.png')});
  await page.setViewportSize({width:390,height:568});await wait(400);await fill(page,'Username',fixture.username);await fill(page,'Password','short-screen synthetic');await click(page,'Sign in');await page.getByText('Username or password not recognised.',{exact:false}).first().waitFor();check('wide form bounded; short form scrolls to fields and actions');
  await page.setViewportSize({width:390,height:844});await page.reload();await page.getByRole('button',{name:'Sign in',exact:true}).waitFor();
  await page.route('**/api',r=>r.fulfill({status:200,contentType:'application/json',body:'{"unrelated":true}'}));await page.reload();await page.getByText('This address did not return a Normal Bias service.',{exact:false}).first().waitFor();await page.unroute('**/api');check('unrelated JSON service rejected');
  let bootstraps=0;
  await page.route('**/api',async route=>{bootstraps++;await wait(700);await route.continue()});
  const retryBox=await page.getByRole('button',{name:'Retry connection',exact:true}).boundingBox();
  await page.mouse.dblclick(retryBox.x+retryBox.width/2,retryBox.y+retryBox.height/2,{delay:60});
  await page.getByText('Sign in to your music.',{exact:false}).first().waitFor();assert.equal(bootstraps,1);
  await page.unroute('**/api');check('duplicate Retry presses start only one discovery request');
  const data=fs.mkdtempSync(path.join(fixtureDir,'connection-secondary-'));
  const log=fs.openSync(path.join(output,'second-api.log'),'w');
  second=spawn('java',['-jar',path.join(root,'api/target/normal-bias-api-0.1.0-SNAPSHOT.jar'),'--spring.config.additional-location=file:'+path.join(fixtureDir,'server.properties'),'--server.port='+secondary.port,'--normalbias.data-dir='+data],{stdio:['ignore',log,log]});
  for(let i=0;i<150;i++){if(fs.readFileSync(path.join(output,'second-api.log'),'utf8').includes('Started NormalBiasApplication'))break;if(second.exitCode!==null)throw Error('Secondary service exited');await wait(100)}
  await fill(page,'Username','must-not-transfer');await fill(page,'Password','must-not-transfer');
  await fill(page,'Service address',secondary.origin);
  await page.getByRole('button',{name:'Open service',exact:true}).waitFor();
  const postCount=requests.length;
  await click(page,'Open service');await page.waitForURL(secondary.origin+'/');await page.getByRole('button',{name:'Sign in',exact:true}).waitFor();
  assert.equal(requests.length,postCount,'no login submission during endpoint navigation');
  const input=page.locator('input[aria-label="Username"]:visible').last();if(await input.count())assert.equal(await input.inputValue(),'');
  check('different endpoint opens hosted app in same tab without credentials in URL or POST');
  const storage=await page.evaluate(()=>JSON.stringify({local:Object.values(localStorage),session:Object.values(sessionStorage)}));assert.ok(!storage.includes(fixture.password)&&!storage.includes('must-not-transfer'));check('passwords never persisted');
  await page.screenshot({path:path.join(output,'second-service.png')});await wait(1000);
  if(stop){await stop();stop=null}
  fs.writeFileSync(path.join(output,'result.json'),JSON.stringify({checks,serviceWorkers:'blocked; fresh assets',recording:'silent connection-only flow'},null,2));console.log(JSON.stringify({checks},null,2));
 } catch(error){await page.screenshot({path:path.join(output,'failure.png')});console.error((await page.locator('body').innerText()).slice(-2200));throw error}
 finally {if(stop)await stop('failed-take.mp4');await context.close();await browser.close();if(second&&second.exitCode===null){await new Promise(r=>{second.once('exit',r);second.kill('SIGTERM')})}}

})().catch(e=>{console.error(e);process.exitCode=1});
