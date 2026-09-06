# Classing edition architecture

The Android project has one `market` flavor dimension:

- `cn`: the production China edition, retaining package name `com.xtawa.classingtime`;
- `global`: a preview placeholder using `com.xtawa.classingtime.global` and a distinct app label.

Mobile and Wear use the same application ID inside each flavor so the Wear Data Layer can pair matching editions. Both apps send `X-Classing-Client-Market` together with the existing platform, package, version code, and signing-certificate headers.

Production release automation currently builds and publishes only `cnRelease`. CI still assembles every release variant so the Global placeholder remains buildable. Google Play Billing is intentionally not included yet.
