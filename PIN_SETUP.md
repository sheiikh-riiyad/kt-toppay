# Firestore PIN setup (development)

The Android app checks `users/{uid}/private/pin` after Google sign-in. If the
document is absent it asks the user to create and confirm a four-digit PIN. After
saving, it returns to PIN login. If the document exists, it asks for that PIN.

## Firebase setup

1. Select Firebase project `comtoppayorg`.
2. Create the default Cloud Firestore database.
3. Deploy the included rules:

   ```powershell
   npx.cmd firebase-tools deploy --only firestore:rules --project comtoppayorg
   ```

5. Build and run the Android app using your usual terminal commands.

This development implementation stores the four digits directly in the `pin`
field. Replace it with server-side hashing before production.

## Data layout

- `users/{uid}`: UID, name, email, email verification, photo URL, creation metadata,
  update timestamp and PIN-configured flag. Values come from Firebase Auth on the
  server, not client-supplied profile information.
- `users/{uid}/private/pin`: salted scrypt hash using a server-only HMAC pepper,
  hash version, failed-attempt count, lock expiry, creation and verification times.
  The raw PIN is never stored or logged. The app never downloads the hash.

Callables derive the UID from the validated Firebase Auth token. PIN enrollment
uses a transaction and cannot overwrite an existing PIN. Verification updates
failure counts transactionally; after five errors, entry is locked for 15 minutes.
Lockouts survive sign-out, reinstalling, and switching devices.

## Validation

```powershell
node --test functions/pin-security.test.js
```

On a device after deploying: verify new-account PIN creation, mismatched
confirmation, existing-account login, leading-zero PINs, five incorrect attempts,
retry after 15 minutes, network failure, background/relaunch lock, and sign-out.
Check the Firebase console for profile/hash records; no plain PIN should appear.

## Scope

This adds a server-verified app unlock, not payment authorization or MFA. The
wallet currently displays demo data. Future money/data endpoints must implement
their own server-side authorization and fresh PIN/session checks; never trust the
Android `unlocked` flag. No persistent backend authorization grant is issued here.
There is intentionally no unauthenticated PIN reset or overwrite path. A forgotten
PIN recovery flow requiring fresh Google authentication must be designed before
production. App Check enforcement can also be added before production deployment.

References:
- https://firebase.google.com/docs/functions/callable
- https://firebase.google.com/docs/functions/config-env
- https://firebase.google.com/docs/functions/manage-functions
