const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const DEV_UID = "dev_789";
const DEV_EMAIL = "marcioafonsorey@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("cloudSync").doc("current").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("inbox").get());
  await assertFails(unauthDb.collection("usernameLookup").doc("abc123").get());
  await assertFails(unauthDb.collection("broadcasts").where("isBroadcast", "==", true).get());
  await assertFails(unauthDb.collection("bugReports").doc("rep1").set({
    id: "rep1",
    reporterUid: ALICE_UID,
    title: "Crash",
    description: "Unauth test",
    createdAtMillis: Date.now(),
    status: "OPEN",
  }));
});

test("Authenticated user: owner can create, update, and read profile, but cannot self-elevate isDev/isBanned", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set({
    userId: ALICE_UID,
    displayName: "Alice",
    username: "alice",
    email: "alice@example.com",
    isPrivate: false,
    isDev: false,
    isBanned: false,
    totalFocusMinutes: 10,
    completedTasksCount: 1,
    completedHabitsCount: 2,
    achievementsCount: 0,
  }));

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());

  // Self-promotion attempt on update must fail
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).update({ isDev: true }));
  // Self-promotion on create must fail
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(BOB_UID).set({
    displayName: "Bob",
    username: "bob",
    isDev: true,
  }));
});

test("Authenticated user: cross-user isolation on users, cloudSync, inbox, and sent", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set({
      displayName: "Alice",
      username: "alice",
    });
    await db.collection("users").doc(ALICE_UID).collection("cloudSync").doc("current").set({
      userId: ALICE_UID,
      backupJson: "{\"tasks\":[]}",
      syncedAt: Date.now(),
    });
    await db.collection("users").doc(ALICE_UID).collection("sent").doc("msg1").set({
      id: "msg1",
      senderUid: ALICE_UID,
      senderDisplayName: "Alice",
      senderUsername: "alice",
      recipientUid: BOB_UID,
      recipientDisplayName: "Bob",
      recipientUsername: "bob",
      isOfficialDev: false,
      title: "Secret Note",
      contentMarkdown: "Private",
      category: "Geral",
      sentAtMillis: Date.now(),
      isRead: true,
      isDownloaded: false,
      isBroadcast: false,
      isBugReport: false,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("cloudSync").doc("current").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("cloudSync").doc("current").set({
    userId: ALICE_UID,
    backupJson: "hacked",
  }));
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("inbox").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("sent").get());
});

test("Authenticated user: owner can read/write/delete cloudSync current & finances", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const syncRef = aliceDb.collection("users").doc(ALICE_UID).collection("cloudSync").doc("current");
  await assertSucceeds(syncRef.set({
    userId: ALICE_UID,
    backupJson: "{\"tasks\":[]}",
    notesJson: "{\"notes\":[]}",
    financesJson: "{\"entries\":[]}",
    settings: { themeAccent: "ORANGE" },
    syncedAt: Date.now(),
  }));
  await assertSucceeds(syncRef.get());
  await assertSucceeds(syncRef.delete());
});

test("Recipient lookup tables: allow single-doc get, deny list enumeration, enforce uid ownership", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("usernameLookup").doc("hash_alice").set({
    uid: ALICE_UID,
    displayName: "Alice",
    username: "alice",
  }));

  // Single doc lookup allowed for signed-in Bob
  await assertSucceeds(bobDb.collection("usernameLookup").doc("hash_alice").get());
  // Listing all lookup docs must be denied
  await assertFails(bobDb.collection("usernameLookup").get());
  // Bob cannot overwrite Alice's lookup entry
  await assertFails(bobDb.collection("usernameLookup").doc("hash_alice").set({
    uid: BOB_UID,
    displayName: "Bob",
    username: "bob",
  }));
});

test("Inbox direct message: sender create & recipient limited field update", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const msg = {
    id: "m1",
    senderUid: ALICE_UID,
    senderDisplayName: "Alice",
    senderUsername: "alice",
    recipientUid: BOB_UID,
    recipientDisplayName: "Bob",
    recipientUsername: "bob",
    isOfficialDev: false,
    title: "Study Notes",
    contentMarkdown: "Chapter 1 summary",
    category: "Estudos",
    sentAtMillis: Date.now(),
    isRead: false,
    isDownloaded: false,
    isBroadcast: false,
    isBugReport: false,
  };

  await assertFails(bobDb.collection("users").doc(BOB_UID).collection("inbox").doc("m1").set(msg));

  // Alice sends to Bob's inbox
  await assertSucceeds(aliceDb.collection("users").doc(BOB_UID).collection("inbox").doc("m1").set(msg));

  // Bob can mark as read / downloaded
  await assertSucceeds(bobDb.collection("users").doc(BOB_UID).collection("inbox").doc("m1").update({
    isRead: true,
    isDownloaded: true,
  }));

  // Bob cannot alter message title/content
  await assertFails(bobDb.collection("users").doc(BOB_UID).collection("inbox").doc("m1").update({
    title: "Tampered",
  }));
});

test("Broadcasts & BugReports: verified developer vs normal user vs unverified email spoof", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const spoofDevDb = testEnv.authenticatedContext("spoof_uid", { email: DEV_EMAIL, email_verified: false }).firestore();
  const realDevDb = testEnv.authenticatedContext(DEV_UID, { email: DEV_EMAIL, email_verified: true }).firestore();

  const broadcast = {
    id: "b1",
    senderUid: DEV_UID,
    senderDisplayName: "Equipe Citra",
    senderUsername: "citra_devs",
    recipientUid: "",
    recipientDisplayName: "Todos",
    recipientUsername: "citra_oficial",
    isOfficialDev: true,
    title: "Novidades Citra",
    contentMarkdown: "Nova atualização disponível!",
    category: "Oficial",
    sentAtMillis: Date.now(),
    isRead: false,
    isDownloaded: false,
    isBroadcast: true,
    isBugReport: false,
  };

  // Normal user and unverified email cannot create broadcasts
  await assertFails(aliceDb.collection("broadcasts").doc("b1").set(broadcast));
  await assertFails(spoofDevDb.collection("broadcasts").doc("b1").set(broadcast));

  // Verified developer creates broadcast; signed-in user can query with isBroadcast == true
  await assertSucceeds(realDevDb.collection("broadcasts").doc("b1").set(broadcast));
  await assertSucceeds(aliceDb.collection("broadcasts").where("isBroadcast", "==", true).get());

  // Alice creates a bug report, cannot list bugReports; verified developer can list
  await assertSucceeds(aliceDb.collection("bugReports").doc("bug1").set({
    id: "bug1",
    reporterUid: ALICE_UID,
    reporterDisplayName: "Alice",
    reporterUsername: "alice",
    title: "Sync issue",
    description: "Details here",
    createdAtMillis: Date.now(),
    status: "OPEN",
  }));
  await assertFails(aliceDb.collection("bugReports").get());
  await assertFails(spoofDevDb.collection("bugReports").get());
  await assertSucceeds(realDevDb.collection("bugReports").get());
});
