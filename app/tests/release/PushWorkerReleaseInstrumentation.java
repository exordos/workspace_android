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

package com.exordos.workspace.cassi.releasetest;

import android.app.ActivityManager;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.os.SystemClock;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

/** Checks real cold-start worker construction in an optimized APK without reflection keep rules. */
public final class PushWorkerReleaseInstrumentation extends Instrumentation {
    private String workerClass;
    private Set<String> previousIds;
    private int previousTasksRemoved;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        workerClass = arguments == null
                ? "com.exordos.workspace.data.push.PushReconciliationWorker"
                : arguments.getString("workerClass",
                        "com.exordos.workspace.data.push.PushReconciliationWorker");
        // Remove tasks from earlier UI tests before Application.onCreate can start new work.
        ActivityManager activities = (ActivityManager) getTargetContext().getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.AppTask task : activities.getAppTasks()) {
            task.finishAndRemoveTask();
            previousTasksRemoved++;
        }
        // Instrumentation.onCreate precedes Application.onCreate and its startup enqueue.
        previousIds = workIds(false);
        start();
    }

    @Override public void onStart() {
        Bundle output = new Bundle();
        try {
            waitForIdleSync();
            Context context = getTargetContext();
            if ((context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                throw new AssertionError("An optimized, non-debuggable target is required");
            }
            long deadline = SystemClock.elapsedRealtime() + 30_000;
            boolean completed = false;
            while (SystemClock.elapsedRealtime() < deadline) {
                Set<String> succeeded;
                try {
                    succeeded = workIds(true);
                } catch (SQLiteException startingDatabase) {
                    SystemClock.sleep(50);
                    continue;
                }
                succeeded.removeAll(previousIds);
                if (!succeeded.isEmpty()) {
                    completed = true;
                    break;
                }
                SystemClock.sleep(50);
            }
            if (!completed) throw new AssertionError("No new startup reconciliation worker succeeded");
            ActivityManager activities = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (!activities.getAppTasks().isEmpty()) throw new AssertionError("A product Activity was launched");
            output.putString("result", "PASS");
            output.putBoolean("new_startup_worker_succeeded", true);
            output.putBoolean("activity_launched", false);
            output.putInt("previous_test_tasks_removed", previousTasksRemoved);
            finish(-1, output);
        } catch (Throwable error) {
            output.putString("result", "FAIL");
            output.putString("failure", error.getClass().getSimpleName() + ": " + error.getMessage());
            finish(0, output);
        }
    }

    private Set<String> workIds(boolean succeededOnly) {
        Set<String> ids = new HashSet<>();
        File file = new File(getTargetContext().getNoBackupFilesDir(), "androidx.work.workdb");
        if (!file.exists()) return ids;
        try (SQLiteDatabase database = SQLiteDatabase.openDatabase(file.getPath(), null,
                SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
             Cursor rows = database.query("WorkSpec", new String[]{"id", "state"},
                     "worker_class_name = ?", new String[]{workerClass}, null, null, null)) {
            while (rows.moveToNext()) {
                if (!succeededOnly || rows.getInt(1) == 2) ids.add(rows.getString(0));
            }
        }
        return ids;
    }
}
