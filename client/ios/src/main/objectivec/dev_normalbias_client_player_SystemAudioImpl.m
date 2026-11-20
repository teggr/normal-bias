#import "dev_normalbias_client_player_SystemAudioImpl.h"
#import <MediaPlayer/MediaPlayer.h>
#import "CodenameOne_GLViewController.h"
#import "dev_normalbias_client_player_SystemAudioEvents.h"

@implementation dev_normalbias_client_player_SystemAudioImpl

-(void)setup {
    if (_targets != nil) return;
    MPRemoteCommandCenter *center = [MPRemoteCommandCenter sharedCommandCenter];
    _commands = [[NSArray alloc] initWithObjects:center.playCommand, center.pauseCommand,
        center.togglePlayPauseCommand, center.nextTrackCommand, center.previousTrackCommand,
        center.changePlaybackPositionCommand, center.stopCommand, nil];
    NSMutableArray *targets = [NSMutableArray array];
    for (NSUInteger i = 0; i < [_commands count]; i++) {
        MPRemoteCommand *command = [_commands objectAtIndex:i];
        int code = (int)i + 1;
        id target = [command addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *event) {
            int position = 0;
            if (code == 6 && [event isKindOfClass:[MPChangePlaybackPositionCommandEvent class]]) {
                double milliseconds = ((MPChangePlaybackPositionCommandEvent*)event).positionTime * 1000.0;
                position = (int)MAX(0, MIN(2147483647.0, milliseconds));
            }
            dev_normalbias_client_player_SystemAudioEvents_command___int_int(CN1_THREAD_GET_STATE_PASS_ARG code, position);
            return MPRemoteCommandHandlerStatusSuccess;
        }];
        command.enabled = YES;
        [targets addObject:target];
    }
    _targets = [targets copy];
    // Skip means adjacent queue track; do not expose unimplemented time-jump commands.
    center.skipForwardCommand.enabled = NO;
    center.skipBackwardCommand.enabled = NO;
    center.seekForwardCommand.enabled = NO;
    center.seekBackwardCommand.enabled = NO;
}

-(void)addArtwork:(NSMutableDictionary*)info {
    if (_artwork == nil) return;
    UIImage *snapshot = _artwork;
    MPMediaItemArtwork *cover = [[[MPMediaItemArtwork alloc] initWithBoundsSize:snapshot.size
        requestHandler:^UIImage*(CGSize size) { return snapshot; }] autorelease];
    [info setObject:cover forKey:MPMediaItemPropertyArtwork];
}

-(void)update:(NSString*)title param1:(NSString*)artist param2:(NSString*)album param3:(int)position param4:(int)duration param5:(BOOL)playing param6:(BOOL)previous param7:(BOOL)next {
    dispatch_async(dispatch_get_main_queue(), ^{
        [self setup];
        MPRemoteCommandCenter *commands = [MPRemoteCommandCenter sharedCommandCenter];
        commands.previousTrackCommand.enabled = previous;
        commands.nextTrackCommand.enabled = next;
        commands.changePlaybackPositionCommand.enabled = duration > 0;
        NSMutableDictionary *info = [NSMutableDictionary dictionaryWithObjectsAndKeys:
            title ?: @"", MPMediaItemPropertyTitle,
            artist ?: @"", MPMediaItemPropertyArtist,
            album ?: @"", MPMediaItemPropertyAlbumTitle,
            @((double)MAX(0, position) / 1000.0), MPNowPlayingInfoPropertyElapsedPlaybackTime,
            @((double)MAX(0, duration) / 1000.0), MPMediaItemPropertyPlaybackDuration,
            @(playing ? 1.0 : 0.0), MPNowPlayingInfoPropertyPlaybackRate, nil];
        [self addArtwork:info];
        MPNowPlayingInfoCenter *center = [MPNowPlayingInfoCenter defaultCenter];
        center.nowPlayingInfo = info;
        center.playbackState = playing ? MPNowPlayingPlaybackStatePlaying : MPNowPlayingPlaybackStatePaused;
    });
}

-(void)artwork:(NSData*)png {
    dispatch_async(dispatch_get_main_queue(), ^{
        UIImage *image = png.length > 0 ? [UIImage imageWithData:png] : nil;
        [image retain];
        [_artwork release];
        _artwork = image;
        NSMutableDictionary *info = [NSMutableDictionary dictionaryWithDictionary:
            [MPNowPlayingInfoCenter defaultCenter].nowPlayingInfo ?: @{}];
        [info removeObjectForKey:MPMediaItemPropertyArtwork];
        [self addArtwork:info];
        if (info.count > 0) [MPNowPlayingInfoCenter defaultCenter].nowPlayingInfo = info;
    });
}

-(void)clear {
    dispatch_async(dispatch_get_main_queue(), ^{
        for (NSUInteger i = 0; i < [_targets count]; i++) {
            MPRemoteCommand *command = [_commands objectAtIndex:i];
            [command removeTarget:[_targets objectAtIndex:i]];
            command.enabled = NO;
        }
        [_targets release]; _targets = nil;
        [_commands release]; _commands = nil;
        [_artwork release]; _artwork = nil;
        [MPNowPlayingInfoCenter defaultCenter].nowPlayingInfo = nil;
        [MPNowPlayingInfoCenter defaultCenter].playbackState = MPNowPlayingPlaybackStateStopped;
    });
}

-(BOOL)isSupported { return YES; }
@end
