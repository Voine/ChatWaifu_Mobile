/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#import "CubismRenderingInstanceSingleton_Metal.h"

static id theSharedManager = nil;

@implementation CubismRenderingInstanceSingleton_Metal

+ (id)sharedManager {
    if (theSharedManager == nil) {
        theSharedManager = [[self alloc] init];
    }
    return theSharedManager;
}

- (void)setMTLDevice:(id <MTLDevice>)param {
    mtlDevice = param;
}

- (id <MTLDevice>)getMTLDevice {
    return mtlDevice;
}

- (void)setMetalLayer:(CAMetalLayer*)param {
    metalLayer = param;
}

- (CAMetalLayer*)getMetalLayer {
    return metalLayer;
}

- (id)init {
    [self setMTLDevice:nil];
    [self setMetalLayer:nil];
    return self;
}

- (void)dealloc {
    [super dealloc];
}

@end
