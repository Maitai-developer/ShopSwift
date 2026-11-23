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
const ADMIN_UID = "admin_789";
const ADMIN_EMAIL = "gakumicollins@gmail.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msOffset = 5000) {
  return new Date(Date.now() - msOffset);
}

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

test("Unauthenticated users are blocked from all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("products").where("active", "==", true).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("cart").get());
  await assertFails(unauthDb.collection("orders").where("userId", "==", ALICE_UID).get());
});

test("Admin (verified gakumicollins@gmail.com) can CRUD products; spoofed or regular users cannot", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const spoofAdminDb = testEnv
    .authenticatedContext("spoof_1", { email: ADMIN_EMAIL, email_verified: false })
    .firestore();
  const shopperDb = testEnv
    .authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true })
    .firestore();

  const created = pastDate(10000);
  const validProduct = {
    title: "Aero ANC Headphones",
    description: "Spatial wireless audio with 40h battery life.",
    priceCents: 24900,
    category: "Audio",
    imageKey: "headphones",
    stock: 25,
    active: true,
    createdAt: created,
    updatedAt: created,
  };

  // Spoofed unverified admin rejected
  await assertFails(spoofAdminDb.collection("products").doc("prod_1").set(validProduct));
  // Regular shopper rejected from creating products
  await assertFails(shopperDb.collection("products").doc("prod_1").set(validProduct));
  // Verified admin succeeds
  await assertSucceeds(adminDb.collection("products").doc("prod_1").set(validProduct));

  // Reject ghost @DocumentId `id` field
  await assertFails(
    adminDb.collection("products").doc("prod_2").set({
      ...validProduct,
      id: "prod_2",
    })
  );

  // Admin partial update succeeds (preserving createdAt)
  await assertSucceeds(
    adminDb.collection("products").doc("prod_1").update({
      priceCents: 22900,
      stock: 18,
      updatedAt: pastDate(1000),
    })
  );

  // Admin cannot mutate createdAt
  await assertFails(
    adminDb.collection("products").doc("prod_1").update({
      createdAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );

  // Shopper query aligned with active == true succeeds; unfiltered query fails
  await assertSucceeds(shopperDb.collection("products").where("active", "==", true).get());
  await assertFails(shopperDb.collection("products").get());

  // Admin can list all products including inactive
  await assertSucceeds(adminDb.collection("products").get());
  await assertSucceeds(adminDb.collection("products").doc("prod_1").delete());
});

test("User profiles enforce owner isolation and reject privilege escalation", async () => {
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true })
    .firestore();
  const bobDb = testEnv
    .authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true })
    .firestore();

  const created = pastDate(10000);
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Ghost field / privilege escalation rejected
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      email: "bob@example.com",
      isAdmin: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Cross-user read & listing users rejected
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(aliceDb.collection("users").get());
});

test("Shopping cart subcollection enforces ownership, product existence, and bounds", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("products").doc("prod_1").set({
      title: "Chrono S Watch",
      description: "Sapphire glass smartwatch.",
      priceCents: 31900,
      category: "Wearables",
      imageKey: "watch",
      stock: 10,
      active: true,
      createdAt: pastDate(20000),
      updatedAt: pastDate(20000),
    });
  });

  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true })
    .firestore();
  const bobDb = testEnv
    .authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true })
    .firestore();

  const created = pastDate(5000);
  // Adding existing product succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("cart").doc("prod_1").set({
      title: "Chrono S Watch",
      priceCents: 31900,
      quantity: 2,
      category: "Wearables",
      imageKey: "watch",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Adding non-existent product rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("cart").doc("nonexistent").set({
      title: "Ghost Item",
      priceCents: 1000,
      quantity: 1,
      category: "Audio",
      imageKey: "headphones",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Out-of-bounds quantity update rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("cart").doc("prod_1").update({
      quantity: 0,
      updatedAt: pastDate(1000),
    })
  );

  // Valid quantity update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("cart").doc("prod_1").update({
      quantity: 3,
      updatedAt: pastDate(1000),
    })
  );

  // Bob cannot read or delete Alice's cart
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("cart").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("cart").doc("prod_1").delete()
  );

  // Alice can list and delete her cart
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("cart").get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("cart").doc("prod_1").delete()
  );
});

test("Orders enforce buyer isolation, total math integrity, and admin-only fulfillment updates", async () => {
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true })
    .firestore();
  const bobDb = testEnv
    .authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true })
    .firestore();
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();

  const created = pastDate(8000);
  const validOrder = {
    userId: ALICE_UID,
    customerEmail: "alice@example.com",
    customerName: "Alice",
    shippingAddress: "410 Terry Ave N, Seattle, WA 98109",
    itemsSummary: "1x Chrono S Watch",
    itemCount: 1,
    subtotalCents: 31900,
    shippingCents: 0,
    totalCents: 31900,
    paymentMethod: "stripe_payment_sheet",
    paymentReference: "pi_3Qx123abc",
    status: "PAID",
    createdAt: created,
    updatedAt: created,
  };

  await assertSucceeds(aliceDb.collection("orders").doc("ord_1").set(validOrder));

  // Reject tampered totalCents
  await assertFails(
    aliceDb.collection("orders").doc("ord_bad").set({
      ...validOrder,
      totalCents: 100,
    })
  );

  // Buyer filtered query succeeds; unfiltered query fails for buyer
  await assertSucceeds(aliceDb.collection("orders").where("userId", "==", ALICE_UID).get());
  await assertFails(aliceDb.collection("orders").get());

  // Cross-user read rejected
  await assertFails(bobDb.collection("orders").doc("ord_1").get());

  // Customer cannot mutate order status
  await assertFails(
    aliceDb.collection("orders").doc("ord_1").update({
      status: "DELIVERED",
      updatedAt: pastDate(1000),
    })
  );

  // Admin can list all orders and advance status to DELIVERED, but cannot revert DELIVERED
  await assertSucceeds(adminDb.collection("orders").get());
  await assertSucceeds(
    adminDb.collection("orders").doc("ord_1").update({
      status: "DELIVERED",
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    adminDb.collection("orders").doc("ord_1").update({
      status: "PROCESSING",
      updatedAt: pastDate(500),
    })
  );
});
