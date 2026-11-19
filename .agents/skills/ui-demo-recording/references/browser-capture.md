# Browser recording with actual audio

The first Normal Bias demonstration used Playwright's browser video capture plus a Web Audio/MediaRecorder capture of the real audio elements. FFmpeg muxed those tracks. This is a fallback when the available recorder does not include tab/system audio. Use a supported combined screen/audio recorder when it already does the job.

## Prepare

- Read the installed recorder/browser API documentation and use the project's browser test dependencies. Discover executable/cache locations; paths from a previous cloud machine may not exist. Playwright recording needs its compatible FFmpeg executable in addition to Chromium. An existing system FFmpeg may be usable; verify rather than assuming it.
- Start a disposable API using the commands in `docs/client-poc.md`. Use synthetic music only. Generate fixtures outside source control or reuse an appropriate fixture. Confirm the API reports reasonable metadata and the browser decodes the duration.
- Open the final generated bundle in a fresh context with video capture enabled. Keep a timestamped action log. Assertions should inspect actual state: audio time advancing, paused time remaining still, the stream changing on queue navigation, search results and continuity of playback.

## Capture audio without replacing it

A capture instrument can attach `AudioContext.createMediaElementSource(element)` to each real `HTMLAudioElement`, routing the decoded output both to the normal destination and a `MediaStreamAudioDestinationNode` recorded by MediaRecorder. Keep one source node per element using a WeakSet/WeakMap. Install instrumentation before the first track plays; a narrow wrapper around the original `HTMLMediaElement.prototype.play` can attach new elements and then call the original method unchanged. Do not replace responses, fabricate progress, resolve failed play promises or disable browser playback restrictions to make a demonstration pass.

Resume the AudioContext after a real pointer gesture. Record whether the context is actually running. A capture request that never starts must time out visibly rather than hang the task.

**Preserve silence:** when nothing feeds the capture destination yet, some browser recording paths omit that time. The first attempt at the original demo lost its initial silence, shifting audio earlier than the picture. Keeping a zero-gain oscillator connected to the destination from the start maintained a continuous recording clock:

```js
const audioContext = new AudioContext();
const destination = audioContext.createMediaStreamDestination();
const clock = audioContext.createOscillator();
const silence = audioContext.createGain();
silence.gain.value = 0;
clock.connect(silence);
silence.connect(destination);
clock.start();
await audioContext.resume(); // after genuine user interaction
const recorder = new MediaRecorder(destination.stream, {
  mimeType: 'audio/webm;codecs=opus' // first check browser support
});
// Install dataavailable/stop handlers before starting, and retain the chunks.
recorder.start(200);
const audioStartEpochMs = Date.now();
```

The zero-gain source adds no audible music; the audible content must come exclusively from the real client media elements. Record the video start epoch as well. On completion, stop the recorder, await the final data/stop events, save the chunks, close the context so the video finalises, and save the video file. Close the AudioContext and stop the clock when done. Keep data transfer chunks bounded; do not print base64 media into tool/chat logs.

## Align and export

Measure the audio/video start offset; do not guess it. For a video that started before audio capture, trim that video prefix while re-encoding, then mux the recorded audio:

```sh
# Set these to the actual local paths and measured offset for this capture.
ffmpeg -ss "$audio_offset_seconds" -i "$video_file" -i "$audio_file" \
  -map 0:v:0 -map 1:a:0 \
  -c:v libx264 -preset fast -crf 21 -pix_fmt yuv420p \
  -c:a aac -b:a 128k -movflags +faststart -shortest "$output_file"
```

If the audio began first, align it appropriately instead of applying this command blindly. `-shortest` can hide a truncated recording: compare the final duration and final frames with the action log. Verify that the ending contains the final demonstrated result. Keep the capture at normal speed for playback demonstrations so timing/pitch remain honest.

## Check the export

```sh
ffprobe -v error \
  -show_entries format=duration,size:stream=codec_name,codec_type,width,height \
  -of json "$output_file"
ffmpeg -hide_banner -i "$output_file" -vn \
  -af silencedetect=noise=-45dB:d=0.8 -f null -
```

These are diagnostic commands, not sufficient proof alone. Play the saved MP4 and inspect it. Compare silence intervals against the recorded pause/resume actions. Optionally sample the saved file's decoded audio through an analyser to verify nonzero RMS during a musical section; choose a timestamp actually containing music. Check several points, including search during playback and the final paused state, for synchronisation. Report the measured facts and any limits without claiming to have heard audio when it was only measured.

The original verified artifact was about 46 seconds, 1280×900 H.264/AAC and 1.4 MB. Those are useful examples, not required dimensions, duration or a file size guarantee. Temporary scripts under `/tmp` and generated output under `client/target` are not reusable dependencies; keep enough commands and method detail in the verification record for a future task to recreate its own capture.
