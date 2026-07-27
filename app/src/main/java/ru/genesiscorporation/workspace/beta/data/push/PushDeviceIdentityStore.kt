package ru.genesiscorporation.workspace.beta.data.push

import android.content.Context
import com.google.crypto.tink.AccessesPartialKey
import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.hybrid.HpkeParameters
import com.google.crypto.tink.hybrid.HpkePublicKey
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class PushDeviceIdentity(
    val registrationUuid: String,
    val keyUuid: String,
    val publicKey: String,
)

interface PushDeviceIdentityProvider {
    suspend fun getOrCreateIdentity(): PushDeviceIdentity
    suspend fun getOrCreateRegistrationUuid(): String
}

class TinkPushDeviceIdentityStore(
    context: Context,
) : PushDeviceIdentityProvider {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        PREFERENCES_FILE,
        Context.MODE_PRIVATE,
    )
    private val lock = Any()

    override suspend fun getOrCreateIdentity(): PushDeviceIdentity =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                loadOrCreateIdentity()
            }
        }

    override suspend fun getOrCreateRegistrationUuid(): String =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                getOrCreateUuid(REGISTRATION_UUID)
            }
        }

    @OptIn(ExperimentalEncodingApi::class)
    @AccessesPartialKey
    private fun loadOrCreateIdentity(): PushDeviceIdentity {
        HybridConfig.register()
        val keysetAlreadyExists = preferences.contains(KEYSET_NAME)
        val parameters = HpkeParameters.builder()
            .setVariant(HpkeParameters.Variant.NO_PREFIX)
            .setKemId(HpkeParameters.KemId.DHKEM_X25519_HKDF_SHA256)
            .setKdfId(HpkeParameters.KdfId.HKDF_SHA256)
            .setAeadId(HpkeParameters.AeadId.AES_256_GCM)
            .build()
        val manager = AndroidKeysetManager.Builder()
            .withSharedPref(appContext, KEYSET_NAME, PREFERENCES_FILE)
            .withKeyTemplate(KeyTemplate.createFrom(parameters))
            .withMasterKeyUri("$ANDROID_KEYSTORE_URI_PREFIX$MASTER_KEY_ALIAS")
            .build()

        if (!manager.isUsingKeystore) {
            preferences.edit()
                .remove(KEYSET_NAME)
                .remove(KEY_UUID)
                .commit()
            throw GeneralSecurityException(
                "Android Keystore is required for the push encryption key",
            )
        }

        val publicKey = manager.keysetHandle
            .getPublicKeysetHandle()
            .primary
            .key as? HpkePublicKey
            ?: throw GeneralSecurityException("Tink did not create an HPKE public key")

        if (
            publicKey.parameters.kemId != HpkeParameters.KemId.DHKEM_X25519_HKDF_SHA256 ||
            publicKey.parameters.kdfId != HpkeParameters.KdfId.HKDF_SHA256 ||
            publicKey.parameters.aeadId != HpkeParameters.AeadId.AES_256_GCM ||
            publicKey.parameters.variant != HpkeParameters.Variant.NO_PREFIX
        ) {
            throw GeneralSecurityException("Unexpected HPKE key parameters")
        }

        val publicKeyBytes = publicKey.publicKeyBytes.toByteArray()
        if (publicKeyBytes.size != X25519_PUBLIC_KEY_BYTES) {
            throw GeneralSecurityException("Unexpected X25519 public key length")
        }

        val keyUuid = if (keysetAlreadyExists) {
            getOrCreateUuid(KEY_UUID)
        } else {
            UUID.randomUUID().toString().also { uuid ->
                check(preferences.edit().putString(KEY_UUID, uuid).commit())
            }
        }
        return PushDeviceIdentity(
            registrationUuid = getOrCreateUuid(REGISTRATION_UUID),
            keyUuid = keyUuid,
            publicKey = Base64.UrlSafe.encode(publicKeyBytes).trimEnd('='),
        )
    }

    private fun getOrCreateUuid(key: String): String {
        preferences.getString(key, null)?.let { stored ->
            runCatching { UUID.fromString(stored) }
                .getOrNull()
                ?.let { return it.toString() }
        }
        return UUID.randomUUID().toString().also { uuid ->
            check(preferences.edit().putString(key, uuid).commit())
        }
    }

    companion object {
        const val PREFERENCES_FILE = "workspace_push_device"
        private const val KEYSET_NAME = "hpke_keyset"
        private const val REGISTRATION_UUID = "registration_uuid"
        private const val KEY_UUID = "key_uuid"
        private const val MASTER_KEY_ALIAS = "workspace_push_hpke_master_key"
        private const val ANDROID_KEYSTORE_URI_PREFIX = "android-keystore://"
        private const val X25519_PUBLIC_KEY_BYTES = 32
    }
}
