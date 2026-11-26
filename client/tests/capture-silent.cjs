const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {spawnSync}=require('node:child_process');
const wait=ms=>new Promise(resolve=>setTimeout(resolve,ms));
// CN1's canvas is absent from Chrome's standard screencast on this Mac.
// Capture actual rendered screenshots on a host clock, including across navigation.
exports.start = async function(page, output){
 const directory=fs.mkdtempSync(path.join(output,'frames-')),frames=[];
 let capturing=true,captureError;
 const loop=(async()=>{while(capturing){
  const name='frame-'+String(frames.length).padStart(5,'0')+'.jpg',before=Date.now();
  try{await page.screenshot({path:path.join(directory,name),type:'jpeg',quality:85});frames.push({name,time:(before+Date.now())/2000})}
  catch(error){captureError=error;capturing=false}
  await wait(100);
 }})();
 return async function stop(filename='demo.mp4'){
  capturing=false;await loop;if(captureError)throw captureError;assert.ok(frames.length>1);
  const end=Date.now()/1000;let concat='';
  for(let i=0;i<frames.length;i++)concat+=`file '${frames[i].name}'\nduration ${Math.max(.001,(frames[i+1]?.time||end)-frames[i].time)}\n`;
  concat+=`file '${frames.at(-1).name}'\n`;fs.writeFileSync(path.join(directory,'frames.txt'),concat);
  const result=spawnSync('ffmpeg',['-y','-v','error','-f','concat','-safe','0','-i',path.join(directory,'frames.txt'),'-vf','scale=390:844:force_original_aspect_ratio=decrease:in_range=pc:out_range=tv,pad=390:844:(ow-iw)/2:(oh-ih)/2','-r','30','-c:v','libx264','-color_range','tv','-pix_fmt','yuv420p','-movflags','+faststart',path.join(output,filename)],{encoding:'utf8'});
  assert.equal(result.status,0,result.stderr);
  fs.writeFileSync(path.join(output,'capture-timing.json'),JSON.stringify({start:frames[0].time,end,frames:frames.length,method:'actual page screenshots; intentionally silent'},null,2));
 };
}
