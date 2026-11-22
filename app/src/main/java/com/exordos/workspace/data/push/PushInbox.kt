/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exordos.workspace.data.push

import com.exordos.workspace.data.WorkspaceDateTime
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.serialization.json.Json

internal data class PushReceipt(val sequence: Long, val route: PushRoute, val createdAt: Long,
    val unreadCount: Int = 1, val activeUnreadCount: Int = 1, val epochVersion: Long = 0, val epochGeneration: String = "")

/** Durable routes and terminal decisions. Message text and encryption keys never enter this database. */
internal class PushInbox(context: Context) {
    private val db = database(context.applicationContext).writableDatabase

    fun removeLegacyReceipts() {
        db.delete(TABLE, "topic_update=0", null)
    }

    /** Null means duplicate/outdated; otherwise the result says whether to alert. */
    fun updateTopic(route: PushRoute, createdAt: Long, unread: Int, active: Int,
        epochVersion: Long, epochGeneration: String): Boolean? {
        require(route.topicUpdate && unread >= active && active >= 0 && epochVersion > 0)
        db.beginTransaction()
        try {
            var oldActive = 0
            var oldState = CLOSED
            var retired = emptyList<String>()
            db.rawQuery("SELECT active_unread_count,state,epoch_version,epoch_generation,retired_generations " +
                "FROM $TABLE WHERE identity=?", arrayOf(route.identity)).use { old ->
                if (old.moveToFirst()) {
                    oldActive = old.getInt(0); oldState = old.getString(1)
                    retired = Json.decodeFromString<List<String>>(old.getString(4))
                    if (epochGeneration in retired ||
                        (old.getString(3) == epochGeneration && epochVersion <= old.getLong(2))) return null
                    if (!old.getString(3).isNullOrBlank() && old.getString(3) != epochGeneration)
                        retired = retired + old.getString(3)
                }
            }
            val state = when {
                active == 0 -> CLOSED
                oldState == DISMISSED && active <= oldActive -> DISMISSED
                else -> UNREAD
            }
            val values = values(route, createdAt, state).apply {
                put("topic_update", 1); put("unread_count", unread); put("active_unread_count", active)
                put("epoch_version", epochVersion); put("epoch_generation", epochGeneration)
                put("retired_generations", Json.encodeToString(retired))
            }
            if (db.update(TABLE, values, "identity=?", arrayOf(route.identity)) == 0)
                check(db.insertOrThrow(TABLE, null, values) != -1L)
            db.setTransactionSuccessful()
            return state == UNREAD && active > oldActive
        } finally { db.endTransaction() }
    }

    /** A WebSocket all-read snapshot can arrive before its encrypted FCM copy. */
    fun closeTopic(registration: PushRegistration, streamUuid: String, topicUuid: String,
        epochVersion: Long, epochGeneration: String) {
        val identity = PushRoute.topicIdentity(registration.serverId, registration.organizationUuid,
            registration.projectUuid, registration.userUuid, streamUuid, topicUuid)
        db.beginTransaction()
        try {
            var retired = emptyList<String>()
            db.rawQuery("SELECT epoch_version,epoch_generation,retired_generations FROM $TABLE WHERE identity=?",
                arrayOf(identity)).use { old ->
                if (old.moveToFirst()) {
                    retired = Json.decodeFromString<List<String>>(old.getString(2))
                    if (epochGeneration in retired ||
                        (old.getString(1) == epochGeneration && epochVersion <= old.getLong(0))) return
                    if (!old.getString(1).isNullOrBlank() && old.getString(1) != epochGeneration)
                        retired = retired + old.getString(1)
                }
            }
            val values = ContentValues().apply {
                put("state", CLOSED); put("unread_count", 0); put("active_unread_count", 0)
                put("created_at", System.currentTimeMillis())
                put("epoch_version", epochVersion); put("epoch_generation", epochGeneration)
                put("retired_generations", Json.encodeToString(retired))
            }
            if (db.update(TABLE, values, "identity=?", arrayOf(identity)) == 0) {
                values.apply {
                    put("identity", identity); put("conversation", identity); put("topic_update", 1)
                    put("server_id", registration.serverId); put("organization_uuid", registration.organizationUuid)
                    put("project_uuid", registration.projectUuid); put("user_uuid", registration.userUuid)
                    put("message_uuid", "")
                }
                db.insertOrThrow(TABLE, null, values)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    /** Close only the card captured before a background HTTP reconciliation. */
    fun closeTopicSnapshot(receipt: PushReceipt) {
        db.update(TABLE, ContentValues().apply {
            put("state", CLOSED); put("unread_count", 0); put("active_unread_count", 0)
            put("created_at", System.currentTimeMillis())
        }, "identity=? AND epoch_version=? AND epoch_generation=? AND route=?",
            arrayOf(receipt.route.identity, receipt.epochVersion.toString(), receipt.epochGeneration, Json.encodeToString(receipt.route)))
    }

    /** Retain closed topic snapshots for one delivery window to reject delayed events. */
    fun pruneTransientDecisions(now: Long) {
        db.delete(TABLE, "state=? AND topic_update=1 AND created_at<?",
            arrayOf(CLOSED, (now - DELIVERY_WINDOW_MILLIS).toString()))
    }

    fun dismiss(identity: String, snapshot: Long, generation: String) {
        db.update(TABLE, ContentValues().apply { put("state", DISMISSED) },
            "identity=? AND state=? AND topic_update=1 AND epoch_version<=? AND epoch_generation=?",
            arrayOf(identity, UNREAD, snapshot.toString(), generation))
    }

    fun removeServer(serverId: String) { db.delete(TABLE, "server_id=?", arrayOf(serverId)) }

    fun resolve(identity: String): PushRoute? = db.rawQuery(
        "SELECT route FROM $TABLE WHERE identity=? AND state=? AND topic_update=1", arrayOf(identity, UNREAD)
    ).use { if (it.moveToFirst()) Json.decodeFromString<PushRoute>(it.getString(0)) else null }

    fun activeTopics(limit: Int): List<PushReceipt> = db.rawQuery(
        "SELECT sequence,route,created_at,unread_count,active_unread_count,epoch_version,epoch_generation FROM $TABLE " +
            "WHERE state=? AND topic_update=1 ORDER BY created_at DESC,identity LIMIT ?", arrayOf(UNREAD, limit.toString())
    ).use(::receipts)

    fun pending(registration: PushRegistration, afterSequence: Long, limit: Int = 50): List<PushReceipt> = db.rawQuery(
        "SELECT sequence,route,created_at,unread_count,active_unread_count,epoch_version,epoch_generation FROM $TABLE WHERE server_id=? AND organization_uuid=? " +
            "AND project_uuid=? AND user_uuid=? AND state=? AND topic_update=1 AND sequence>? ORDER BY sequence LIMIT ?",
        arrayOf(registration.serverId, registration.organizationUuid, registration.projectUuid, registration.userUuid,
            UNREAD, afterSequence.toString(), limit.toString())
    ).use(::receipts)

    private fun receipts(cursor: Cursor): List<PushReceipt> = buildList {
        while (cursor.moveToNext()) add(PushReceipt(cursor.getLong(0), Json.decodeFromString(cursor.getString(1)), cursor.getLong(2),
            cursor.getInt(3), cursor.getInt(4), cursor.getLong(5), cursor.getString(6) ?: ""))
    }

    private fun values(route: PushRoute, createdAt: Long, state: String) = ContentValues().apply {
        put("identity", route.identity); put("conversation", route.conversationIdentity)
        put("server_id", route.serverId); put("organization_uuid", route.organizationUuid)
        put("project_uuid", route.projectUuid); put("user_uuid", route.userUuid); put("message_uuid", route.messageUuid)
        put("route", Json.encodeToString(route)); put("created_at", createdAt); put("state", state)
    }

    private class Database(context: Context) : SQLiteOpenHelper(context, "workspace_push_inbox.db", null, 2) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE $TABLE (sequence INTEGER PRIMARY KEY AUTOINCREMENT, identity TEXT NOT NULL UNIQUE, " +
                "conversation TEXT NOT NULL, server_id TEXT NOT NULL, organization_uuid TEXT NOT NULL, project_uuid TEXT NOT NULL, " +
                "user_uuid TEXT NOT NULL, message_uuid TEXT NOT NULL, route TEXT, created_at INTEGER NOT NULL, state TEXT NOT NULL)")
            db.execSQL("CREATE INDEX push_conversations ON $TABLE(state,conversation,created_at)")
            db.execSQL("CREATE INDEX push_owner ON $TABLE(server_id,organization_uuid,project_uuid,user_uuid,state,sequence)")
            addTopicColumns(db)
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) addTopicColumns(db)
        }
        private fun addTopicColumns(db: SQLiteDatabase) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN topic_update INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN unread_count INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN active_unread_count INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN epoch_version INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN epoch_generation TEXT")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN retired_generations TEXT NOT NULL DEFAULT '[]'")
        }
    }
    companion object {
        const val DELIVERY_WINDOW_MILLIS = 3600_000L
        private const val TABLE = "push_receipts"
        private const val UNREAD = "unread"
        private const val DISMISSED = "dismissed"
        private const val CLOSED = "closed"
        private var helper: Database? = null
        @Synchronized private fun database(context: Context): Database = helper ?: Database(context).also { helper = it }
    }
}
