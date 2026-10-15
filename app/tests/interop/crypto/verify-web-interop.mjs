// Run with tsx so the unmodified TypeScript provider can be imported directly.
// Arguments: path to git-show-extracted web provider.ts, Android fixture JSON.
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";

const [providerPath, fixturePath] = process.argv.slice(2);
if (!providerPath || !fixturePath) throw new Error("Expected web provider path and Android fixture path");
const { createRealChatCryptoProvider } = await import(pathToFileURL(providerPath).href);
const fixture = JSON.parse(await readFile(fixturePath, "utf8"));
const provider = createRealChatCryptoProvider();
const bytes = (key) => new Uint8Array(Buffer.from(fixture[key], "base64"));
const unwrap = (result) => {
  assert.equal(result.ok, true, "The actual web provider rejected the Android envelope");
  return result.value;
};
const plaintext = unwrap(await provider.decryptMessage({
  groupKey: { bytes: bytes("groupKey") },
  envelope: { bytes: bytes("messageEnvelope") },
  context: fixture.messageContext,
}));
assert.deepEqual(Buffer.from(plaintext), Buffer.from(bytes("plaintext")));
const groupKey = unwrap(await provider.decryptGroupKeyGrant({
  requestKeyPair: { publicKey: { bytes: bytes("publicKey") }, privateKey: bytes("privateKey") },
  envelope: { bytes: bytes("grantEnvelope") },
  context: fixture.grantContext,
}));
assert.deepEqual(Buffer.from(groupKey.bytes), Buffer.from(bytes("groupKey")));
console.log("PASS: actual web provider decrypts Android message and HPKE grant envelopes.");
