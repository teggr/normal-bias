const root=require('node:path').resolve(__dirname,'../../..');
const fs=require('node:fs'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const {chromium}=require('playwright-core');
const fixtureDir=root+'/client/target/auth-client';
const fixture=JSON.parse(fs.readFileSync(fixtureDir+'/fixture.json'));
const wait=ms=>new Promise(r=>setTimeout(r,ms));
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

(async()=>{
const c=new crypto.X509Certificate(fs.readFileSync(fixtureDir+'/tls.crt'));const pin=crypto.createHash('sha256').update(c.publicKey.export({type:'spki',format:'der'})).digest('base64');
const browser=await chromium.launch({executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true,args:['--ignore-certificate-errors-spki-list='+pin]});
try{
const context=await browser.newContext({viewport:{width:1440,height:960},serviceWorkers:'block'}),page=await context.newPage();page.setDefaultTimeout(15000);
await page.goto(fixture.origin+'/index.html');await page.getByRole('button',{name:'Sign in',exact:true}).waitFor();
await fill(page,'Username',fixture.username);await fill(page,'Password',fixture.password);await click(page,'Sign in');
await page.getByRole('button',{name:'All songs',exact:true}).waitFor();console.log('PASS signed-in Library');await click(page,'Show search');
assert.equal(await page.getByRole('button',{name:'Reset',exact:true}).count(),0);await click(page,'Hide search');
await page.screenshot({path:root+'/client/target/recordings/wide-listening/account-ready.png'});
await click(page,'Play Morning Circuit');await page.waitForFunction(()=>Array.from(document.querySelectorAll('audio')).some(e=>!e.paused&&e.currentTime>.5));
const before=await page.locator('audio').first().evaluate(e=>({src:e.currentSrc,time:e.currentTime}));
await click(page,'Profile');assert.equal(await page.getByRole('button',{name:'All songs',exact:true}).count(),0);assert.equal(await page.getByRole('slider').count(),0);assert.equal(await page.getByRole('button',{name:'Clear queue',exact:true}).count(),0);
await click(page,'Change password');await click(page,'Cancel');await page.getByText('Username',{exact:true}).first().waitFor();
await click(page,'Back to Library');await page.getByRole('button',{name:'Albums',exact:true}).waitFor();
const after=await page.locator('audio').first().evaluate(e=>({src:e.currentSrc,time:e.currentTime,paused:e.paused}));assert.equal(before.src,after.src);assert.ok(after.time>before.time&&!after.paused);
await click(page,'Queue');await page.getByRole('button',{name:'Close queue',exact:true}).waitFor();await click(page,'Profile');await click(page,'Back to Queue');await page.getByRole('button',{name:'Close queue',exact:true}).waitFor();await click(page,'Close queue');
await click(page,'Profile');await page.setViewportSize({width:390,height:844});await wait(600);await click(page,'Back to Library');assert.equal(await page.getByRole('button',{name:'Open Now Playing',exact:true}).count(),1);
await page.setViewportSize({width:1440,height:960});await wait(600);await page.getByRole('button',{name:'Albums',exact:true}).waitFor();await click(page,'Pause');
await page.screenshot({path:root+'/client/target/recordings/wide-listening/account-restored.png'});
console.log('PASS console Queue return, full-screen Account/password Cancel/Back and resize, preserving actual synthetic playback');
}finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
