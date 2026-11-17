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

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.app.ActivityOptions;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.pm.ApplicationInfo;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONObject;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Observes actual externally delivered encrypted pushes in the optimized APK; no payload injection. */
public final class WorkspacePushLiveReleaseInstrumentation extends Instrumentation {
    private Bundle arguments;
    private NotificationManager manager;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        this.arguments = arguments;
        start();
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private String argument(String name) {
        String value = arguments.getString(name);
        require(value != null && !value.isEmpty(), "Missing argument: " + name);
        return value;
    }

    private void await(BooleanSupplier condition, String message) {
        long deadline = SystemClock.uptimeMillis() + 30000;
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition.getAsBoolean()) return;
            SystemClock.sleep(50);
        }
        require(condition.getAsBoolean(), message);
    }

    private Notification active(String identity) {
        for (StatusBarNotification entry : manager.getActiveNotifications()) {
            if (entry.getId() == 0 && identity.equals(entry.getTag())) return entry.getNotification();
        }
        return null;
    }

    private static final class Receipt {
        final String identity;
        final JSONObject route;
        final String state;
        Receipt(String identity, JSONObject route, String state) { this.identity = identity; this.route = route; this.state = state; }
    }

    private Receipt receipt(String event) throws Exception {
        return receipt(event, arguments.getString("serverId"));
    }

    private Receipt receipt(String event, String server) throws Exception {
        Receipt found = null;
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(getTargetContext().getDatabasePath("workspace_push_inbox.db").getPath(), null, SQLiteDatabase.OPEN_READONLY);
             Cursor cursor = db.rawQuery("SELECT identity,route,state FROM push_receipts WHERE route IS NOT NULL", null)) {
            while (cursor.moveToNext()) {
                JSONObject route = new JSONObject(cursor.getString(1));
                if (!event.equals(route.getString("eventUuid"))) continue;
                if (server != null && !server.equals(route.getString("serverId"))) continue;
                require(found == null, "Multiple saved connections matched; specify serverId");
                found = new Receipt(cursor.getString(0), route, cursor.getString(2));
            }
        }
        return found;
    }

    private boolean closedIdentity(String identity, String expectedState) {
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(getTargetContext().getDatabasePath("workspace_push_inbox.db").getPath(), null, SQLiteDatabase.OPEN_READONLY);
             Cursor cursor = db.rawQuery("SELECT state FROM push_receipts WHERE identity=?", new String[] {identity})) {
            return cursor.moveToFirst() && expectedState.equals(cursor.getString(0));
        } catch (Exception error) { return false; }
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            String action = argument("action");
            boolean emulator = "ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE);
            require(emulator || "observe".equals(action) || "open".equals(action) || "cancel".equals(action),
                    "Fixture mutations require a dedicated emulator");
            require((getTargetContext().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0,
                    "Install the optimized release APK");
            String event = argument("eventUuid");
            require(UUID.fromString(event).toString().equals(event), "Invalid event UUID");
            require("observe".equals(action) || "open".equals(action) || "cancel".equals(action)
                    || "dismiss".equals(action) || "age".equals(action) || "absent".equals(action)
                    || "paired".equals(action) || "openOffline".equals(action), "Unknown action");
            await(() -> {
                try { return receipt(event) != null; }
                catch (Exception notYetStored) { return false; }
            }, "No authenticated receipt for the external event");
            Receipt receipt = receipt(event);
            JSONObject route = receipt.route;
            String identity = "workspace:message:" + receipt.identity;
            String expectedMessage = "cancel".equals(action)
                    ? arguments.getString("messageUuid", "") : argument("messageUuid");
            require(route.getString("messageUuid").equals(expectedMessage), "Wrong receipt message");
            manager = getTargetContext().getSystemService(NotificationManager.class);
            if ("absent".equals(action)) {
                await(() -> active(identity) == null && closedIdentity(receipt.identity, "dismissed"),
                        "A dismissed receipt reappeared");
            } else if ("cancel".equals(action)) {
                await(() -> active(identity) == null && closedIdentity(receipt.identity, "closed"),
                        "Authenticated cancellation was not applied");
            } else if ("age".equals(action)) {
                require("unread".equals(receipt.state), "Only unread fixtures can be aged");
                try (SQLiteDatabase db = SQLiteDatabase.openDatabase(getTargetContext().getDatabasePath("workspace_push_inbox.db").getPath(), null, SQLiteDatabase.OPEN_READWRITE)) {
                    db.execSQL("UPDATE push_receipts SET created_at=? WHERE identity=?",
                            new Object[] {System.currentTimeMillis() - 15L * 24 * 3600000L, receipt.identity});
                }
            } else {
                await(() -> active(identity) != null, "Expected external notification was not displayed");
                Notification notification = active(identity);
                if (arguments.containsKey("expectedCount")) require(notification.number == Integer.parseInt(argument("expectedCount")), "Wrong unread count");
                require(notification.getTimeoutAfter() >= 3L * 24 * 3600000L, "Unexpected short alert deadline");
                require((notification.flags & Notification.FLAG_AUTO_CANCEL) == 0, "A tap would discard an unread alert");
                require(argument("expectedSender").equals(notification.extras.getString(Notification.EXTRA_TITLE)), "Wrong sender");
                require(argument("expectedPreview").contentEquals(notification.extras.getCharSequence(Notification.EXTRA_TEXT)), "Wrong preview");
                require(notification.contentIntent != null, "Missing notification intent");
                if (Build.VERSION.SDK_INT >= 31) require(notification.contentIntent.isImmutable(), "Mutable notification intent");
                if ("paired".equals(action)) {
                    String secondServer = argument("secondServerId");
                    await(() -> {
                        try { return receipt(event, secondServer) != null; }
                        catch (Exception notYetStored) { return false; }
                    }, "The second saved connection has not received its external envelope");
                    Receipt other = receipt(event, secondServer);
                    require(other != null && !receipt.identity.equals(other.identity), "Duplicate connections shared a receipt");
                    require("unread".equals(other.state), "The second connection lost its unread receipt");
                    await(() -> active("workspace:message:" + other.identity) != null,
                            "The second saved connection did not display its own notification");
                }
                if ("dismiss".equals(action)) {
                    notification.deleteIntent.send();
                    await(() -> active(identity) == null && closedIdentity(receipt.identity, "dismissed"), "Manual dismissal was not retained");
                }
                if ("open".equals(action) || "openOffline".equals(action)) {
                    if ("openOffline".equals(action)) require(getTargetContext().getSystemService(
                            ConnectivityManager.class).getActiveNetwork() == null, "Disable network for failed-loading acceptance");
                    startActivitySync(getTargetContext().getPackageManager().getLaunchIntentForPackage(getTargetContext().getPackageName()));
                    ActivityOptions options = ActivityOptions.makeBasic();
                    if (Build.VERSION.SDK_INT >= 34) options.setPendingIntentBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                    notification.contentIntent.send(getTargetContext(), 0, null, null, null, null, options.toBundle());
                    if ("openOffline".equals(action)) {
                        SystemClock.sleep(10000);
                        Receipt retained = receipt(event);
                        require(retained != null && "unread".equals(retained.state) && active(identity) != null,
                                "Offline navigation discarded an unread receipt");
                        result.putString("result", "PASS: real encrypted push retained after offline open in optimized APK");
                        finish(Activity.RESULT_OK, result);
                        return;
                    }
                    String expected = argument("expectedMessageText");
                    await(() -> {
                        AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
                        if (root == null || !getTargetContext().getPackageName().contentEquals(root.getPackageName())) return false;
                        for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(expected)) {
                            if (node.getText() != null && node.getText().toString().contains(expected)) return true;
                        }
                        return false;
                    }, "The optimized app did not load the actual chat message");
                    await(() -> active(identity) == null && closedIdentity(receipt.identity, "closed"),
                            "Successful message read did not remove its notification");
                }
            }
            result.putString("result", "PASS: real encrypted push " + action + " in optimized APK");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("result", "FAIL");
            // Provider responses, credentials and payloads must never enter test output.
            result.putString("failure_type", failure.getClass().getSimpleName());
            if (failure instanceof AssertionError) result.putString("assertion", failure.getMessage());
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
