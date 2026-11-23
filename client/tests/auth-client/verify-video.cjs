/* Plays the final saved artifact and measures its decoded audio; no API/mock app. */
const {chromium}=require('playwright-core'),http=require('node:http'),fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const movie=path.resolve(process.argv[2]);
const times=process.argv[3]?JSON.parse(process.argv[3]):{first:3,paused:8,renewed:15,ending:21};
const server=http.createServer((req,res)=>{
 if(req.url==='/'){res.setHeader('Content-Type','text/html');res.end('<video controls src="/movie.mp4"></video>');return}
 if(req.url!='/movie.mp4'){res.writeHead(404).end();return}
 const size=fs.statSync(movie).size,m=/^bytes=(\d+)-(\d*)$/.exec(req.headers.range||''),start=m?Number(m[1]):0,end=m&&m[2]?Math.min(Number(m[2]),size-1):size-1;
 res.writeHead(m?206:200,{'Content-Type':'video/mp4','Accept-Ranges':'bytes','Content-Length':end-start+1,...(m?{'Content-Range':`bytes ${start}-${end}/${size}`}:{})});fs.createReadStream(movie,{start,end}).pipe(res);
});
(async()=>{
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));let browser;
 try{
  browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true,args:['--autoplay-policy=no-user-gesture-required']});
  const p=await browser.newPage();await p.goto('http://127.0.0.1:'+server.address().port);await p.waitForFunction(()=>document.querySelector('video').readyState>=2);
  await p.evaluate(()=>{const v=document.querySelector('video');window.ctx=new AudioContext();window.analyser=ctx.createAnalyser();const source=ctx.createMediaElementSource(v),mute=ctx.createGain();mute.gain.value=0;source.connect(analyser);analyser.connect(mute).connect(ctx.destination)});
  async function sample(seconds){await p.evaluate(async t=>{const v=document.querySelector('video');v.currentTime=t;await ctx.resume();await v.play()},seconds);await p.waitForTimeout(500);return p.evaluate(()=>{const v=document.querySelector('video'),a=new Float32Array(analyser.fftSize);analyser.getFloatTimeDomainData(a);return{time:v.currentTime,rms:Math.sqrt(a.reduce((sum,x)=>sum+x*x,0)/a.length),error:v.error?.code||null}})}
  const first=await sample(times.first),paused=await sample(times.paused),renewed=await sample(times.renewed),ending=await sample(times.ending);
  assert.ok(first.rms>.001&&renewed.rms>.001);assert.ok(paused.rms<.0001&&ending.rms<.0001);assert.ok(first.time>times.first&&renewed.time>times.renewed&&!first.error&&!renewed.error);
  await p.evaluate(()=>{const v=document.querySelector('video');v.currentTime=v.duration-.3;v.play()});await p.waitForFunction(()=>document.querySelector('video').ended);
  const result={first,paused,renewed,ending,ended:true};fs.writeFileSync(path.join(path.dirname(movie),'saved-playback.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result,null,2));
 }finally{if(browser)await browser.close();server.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
