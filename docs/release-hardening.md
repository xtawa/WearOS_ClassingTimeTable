# Release hardening

Classing release APKs are built with R8 optimization and resource shrinking enabled for both the Mobile and Wear modules.

## Required release outputs

Each signed release build must produce:

- `Classing-Mobile-release.apk`
- `Classing-Wear-release.apk`
- APK SHA-256 checksums
- R8 `mapping.txt` for Mobile and Wear
- R8 `seeds.txt`, `usage.txt`, and `configuration.txt` when generated

The mapping files are required to de-obfuscate production crash reports. They must be treated as internal build artifacts and must not be bundled inside the APK.

## Security boundary

R8 and resource shrinking raise the cost of static analysis but do not establish client authenticity. Online access remains controlled by server-side signature enforcement. Secrets, JWT signing keys, administrator credentials, and certificate-registration tokens must never be embedded in either APK.
