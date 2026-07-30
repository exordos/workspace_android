package ru.genesiscorporation.workspace.beta.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class WorkspaceAccountTest {
    @Test
    fun accountIdentityIsStableAcrossHarmlessCasingAndSlashes() {
        assertEquals(
            buildWorkspaceAccountId(
                "https://WORKSPACE.example.com/",
                "AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA",
                "BBBBBBBB-BBBB-4BBB-8BBB-BBBBBBBBBBBB",
            ),
            buildWorkspaceAccountId(
                "https://workspace.example.com",
                "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
            ),
        )
    }

    @Test
    fun projectAndUserBothPartitionAccountsOnOneServer() {
        val base = "https://workspace.example.com"
        val userA = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        val userB = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
        val projectA = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"
        val projectB = "dddddddd-dddd-4ddd-8ddd-dddddddddddd"

        assertNotEquals(
            buildWorkspaceAccountId(base, projectA, userA),
            buildWorkspaceAccountId(base, projectB, userA),
        )
        assertNotEquals(
            buildWorkspaceAccountId(base, projectA, userA),
            buildWorkspaceAccountId(base, projectA, userB),
        )
    }

    @Test
    fun attachmentCacheDirectoryIsStableAndAccountScoped() {
        val cacheRoot = Files.createTempDirectory("workspace-cache").toFile()
        try {
            val first = accountAttachmentCacheDirectory(cacheRoot, "account-a")
            val firstAgain = accountAttachmentCacheDirectory(cacheRoot, "account-a")
            val second = accountAttachmentCacheDirectory(cacheRoot, "account-b")

            assertEquals(first.canonicalPath, firstAgain.canonicalPath)
            assertNotEquals(first.canonicalPath, second.canonicalPath)
            assertTrue(first.canonicalPath.startsWith(cacheRoot.canonicalPath))
            assertFalse(first.name.contains("account-a"))
        } finally {
            cacheRoot.deleteRecursively()
        }
    }

    @Test
    fun attachmentCacheSizeAndClearStayInsideExactAccountDirectory() {
        val cacheRoot = Files.createTempDirectory("workspace-cache-clear").toFile()
        try {
            val first = accountAttachmentCacheDirectory(cacheRoot, "account-a")
            val second = accountAttachmentCacheDirectory(cacheRoot, "account-b")
            assertTrue(first.mkdirs())
            assertTrue(second.mkdirs())
            File(first, "one.bin").writeBytes(ByteArray(12))
            File(first, "two.bin").writeBytes(ByteArray(30))
            File(second, "keep.bin").writeBytes(ByteArray(99))

            assertEquals(
                42L,
                accountAttachmentCacheSizeBytes(cacheRoot, "account-a"),
            )
            assertTrue(clearAccountAttachmentCache(cacheRoot, "account-a"))
            assertEquals(0L, accountAttachmentCacheSizeBytes(cacheRoot, "account-a"))
            assertFalse(first.exists())
            assertEquals(
                99L,
                accountAttachmentCacheSizeBytes(cacheRoot, "account-b"),
            )
            assertTrue(File(second, "keep.bin").exists())
        } finally {
            cacheRoot.deleteRecursively()
        }
    }

    @Test
    fun credentialSnapshotStringNeverContainsCredentialsOrIdentity() {
        val snapshot = ActiveCredentialSnapshot(
            ownerKey = "owner-secret",
            accountId = "account-secret",
            baseUrl = "https://private.example.com",
            projectId = "project-secret",
            userId = "user-secret",
            accessToken = "access-secret",
            refreshToken = "refresh-secret",
        )

        val rendered = snapshot.toString()

        listOf(
            "owner-secret",
            "account-secret",
            "private.example.com",
            "project-secret",
            "user-secret",
            "access-secret",
            "refresh-secret",
        ).forEach { sensitiveValue ->
            assertFalse(rendered.contains(sensitiveValue))
        }
        assertTrue(rendered.contains("accessTokenPresent=true"))
        assertTrue(rendered.contains("refreshTokenPresent=true"))
    }
}
