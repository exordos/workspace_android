package ru.genesiscorporation.workspace.beta.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import java.io.File
import java.util.UUID

internal class IsolatedAndroidTestContext(
    base: Context,
    label: String,
) : ContextWrapper(base) {
    private val prefix = "cassi-test-$label-${UUID.randomUUID()}"
    private val root = File(base.cacheDir, prefix)
    private val isolatedFiles = File(root, "files").apply { mkdirs() }
    private val isolatedCache = File(root, "cache").apply { mkdirs() }
    private val preferenceNames = mutableSetOf<String>()

    override fun getApplicationContext(): Context = this
    override fun getFilesDir(): File = isolatedFiles
    override fun getCacheDir(): File = isolatedCache

    override fun getSharedPreferences(
        name: String,
        mode: Int,
    ): SharedPreferences {
        val isolatedName = "$prefix-$name"
        preferenceNames += isolatedName
        return baseContext.getSharedPreferences(isolatedName, mode)
    }

    fun cleanUp() {
        preferenceNames.forEach(baseContext::deleteSharedPreferences)
        root.deleteRecursively()
    }
}
