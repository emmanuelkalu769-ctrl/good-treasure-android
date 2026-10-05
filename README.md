# Good Treasure native Android 3.0.0

Sign-in is the opening screen for signed-out users. Android Credential Manager and Google's native Sign in with Google option return an ID token, exchanged with Supabase using a SHA-256 nonce. There is no browser OAuth fallback or WebView. Existing sessions are encrypted with Android Keystore. Google Cloud must register the package and APK SHA-1 fingerprint before native login works.

The backend validates phone access tokens with Supabase and uses the same user:<Supabase UUID> cart key as the website cookie session. Live SSE updates and polling refresh the shared bag. Stale cart versions and overlapping GET responses are discarded. Version 3 of the server connection is required; older live deployments are rejected with a clear message.

The six outfit images and native shop/forms are bundled. Package com.goodtreasure.fashion, min API 24, target 35. Login and shared data require internet. Paystack payment still uses its secure external provider screen.

Build-local.py uses the workspace portable JDK/SDK and dependencies downloaded from Google Maven and Maven Central. Run fetch-native-google.py to prepare dependencies first. Private signing material is outside this package and must remain private. The source archive contains no service, Mailgun or signing secrets.

Verified: Java/resources/D8 compilation, APK v2/v3 signature and alignment, shared identity tests, private-cart streaming tests and SQL concurrency/isolation tests. Actual phone login requires configuration and device testing. See NATIVE-APP-SETUP.md.
