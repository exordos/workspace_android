package ru.genesiscorporation.workspace.beta.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceSnapshotMigrationInstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WorkspaceSnapshotDatabase::class.java,
    )

    @After
    fun cleanUp() {
        InstrumentationRegistry.getInstrumentation()
            .targetContext
            .deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migrationFromOneToTwoPreservesHistoryAndCreatesCatalogTables() {
        helper.createDatabase(DATABASE_NAME, 1).apply {
            execSQL(
                """
                INSERT INTO cached_streams (
                    owner_key_hash,
                    uuid,
                    position,
                    encrypted_payload,
                    cached_at_millis
                ) VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf(
                    "owner-hash",
                    STREAM_UUID,
                    0,
                    byteArrayOf(1, 2, 3),
                    1_785_456_000_000L,
                ),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            2,
            true,
            WorkspaceSnapshotDatabase.MIGRATION_1_2,
        ).use { database ->
            assertEquals(
                1,
                database.query("SELECT * FROM cached_streams").use {
                    it.count
                },
            )
            assertEquals(
                0,
                database.query("SELECT * FROM cached_folders").use {
                    it.count
                },
            )
            assertEquals(
                0,
                database.query("SELECT * FROM cached_users").use {
                    it.count
                },
            )
            assertEquals(
                0,
                database.query(
                    "SELECT * FROM cached_stream_bindings",
                ).use { it.count },
            )
        }
    }

    companion object {
        private const val DATABASE_NAME =
            "workspace-snapshot-migration-test.db"
        private const val STREAM_UUID =
            "20000000-0000-4000-8000-000000000001"
    }
}
