#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

@interface dev_normalbias_client_player_SystemAudioImpl : NSObject {
    NSArray *_commands;
    NSArray *_targets;
    UIImage *_artwork;
}
-(void)update:(NSString*)title param1:(NSString*)artist param2:(NSString*)album param3:(int)position param4:(int)duration param5:(BOOL)playing param6:(BOOL)previous param7:(BOOL)next;
-(void)artwork:(NSData*)png;
-(void)clear;
-(BOOL)isSupported;
@end
