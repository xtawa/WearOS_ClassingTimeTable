"""Verify the actual device bundle metadata before publishing the unsigned IPA."""
import argparse
import plistlib
import zipfile


def verify(path, version, build):
    root = "Payload/Classing.app/"
    phone_id = "com.xtawa.classingtime.apple"
    bundles = [
        ("", phone_id, "iPhoneOS"),
        ("PlugIns/ClassingWidgets.appex/", phone_id + ".widgets", "iPhoneOS"),
        ("Watch/ClassingWatch.app/", phone_id + ".watchkitapp", "WatchOS"),
        ("Watch/ClassingWatch.app/PlugIns/ClassingWatchWidgets.appex/", phone_id + ".watchkitapp.widgets", "WatchOS"),
    ]
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        for name in names:
            if name.endswith((".storekit", "embedded.mobileprovision")) or "/_CodeSignature/" in name:
                raise ValueError(f"Unexpected test/signing material: {name}")
        for relative, bundle_id, platform in bundles:
            info = plistlib.loads(archive.read(root + relative + "Info.plist"))
            expected = {"CFBundleIdentifier": bundle_id, "CFBundleShortVersionString": version, "CFBundleVersion": build}
            for key, value in expected.items():
                if info.get(key) != value:
                    raise ValueError(f"{bundle_id}: {key}={info.get(key)!r}, expected {value!r}")
            if info.get("CFBundleSupportedPlatforms") != [platform]:
                raise ValueError(f"{bundle_id}: not a {platform} device bundle")
            if relative == "Watch/ClassingWatch.app/" and info.get("WKCompanionAppBundleIdentifier") != phone_id:
                raise ValueError("Watch companion identifier does not match iPhone")
            if root + relative + info.get("CFBundleExecutable", "") not in names:
                raise ValueError(f"{bundle_id}: executable is missing")
            print(f"Verified {bundle_id}: {version} ({build}), {platform}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("ipa")
    parser.add_argument("--version", required=True)
    parser.add_argument("--build", required=True)
    args = parser.parse_args()
    verify(args.ipa, args.version, args.build)
