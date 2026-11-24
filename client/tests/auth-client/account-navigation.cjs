/* Actual authenticated client: full-screen Account, origin-aware Back and audio continuity. */
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const {chromium}=require('playwright-core');
const out=path.resolve(__dirname,'../../target/auth-client'),recording=path.resolve(__dirname,'../../target/recordings/account-view');
const fixture=JSON.parse(fs.readFileSync(path.join(out,'fixture.json')));
const wait=ms=>new Promise(r=>setTimeout(r,ms));
let browser,page,stop;const checks=[],events=[];
async function until(fn,label){for(let i=0;i<200;i++){if(await fn())return;await wait(100)}throw Error('Timeout: '+label)}
async function click(name){const item=page.getByRole('button',{name,exact:true}).first();await item.waitFor();let b=await item.boundingBox();if(b.y+b.height>844){await page.mouse.move(200,650);await page.mouse.wheel(0,350);await wait(400);b=await item.boundingBox()}await page.mouse.click(b.x+b.width/2,b.y+b.height/2);await wait(450)}
async function audio(){return page.locator('audio').evaluateAll(xs=>xs.map(x=>({src:x.currentSrc,time:x.currentTime,paused:x.paused})));}
function event(name){events.push({name,at:Date.now()/1000})}
async function account(back){
 await click('Profile');await page.getByRole('button',{name:back,exact:true}).waitFor();
 assert.equal(await page.getByRole('dialog').count(),0,'Account is not a dialog');
 assert.ok(await page.getByText('Username',{exact:true}).count(),'Username caption');
 assert.ok(await page.getByText(fixture.username,{exact:true}).count(),'authenticated username displayed');
 assert.equal(await page.locator('[role="textbox"][aria-readonly="false"]').count(),0,'username is read-only');
 assert.equal(await page.getByRole('button',{name:'Close',exact:true}).count(),0,'no Close action');
 assert.equal(await page.getByRole('button',{name:'Open Now Playing',exact:true}).count(),0,'no mini-player on Account');
 const b=await page.getByRole('button',{name:'Sign out',exact:true}).boundingBox();assert.ok(b.x>0&&b.x+b.width<=390&&b.y<844,'Account actions fit compact view');
}
(async()=>{
 fs.mkdirSync(recording,{recursive:true});const cert=new crypto.X509Certificate(fs.readFileSync(path.join(out,'tls.crt')));
 const pin=crypto.createHash('sha256').update(cert.publicKey.export({type:'spki',format:'der'})).digest('base64');
 browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true,args:['--ignore-certificate-errors-spki-list='+pin]});
 const context=await browser.newContext({viewport:{width:390,height:844},serviceWorkers:'block'});page=await context.newPage();page.setDefaultTimeout(15000);const errors=[];let opens=0;
 page.on('pageerror',e=>errors.push(String(e)));page.on('request',r=>{if(new URL(r.url()).pathname.endsWith('/playback-grants')&&r.method()==='POST')opens++});
 await page.goto(fixture.origin);await page.locator('input[aria-label="Username"]:focus').waitFor();await page.locator('input[aria-label="Username"]:focus').fill(fixture.username);await page.keyboard.press('Tab');await wait(300);
 const boxes=await page.locator('div[role="textbox"]').evaluateAll(es=>es.map(e=>e.getBoundingClientRect().toJSON()));const b=boxes.filter(b=>b.height>20).sort((a,b)=>b.y-a.y)[0];await page.mouse.click(b.x+20,b.y+b.height/2);const password=page.locator('input[type=password]:focus');await password.waitFor();await password.fill(fixture.password);await page.keyboard.press('Tab');await click('Sign in');await page.getByRole('button',{name:'Play Morning Circuit',exact:true}).waitFor();
 if(process.env.RECORD_DEMO)stop=await require('../capture-demo.cjs').start(page,recording);
 event('library');await account('Back to Library');event('account-library');await page.screenshot({path:path.join(recording,'account-library.png')});await wait(1500);await click('Back to Library');await page.getByRole('button',{name:'All songs',exact:true}).waitFor();checks.push('full-screen Account labels the read-only username, omits Close and returns to Library');
 await click('Show search');await page.locator('input:focus,textarea:focus').waitFor();await page.locator('input:focus,textarea:focus').fill('Morning');await page.keyboard.press('Tab');await click('Search');await page.getByText('1–1 of 1',{exact:true}).first().waitFor();await click('Hide search');
 await account('Back to Library');await click('Back to Library');await page.getByText('1–1 of 1',{exact:true}).first().waitFor();assert.equal(await page.getByRole('button',{name:'Show search',exact:true}).count(),1);checks.push('Library query/results and collapsed search survive Account');
 await click('Show search');await click('Reset');await click('Hide search');await page.getByRole('button',{name:'Play Evening Signal',exact:true}).waitFor();
 const plus=await page.getByRole('button',{name:'+ Queue',exact:true}).nth(1).boundingBox();await page.mouse.click(plus.x+plus.width/2,plus.y+plus.height/2);await click('Play Morning Circuit');await until(async()=>(await audio())[0]?.time>1,'actual playback');event('playing');
 await click('Now Playing');const before=(await audio())[0],opened=opens;
 await account('Back to Now Playing');event('account-player');await wait(2000);await page.screenshot({path:path.join(recording,'account-playing.png')});await click('Back to Now Playing');await page.getByRole('slider').waitFor();const after=(await audio())[0];assert.ok(after.src===before.src&&!after.paused&&after.time>before.time);assert.equal(opens,opened);checks.push('Account from Now Playing preserves live stream, position and intent');
 await click('Queue');await click('Back to Now Playing');await account('Back to Now Playing');await click('Back to Now Playing');await click('Queue');await page.getByText('2 tracks',{exact:true}).first().waitFor();checks.push('Queue Back reaches Now Playing for Account access and returning retains Queue state');
 await click('Back to Now Playing');await click('Pause');event('paused');await wait(2000);const paused=(await audio())[0];
 await account('Back to Now Playing');await page.setViewportSize({width:1180,height:844});await wait(700);await page.getByRole('button',{name:'Back to Now Playing',exact:true}).waitFor();await page.screenshot({path:path.join(recording,'account-wide.png')});await page.setViewportSize({width:390,height:844});await until(async()=>{const b=await page.getByRole('button',{name:'Change password',exact:true}).boundingBox();return b&&b.x>=0&&b.x+b.width<=390},'compact Account layout');
 await click('Change password');await page.getByRole('button',{name:'Cancel',exact:true}).waitFor();await click('Cancel');await page.getByRole('button',{name:'Back to Now Playing',exact:true}).waitFor();assert.equal(await page.getByRole('dialog').count(),0);checks.push('password cancellation returns to Account and retains its launcher across resize');
 await click('Back to Now Playing');const restored=(await audio())[0];assert.ok(restored.src===paused.src&&restored.paused&&Math.abs(restored.time-paused.time)<.2);assert.equal(opens,opened);checks.push('Account and password cancellation preserve paused position without reopening media');
 await click('Play');await until(async()=>(await audio())[0].time>paused.time+1,'resume');event('resumed');await wait(2000);await account('Back to Now Playing');await click('Sign out');event('logged-out');await page.getByRole('button',{name:'Sign in',exact:true}).waitFor();assert.equal((await audio()).length,0);await wait(2500);checks.push('Account Sign out releases audio and returns to sign-in');
 if(stop){await stop();stop=null}assert.deepEqual(errors,[]);fs.writeFileSync(path.join(recording,'result.json'),JSON.stringify({checks,events,opens,errors},null,2));console.log(JSON.stringify({checks},null,2));
})().catch(async e=>{console.error(e);process.exitCode=1;if(page){await page.screenshot({path:path.join(recording,'failure.png')});console.error((await page.locator('body').innerText()).slice(-1200))}}).finally(async()=>{if(stop)await stop().catch(()=>{});if(browser)await browser.close()});
