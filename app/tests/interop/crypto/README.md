# Android/web encrypted-chat compatibility

`unit/resources/crypto/web-chat-crypto-v1.json` contains synthetic test keys and
envelopes produced by the unmodified `createRealChatCryptoProvider` from
workspace_ui revision `f9232838e5230e39dd2e383231b125c2c7790a25`.
`WorkspaceChatCryptoTest` checks decoding and reproduces its exact message
envelope with the fixture salt. It also checks RFC 5869 A.1, RFC 8439 2.8.2,
and RFC 9180 A.2.1 vectors independently.

To verify the other direction, run the crypto JVM tests with
`CASSI_CRYPTO_INTEROP_OUTPUT=/absolute/path/android-vectors.json`, then use `tsx`
to execute `verify-web-interop.mjs PROVIDER_TS ANDROID_VECTORS_JSON`. Extract
the provider and its type file using `git show` from the same web revision into
an isolated temporary directory with `package.json` containing `"type":"module"`.
Install the web revision's exact crypto dependencies there:

```
@hpke/chacha20poly1305@1.8.0
@hpke/core@1.9.0
@hpke/dhkem-x25519@1.8.0
@noble/ciphers@2.4.0
@noble/hashes@2.2.0
```

The verifier imports and calls the actual web provider; it does not reimplement
the protocol. Fixture exports contain only synthetic test data. Normal app code
never exports keys or plaintext.

`EncryptedChatCryptoInstrumentedTest` separately verifies Android's JCA
ChaCha20-Poly1305 primitive and the Keystore-backed durable store. It creates
isolated preference names and Keystore aliases, then removes those test resources.
Production chat preferences must be excluded from backup/device transfer because
their Android Keystore master key cannot be transferred.
