package ru.genesiscorporation.workspace.beta.data

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.security.GeneralSecurityException

internal interface WorkspaceSnapshotCipher {
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray
    fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray
}

internal class TinkWorkspaceSnapshotCipher(
    context: Context,
) : WorkspaceSnapshotCipher {
    private val appContext = context.applicationContext
    private val aead: Aead by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AeadConfig.register()
        val manager = AndroidKeysetManager.Builder()
            .withSharedPref(appContext, KEYSET_NAME, PREFERENCES_FILE)
            .withKeyTemplate(
                KeyTemplate.createFrom(
                    PredefinedAeadParameters.AES256_GCM,
                ),
            )
            .withMasterKeyUri("$ANDROID_KEYSTORE_URI_PREFIX$MASTER_KEY_ALIAS")
            .build()
        if (!manager.isUsingKeystore) {
            throw GeneralSecurityException(
                "Android Keystore is required for Workspace offline history",
            )
        }
        manager.keysetHandle.getPrimitive(
            RegistryConfiguration.get(),
            Aead::class.java,
        )
    }

    override fun encrypt(
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): ByteArray = aead.encrypt(plaintext, associatedData)

    override fun decrypt(
        ciphertext: ByteArray,
        associatedData: ByteArray,
    ): ByteArray = aead.decrypt(ciphertext, associatedData)

    companion object {
        const val PREFERENCES_FILE = "workspace_snapshot_crypto"
        private const val KEYSET_NAME = "snapshot_keyset"
        private const val MASTER_KEY_ALIAS = "workspace_snapshot_master_key"
        private const val ANDROID_KEYSTORE_URI_PREFIX = "android-keystore://"
    }
}
