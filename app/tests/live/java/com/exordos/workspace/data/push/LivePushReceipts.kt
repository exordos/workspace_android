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

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.serialization.json.Json

/** Test-only lookup of externally authenticated receipts; specify the connection when accounts repeat. */
internal fun livePushRoute(context: Context, eventUuid: String, serverId: String?): PushRoute? {
    val matches = mutableListOf<PushRoute>()
    SQLiteDatabase.openDatabase(context.getDatabasePath("workspace_push_inbox.db").path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        db.rawQuery("SELECT route FROM push_receipts WHERE route IS NOT NULL", null).use { cursor ->
            while (cursor.moveToNext()) {
                val route = Json.decodeFromString<PushRoute>(cursor.getString(0))
                if (route.eventUuid == eventUuid && (serverId == null || route.serverId == serverId)) matches += route
            }
        }
    }
    require(matches.size <= 1) { "Multiple saved connections matched; specify serverId" }
    return matches.singleOrNull()
}
internal fun livePushClosed(context: Context, identity: String): Boolean =
    SQLiteDatabase.openDatabase(context.getDatabasePath("workspace_push_inbox.db").path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        db.rawQuery("SELECT state FROM push_receipts WHERE identity=?", arrayOf(identity)).use { it.moveToFirst() && it.getString(0) == "closed" }
    }
