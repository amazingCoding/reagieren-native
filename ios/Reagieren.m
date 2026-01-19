#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE (Reagieren, NSObject)

RCT_EXTERN_METHOD(update : (NSString *)apiKey projectId : (NSString *)
                      projectId resolve : (RCTPromiseResolveBlock)
                          resolve reject : (RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(restart)

@end
