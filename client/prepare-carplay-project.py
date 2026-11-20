#!/usr/bin/env python3
"""Bounded repairs to pinned 7.0.282 local ios-source output; never edit vendor caches.

The generator emits the bridge but leaves phone-only scene dispatch and omits the
signing entitlement. Validate known source shapes before modifying generated files.
"""
import plistlib
import sys
from pathlib import Path


def prepare(project):
    source = project / "NormalBias-src"
    manifest_path = source / "NormalBias-Info.plist"
    delegate_path = source / "CodenameOne_GLAppDelegate.m"
    bridge_path = source / "CodenameOne_CarPlaySceneDelegate.m"
    pbx_path = project / "NormalBias.xcodeproj/project.pbxproj"
    manifest = plistlib.loads(manifest_path.read_bytes())
    scene = manifest["UIApplicationSceneManifest"]
    car = scene["UISceneConfigurations"]["CPTemplateApplicationSceneSessionRoleApplication"][0]
    if car["UISceneDelegateClassName"] != "CodenameOne_CarPlaySceneDelegate":
        raise ValueError("Unexpected CarPlay scene delegate; review generator output")
    delegate = delegate_path.read_text()
    bridge = bridge_path.read_text()
    pbx = pbx_path.read_text()
    marker = "// Normal Bias: route the CarPlay scene separately from the phone."
    anchor = '    UISceneConfiguration *sceneConfiguration = [UISceneConfiguration configurationWithName:@"Default Configuration" sessionRole:connectingSceneSession.role];'
    if marker not in delegate:
        if delegate.count(anchor) != 1:
            raise ValueError("Unexpected app scene dispatch; review generator output")
        delegate = delegate.replace(anchor, '''    // Normal Bias: route the CarPlay scene separately from the phone.
#ifdef CN1_USE_CARPLAY
    if ([connectingSceneSession.role isEqualToString:@"CPTemplateApplicationSceneSessionRoleApplication"]) {
        UISceneConfiguration *carConfiguration = [UISceneConfiguration configurationWithName:@"CarPlay Configuration" sessionRole:connectingSceneSession.role];
        carConfiguration.sceneClass = NSClassFromString(@"CPTemplateApplicationScene");
        carConfiguration.delegateClass = NSClassFromString(@"CodenameOne_CarPlaySceneDelegate");
        return carConfiguration;
    }
#endif
''' + anchor)
    # The pinned bridge runs without ARC. Its singleton needs to own this dictionary
    # until disconnect, rather than retaining an expired autoreleased object.
    old = "_imageTable = [NSMutableDictionary dictionary];"
    new = "_imageTable = [[NSMutableDictionary alloc] init];"
    if old in bridge:
        if bridge.count(old) != 1:
            raise ValueError("Unexpected CarPlay image table; review generator output")
        bridge = bridge.replace(old, new)
    elif new not in bridge:
        raise ValueError("Unknown CarPlay image table lifetime; review generator output")
    entitlement = '\t\t\t\tCODE_SIGN_ENTITLEMENTS = "NormalBias-src/NormalBias-CarPlay.entitlements";\n'
    info = '\t\t\t\tINFOPLIST_FILE = "NormalBias-src/NormalBias-Info.plist";'
    if "CODE_SIGN_ENTITLEMENTS" not in pbx:
        if pbx.count(info) != 2:
            raise ValueError("Unexpected app build configurations; review generator output")
        pbx = pbx.replace(info, entitlement + info)
    elif pbx.count(entitlement) != 2:
        raise ValueError("Existing signing entitlements differ; refusing to replace them")
    scene["UIApplicationSupportsMultipleScenes"] = True
    car["UISceneClassName"] = "CPTemplateApplicationScene"
    manifest_path.write_bytes(plistlib.dumps(manifest, sort_keys=False))
    delegate_path.write_text(delegate)
    bridge_path.write_text(bridge)
    pbx_path.write_text(pbx)
    (source / "NormalBias-CarPlay.entitlements").write_bytes(plistlib.dumps({
        "com.apple.developer.carplay-audio": True
    }))
    print("Prepared local CarPlay scene dispatch, scene class, retained image table and signing entitlement.")
    print("No signing or installation performed. An Apple-approved CarPlay provisioning profile is still required.")


if __name__ == "__main__":
    prepare(Path(sys.argv[1]))
