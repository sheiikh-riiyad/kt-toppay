const { initializeApp } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');
const { getFirestore, FieldValue } = require('firebase-admin/firestore');
const { onCall, HttpsError } = require('firebase-functions/v2/https');
const { defineSecret } = require('firebase-functions/params');
const { validPin, createPin, matchesPin, attemptState, MAX_ATTEMPTS } = require('./pin-security');

initializeApp();
const db = getFirestore();
const pepper = defineSecret('PIN_PEPPER');
const options = { region: 'us-central1', maxInstances: 5, concurrency: 10 };

function requireUid(request) {
  if (!request.auth) throw new HttpsError('unauthenticated', 'Sign in with Google first.');
  if (request.auth.token.firebase?.sign_in_provider !== 'google.com') {
    throw new HttpsError('permission-denied', 'Google sign-in is required.');
  }
  return request.auth.uid; // Never accept a UID supplied in the payload.
}

function requirePin(request) {
  if (!validPin(request.data?.pin)) throw new HttpsError('invalid-argument', 'Enter exactly 4 digits.');
  return request.data.pin;
}

function profileData(user) {
  return {
    uid: user.uid, displayName: user.displayName || '', email: user.email || '',
    photoUrl: user.photoURL || '', emailVerified: user.emailVerified,
    authCreatedAt: user.metadata.creationTime || null,
    updatedAt: FieldValue.serverTimestamp()
  };
}

exports.pinStatus = onCall(options, async request => {
  const uid = requireUid(request);
  const user = await getAuth().getUser(uid);
  const pin = await db.doc(`users/${uid}/private/pin`).get();
  await db.doc(`users/${uid}`).set(profileData(user), { merge: true });
  return { hasPin: pin.exists };
});

exports.createWalletPin = onCall({ ...options, secrets: [pepper] }, async request => {
  const uid = requireUid(request);
  const pin = requirePin(request);
  const user = await getAuth().getUser(uid);
  const record = await createPin(pin, uid, pepper.value());
  const ref = db.doc(`users/${uid}/private/pin`);
  await db.runTransaction(async tx => {
    if ((await tx.get(ref)).exists) throw new HttpsError('already-exists', 'A PIN is already set. Enter your existing PIN.');
    tx.create(ref, { ...record, failedAttempts: 0, lockedUntil: 0, createdAt: FieldValue.serverTimestamp() });
    tx.set(db.doc(`users/${uid}`), { ...profileData(user), pinConfigured: true }, { merge: true });
  });
  return { created: true };
});

exports.verifyWalletPin = onCall({ ...options, secrets: [pepper] }, async request => {
  const uid = requireUid(request);
  const pin = requirePin(request);
  const ref = db.doc(`users/${uid}/private/pin`);
  const outcome = await db.runTransaction(async tx => {
    const snapshot = await tx.get(ref);
    if (!snapshot.exists) throw new HttpsError('failed-precondition', 'Set up a PIN first.');
    const record = snapshot.data();
    const now = Date.now();
    if ((record.lockedUntil || 0) > now) return { verified: false, locked: true };
    const verified = await matchesPin(pin, uid, record, pepper.value());
    const state = attemptState(record, verified, now);
    tx.update(ref, { ...state, ...(verified ? { lastVerifiedAt: FieldValue.serverTimestamp() } : {}) });
    return { verified, locked: state.lockedUntil > now, attemptsLeft: MAX_ATTEMPTS - state.failedAttempts };
  });
  // Throw only AFTER committing the failed-attempt counter.
  if (outcome.locked) throw new HttpsError('resource-exhausted', 'Too many attempts. Try again in 15 minutes.');
  if (!outcome.verified) throw new HttpsError('permission-denied', `Incorrect PIN. ${outcome.attemptsLeft} attempts remaining.`);
  return { verified: true };
});
