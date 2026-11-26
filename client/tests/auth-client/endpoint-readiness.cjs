/* Real generated sign-in form: bounded preflight, stale results, credential gate and Tab. */
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const {chromium}=require('playwright-core');
const root=path.resolve(__dirname,'../../..'),out=path.join(root,'client/target/auth-client');
const fixture=JSON.parse(fs.readFileSync(path.join(out,'fixture.json')));
const output=path.join(root,'client/target/recordings/endpoint-readiness');
const wait=ms=>new Promise(r=>setTimeout(r,ms));
let browser,page,stop;
async function click(name){const item=page.getByRole('button',{name,exact:true}).first();await item.waitFor();const b=await item.boundingBox();assert.ok(b,name);await page.mouse.click(b.x+b.width/2,b.y+b.height/2);await wait(350)}
async function disabled(name,expected=true){const el=page.getByRole('textbox',{name,exact:true}).first();await el.waitFor();assert.equal((await el.getAttribute('aria-disabled'))==='true',expected,name)}
async function badge(name){await page.locator('[aria-label="'+name+'"]').first().waitFor()}
async function focus(name){
 await page.keyboard.press('Tab');await wait(250);
 for(let attempt=0;attempt<4;attempt++){
  const mirror=page.getByRole('textbox',{name,exact:true}).first();let b=await mirror.boundingBox();
  if(b.y<0||b.y+b.height>page.viewportSize().height){await page.mouse.move(200,200);await page.mouse.wheel(0,b.y<0?-500:350);await wait(350);b=await mirror.boundingBox()}
  await page.mouse.click(b.x+20,b.y+b.height/2);await wait(350);
  const editor=page.locator(name==='Password'?'input[type=password]:focus':'input[name="'+name+'"]:focus');
  if(await editor.count())return editor;
 }
 throw Error('Cannot focus native editor '+name);
}

(async()=>{
 fs.mkdirSync(output,{recursive:true});const cert=new crypto.X509Certificate(fs.readFileSync(path.join(out,'tls.crt')));
 const pin=crypto.createHash('sha256').update(cert.publicKey.export({type:'spki',format:'der'})).digest('base64');
 browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true,args:['--ignore-certificate-errors-spki-list='+pin]});
 const context=await browser.newContext({viewport:{width:390,height:844},serviceWorkers:'block'});
 page=await context.newPage();page.setDefaultTimeout(15000);const checks=[];const check=x=>{checks.push(x);console.log('PASS',x)};
 const posts=[];page.on('request',r=>{if(r.method()==='POST')posts.push(r.url())});
 await page.route('**/api',r=>r.abort('failed'));await page.goto(fixture.origin);
 await badge('Service unavailable');await disabled('Username');await disabled('Password');
 assert.equal(await page.getByRole('button',{name:'Sign in',exact:true}).getAttribute('aria-disabled'),'true');
 await page.screenshot({path:path.join(output,'unavailable.png')});check('cross and actionable network error keep credentials disabled');
 if(process.env.RECORD_DEMO)stop=await require('../capture-silent.cjs').start(page,output);
 await wait(1100);
 await page.unroute('**/api');let held=0;
 await page.route('**/api',async r=>{held++;await wait(8000);await r.continue().catch(()=>{})});
 const started=Date.now();await click('Retry connection');await badge('Checking service');await disabled('Username');
 await badge('Service unavailable');assert.ok(Date.now()-started<7800,'six-second deadline');
 await page.getByText('Connection timed out.',{exact:false}).first().waitFor();check('hung discovery ends with a cross before eight seconds');
 await wait(2300);await badge('Service unavailable');await disabled('Username');check('late success cannot unlock credentials');
 await page.unroute('**/api');await click('Retry connection');await badge('Service reachable');await disabled('Username',false);await disabled('Password',false);
 check('Retry validates endpoint and enables credentials with a tick');await page.screenshot({path:path.join(output,'ready.png')});await wait(1200);
 const username=await focus('Username');await username.waitFor();await username.fill('tab-demo');await page.keyboard.press('Tab');
 await page.locator('input[type=password]:focus').waitFor();
 await page.keyboard.press('Shift+Tab');await page.locator('input[name="Username"]:focus').waitFor();
 check('Tab reaches Password and Shift+Tab returns to Username');
 await page.keyboard.press('Tab');await page.keyboard.type('masked-demo',{delay:30});await page.keyboard.press('Tab');
 await page.waitForFunction(()=>document.activeElement.getAttribute('aria-label')==='Sign in',{},{timeout:3000});
 check('Tab from Password focuses Sign in');await wait(1200);await page.screenshot({path:path.join(output,'keyboard-sign-in.png')});
 if(stop){await stop();stop=null}
 // Address edits invalidate a previous tick immediately, before debounce/network completion.
 const address=await focus('Service address');await address.waitFor();await address.fill('https://user:secret@invalid.example/api');
 await disabled('Username');await disabled('Password');await page.keyboard.press('Tab');await badge('Service unavailable');
 await page.getByText('Enter an HTTPS service address,',{exact:false}).first().waitFor();check('address edits revoke readiness; invalid input stays local');
 assert.equal(posts.length,0,'no credentials submitted before a verified endpoint');
 const edited=await focus('Service address');await edited.fill(fixture.origin);await page.keyboard.press('Tab');await badge('Service reachable');
 const user=await focus('Username');await user.fill(fixture.username);await page.keyboard.press('Tab');await page.locator('input[type=password]:focus').fill(fixture.password);await page.keyboard.press('Tab');
 await click('Sign in');await page.getByRole('button',{name:'Play Morning Circuit',exact:true}).waitFor();check('verified endpoint signs in via the advertised action');
 fs.writeFileSync(path.join(output,'result.json'),JSON.stringify({checks,held,serviceWorkers:'blocked'},null,2));
})().catch(async error=>{console.error(error);process.exitCode=1;if(page){await page.screenshot({path:path.join(output,'failure.png')});console.error((await page.locator('body').innerText()).slice(-1800));console.error(await page.locator('input:focus').evaluateAll(xs=>xs.map(x=>x.outerHTML)))}}).finally(async()=>{if(stop)await stop('failed-take.mp4').catch(()=>{});if(browser)await browser.close()});
