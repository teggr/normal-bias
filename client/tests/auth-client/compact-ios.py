#!/usr/bin/env python3
"""Compact shell acceptance on the documented synthetic iPhone simulator; no physical device."""
import json, time
from ios import probe, until, login, sim, SIM, out, fixture
checks=[]
def tap(name):
    probe('tap',name=name);time.sleep(.4)
def shot(name):
    sim('io',SIM,'screenshot',str(out/(name+'.png')))
s=until(lambda s:s['library'] or 'Sign in' in s['labels'],'app ready')
if not s['library']:
    login();until(lambda s:s['library'] and 'Morning Circuit' in s['labels'],'login')
if 'All songs' not in probe()['labels']: tap('Library')
tap('Profile');until(lambda s:'Account' in s['labels'],'Account from Library');assert 'Username' in probe()['labels'] and fixture['username'] in probe()['labels'] and 'Close' not in probe()['labels'];tap('Back to Library');until(lambda s:'All songs' in s['labels'],'Account Back to Library');checks.append('native full-screen Account labels the username and returns to Library')
tap('Show search');probe('text',name='Library search',value='Morning');tap('Search');until(lambda s:'1–1 of 1' in s['labels'],'filtered library');shot('compact-native-search');tap('Hide search');until(lambda s:'1–1 of 1' in s['labels'],'hidden search retains filter');tap('Show search');tap('Reset');tap('Hide search');until(lambda s:'1–2 of 2' in s['labels'],'reset library');checks.append('native category-row search expands/collapses and retains filter')
tap('Now Playing');until(lambda s:'Now Playing' in s['labels'] and 'All songs' not in s['labels'],'empty player')
tap('Queue')
if probe().get('queue',0): tap('Clear queue')
until(lambda s:'0 tracks' in s['labels'],'empty Queue')
tap('Back to Now Playing');until(lambda s:'Now Playing' in s['labels'] and '0 tracks' not in s['labels'],'Queue Back')
checks.append('native empty player, nested Queue and Back')
tap('Library');tap('Evening Signal');tap('Morning Circuit');until(lambda s:s.get('playing') and s.get('queue')==2 and s.get('time',0)>1000,'playback')
shot('compact-native-library')
tap('Now Playing');until(lambda s:'Now Playing' in s['labels'] and 'All songs' not in s['labels'],'player');shot('compact-native-player')
tap('Profile');until(lambda s:'Sign out' in s['labels'],'profile');shot('compact-native-profile');assert 'Account' in probe()['labels'] and 'Username' in probe()['labels'] and fixture['username'] in probe()['labels'] and 'Close' not in probe()['labels'];tap('Back to Now Playing')
until(lambda s:s['library'] and s.get('playing') and 'Now Playing' in s['labels'],'Account Back')
checks.append('native Account Back preserves player and playback')
tap('Profile');tap('Change password');until(lambda s:'Current password' in s['labels'] or 'Change password' in s['labels'],'password form');tap('Cancel')
until(lambda s:s['library'] and s.get('playing') and 'Account' in s['labels'],'password cancellation to Account');tap('Back to Now Playing');until(lambda s:'Now Playing' in s['labels'],'Account origin retained')
checks.append('native password cancellation preserves destination and playback')
tap('Queue');until(lambda s:'2 tracks' in s['labels'],'Queue');shot('compact-native-queue');before=probe()['time'];time.sleep(1);assert probe()['time']>before
checks.append('native Queue rows/count and continuing playback')
before=probe();tap('Shuffle queue');after=probe();assert after['currentTrack']==before['currentTrack'] and after['playing'] and after['time']>=before['time'];assert sorted(after['queueIds'])==sorted(before['queueIds']);shot('compact-native-shuffle-queue');checks.append('native Queue toolbar shuffle preserves current track, queue membership and live playback')
tap('Back to Now Playing');probe('pause');until(lambda s:not s.get('playing') and not s.get('loading'),'pause')
probe('seek',value='30000');until(lambda s:abs(s.get('time',0)-30000)<1200 and not s.get('playing'),'paused seek')
current=probe()['currentTrack'];tap('Restart track');until(lambda s:s['time']<500 and not s['playing'],'paused Restart');assert probe()['currentTrack']==current;checks.append('native Restart seeks to zero and retains paused intent/current track')
probe('resume');until(lambda s:s['playing'] and s['time']>1000,'playing before Restart');probe('seek',value='30000');tap('Restart track');until(lambda s:s['playing'] and s['time']<1800,'playing Restart');assert probe()['currentTrack']==current;shot('compact-native-restart');checks.append('native Restart seeks to zero and continues playing the same track')
probe('pause');probe('seek',value='30000');
before=probe()['time'];tap('Shuffle queue');assert not probe()['playing'] and abs(probe()['time']-before)<500;shot('compact-native-shuffle-player');tap('Library');tap('Open Now Playing');state=probe();assert not state['playing'] and abs(state['time']-before)<500
checks.append('native mini-player navigation preserves paused position/intent')
tap('Player information');until(lambda s:'Track information' in s['labels'] and 'Title' in s['labels'],'inline information');assert 'Close' not in probe()['labels'];shot('compact-native-information');tap('Show artwork');until(lambda s:'Track information' not in s['labels'],'artwork restored')
checks.append('native wheel information swaps artwork inline without a popup')
tap('Queue');tap('Clear queue');until(lambda s:'0 tracks' in s['labels'] and s.get('queue')==0 and not s.get('playing'),'cleared Queue')
tap('Back to Now Playing');tap('Library');until(lambda s:'All songs' in s['labels'],'Library return')
checks.append('native clear releases playback and retains empty Queue; Library restored')
(out/'compact-ios-result.json').write_text(json.dumps({'checks':checks,'device':'iPhone 17 Pro simulator; iOS 26.0'},indent=2));print(json.dumps({'checks':checks},indent=2))
