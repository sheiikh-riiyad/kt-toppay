# Enable Google sign-in

The app now opens with a Google login screen. Firebase verifies Google credentials
before opening the wallet and persists the signed-in session. Profile > Sign out
returns to login. The wallet still uses sample balances and transactions.

## One-time Firebase setup

1. Open https://console.firebase.google.com/ and create a project.
2. Add an Android app with package name `com.toppay.org`.
3. In your VS Code PowerShell terminal, run:

   ```powershell
   .\gradlew.bat signingReport
   ```

4. Add the debug variant's SHA-1 and SHA-256 fingerprints under Firebase Project
   settings > Your apps > your Android app > SHA certificate fingerprints.
5. Open Authentication > Sign-in method, enable Google, choose a support email,
   and save.
6. Download the updated `google-services.json` from your Android app's Firebase
   settings AFTER enabling Google, and place it at `app/google-services.json`.
   The file must contain the web OAuth client used to generate `default_web_client_id`.
7. Build, install, and start from your terminal:

   ```powershell
   .\gradlew.bat installDebug
   adb shell am force-stop com.toppay.org
   adb shell am start -n com.toppay.org/.MainActivity
   ```

The Google Services plugin is applied in the app module and requires
`app/google-services.json`. The configuration matches `com.toppay.org`
and now contains Android and web OAuth clients. Whenever you download an updated
configuration, replace the app module's copy and rebuild; files in the project
root are not used by the app. No fake login or bypass is provided. Never put service
account private keys in the app.

## Verify on your phone

- Tap Sign in with Google, choose an account, and confirm the wallet opens.
- Restart the app and check that the session persists.
- Open Profile > Sign out and check that login returns.
- Cancel the Google picker and check that the wallet stays inaccessible.
- Check a failed/offline attempt shows an error and lets you retry.

For sign-in configuration errors, confirm the package, signing fingerprint,
Google provider, and updated JSON all belong to the same Firebase project.
Register the production signing certificate separately before a release.

Official guides:
- https://firebase.google.com/docs/auth/android/google-signin
- https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation
