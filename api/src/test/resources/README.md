# Synthetic MP3 fixture

`tone.mp3` is a generated one-second 440 Hz sine wave, not personal or copyrighted music. It carries title `Synthetic tone`, artist `Normal Bias tests`, album `Fixtures`.

Generated for this repository with:

```sh
ffmpeg -f lavfi -i 'sine=frequency=440:duration=1' -codec:a libmp3lame -b:a 64k -metadata title='Synthetic tone' -metadata artist='Normal Bias tests' -metadata album='Fixtures' tone.mp3
```

The binary is checked in so tests need only Java and Maven, not FFmpeg.
