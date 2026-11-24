/* Optional recording of the real generated client. No replacement audio or simulated UI. */
const fs = require('node:fs');
const path = require('node:path');
const {spawnSync} = require('node:child_process');

exports.start = async function(page, directory) {
    fs.mkdirSync(directory, {recursive:true});
    const {width, height} = page.viewportSize();
    await page.evaluate(async () => {
        const context = new AudioContext();
        const destination = context.createMediaStreamDestination();
        const clock = context.createOscillator(), silent = context.createGain();
        silent.gain.value = 0;
        clock.connect(silent).connect(destination);
        clock.start();
        const connected = new WeakSet();
        const original = HTMLMediaElement.prototype.play;
        HTMLMediaElement.prototype.play = function(...args) {
            if (!connected.has(this)) {
                const source = context.createMediaElementSource(this);
                source.connect(destination);
                source.connect(context.destination);
                connected.add(this);
            }
            context.resume();
            return original.apply(this, args);
        };
        await context.resume();
        if (context.state !== 'running') throw new Error('Capture AudioContext suspended');
        const recorder = new MediaRecorder(destination.stream, {mimeType:'audio/webm;codecs=opus'});
        const chunks = [];
        recorder.ondataavailable = event => { if (event.data.size) chunks.push(event.data); };
        const stopped = new Promise(resolve => recorder.onstop = resolve);
        recorder.start(200);
        window.demoCapture = {recorder, chunks, stopped, context, clock, original,
            audioEpoch:performance.timeOrigin + performance.now()};
    });
    const frames = [];
    let capturing = true;
    const captureLoop = (async () => {
        while (capturing) {
            const name = `frame-${String(frames.length).padStart(5,'0')}.jpg`;
            const before = Date.now();
            await page.screenshot({path:path.join(directory,name),type:'jpeg',quality:85});
            // Midpoint of screenshot acquisition, on the same epoch clock as browser audio.
            frames.push({name,time:(before + Date.now()) / 2000});
            await new Promise(resolve => setTimeout(resolve, 80));
        }
    })();
    return async function stop() {
        capturing = false;
        await captureLoop;
        const end = Date.now() / 1000;
        const captured = await page.evaluate(async () => {
            const c = window.demoCapture;
            c.recorder.stop();
            await c.stopped;
            const bytes = new Uint8Array(await new Blob(c.chunks).arrayBuffer());
            let binary = '';
            for (let i = 0; i < bytes.length; i += 16384) binary += String.fromCharCode(...bytes.subarray(i, i+16384));
            c.clock.stop();
            await c.context.close();
            HTMLMediaElement.prototype.play = c.original;
            return {audio:btoa(binary),audioEpoch:c.audioEpoch};
        });
        if (frames.length < 2) throw new Error('No video frames captured');
        fs.writeFileSync(path.join(directory,'audio.webm'),Buffer.from(captured.audio,'base64'));
        let concat = '';
        frames.forEach((frame,i) => {
            concat += `file '${frame.name}'\nduration ${Math.max(0.001,(frames[i+1]?.time || end)-frame.time)}\n`;
        });
        concat += `file '${frames.at(-1).name}'\n`;
        fs.writeFileSync(path.join(directory,'frames.txt'),concat);
        const offset = captured.audioEpoch / 1000 - frames[0].time;
        if (Math.abs(offset) > 5) throw new Error('Unexpected capture clocks: ' + offset);
        fs.writeFileSync(path.join(directory,'timing.json'),JSON.stringify({offset,frames:frames.length,start:frames[0].time,end}));
        const result = spawnSync('ffmpeg',['-hide_banner','-loglevel','error','-f','concat','-safe','0',
            '-i',path.join(directory,'frames.txt'),'-itsoffset',String(offset),'-i',path.join(directory,'audio.webm'),
            '-vf',`scale=${width}:${height}:force_original_aspect_ratio=decrease,pad=${width}:${height}:(ow-iw)/2:(oh-ih)/2`,
            '-r','30','-c:v','libx264','-preset','fast','-crf','22','-pix_fmt','yuv420p',
            '-c:a','aac','-b:a','128k','-movflags','+faststart','-shortest','-y',path.join(directory,'demo.mp4')],{encoding:'utf8'});
        if (result.status !== 0) throw new Error(result.stderr);
        console.log('Recorded actual client audio/video:',path.join(directory,'demo.mp4'));
    };
};
