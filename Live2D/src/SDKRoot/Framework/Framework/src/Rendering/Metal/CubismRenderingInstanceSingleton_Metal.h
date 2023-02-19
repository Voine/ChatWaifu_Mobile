/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#import <UIKit/UIKit.h>
#import <MetalKit/MetalKit.h>

//Framework側で保持しなければいけない値
@interface CubismRenderingInstanceSingleton_Metal : NSObject {
    id <MTLDevice> mtlDevice;
    CAMetalLayer* metalLayer;
}
+ (id)sharedManager;
- (void)setMTLDevice:(id <MTLDevice>)param;
- (id <MTLDevice>)getMTLDevice;

- (void)setMetalLayer:(CAMetalLayer*)param;
- (CAMetalLayer*)getMetalLayer;

@end
