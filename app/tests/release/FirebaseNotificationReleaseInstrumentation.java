package com.exordos.workspace.cassi.releasetest;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import java.util.Arrays;
import java.util.function.BooleanSupplier;

/** Drives the installed optimized APK through its real FCM receiver, without R8 test keep rules. */
public final class FirebaseNotificationReleaseInstrumentation extends Instrumentation {
    private NotificationManager manager;
    private final String[] ids = {"71401", "71402", "2147483648", "9223372036854775808", "71403", "0"};

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    private void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private void await(BooleanSupplier condition) {
        long deadline = SystemClock.uptimeMillis() + 10000;
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition.getAsBoolean()) return;
            SystemClock.sleep(25);
        }
        require(condition.getAsBoolean(), "Notification state did not converge");
    }

    private Notification active(String id) {
        String tag = id.length() > 10 || Long.parseLong(id) > Integer.MAX_VALUE ? "workspace-message:" + id : null;
        int number = tag == null ? Integer.parseInt(id) : 0;
        for (StatusBarNotification entry : manager.getActiveNotifications()) {
            if (entry.getId() == number && java.util.Objects.equals(tag, entry.getTag())) return entry.getNotification();
        }
        return null;
    }

    private Notification notification(String id) { await(() -> active(id) != null); return active(id); }

    private void deliver(String kind, String id, String sender) {
        Intent intent = new Intent("com.google.android.c2dm.intent.RECEIVE")
                .setPackage(getTargetContext().getPackageName()).putExtra("from", "synthetic-sender")
                .putExtra("kind", kind);
        if ("remove_notification_message".equals(kind)) intent.putExtra("message_ids", id);
        else {
            if (id != null) intent.putExtra("workspace_message_id", id);
            intent.putExtra("sender_id", sender).putExtra("sender_full_name", "Synthetic " + sender)
                    .putExtra("content", "Synthetic content").putExtra("stream", "synthetic-stream")
                    .putExtra("topic", "synthetic-topic");
        }
        getTargetContext().sendBroadcast(intent);
    }

    private void open(Activity activity, PendingIntent pending, String expected) throws Exception {
        ActivityOptions options = ActivityOptions.makeBasic();
        if (Build.VERSION.SDK_INT >= 34) options.setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        pending.send(getTargetContext(), 0, null, null, null, null, options.toBundle());
        await(() -> {
            String[] actual = {null};
            runOnMainSync(() -> actual[0] = activity.getIntent().getStringExtra("deeplink"));
            return expected.equals(actual[0]);
        });
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        int resultCode = Activity.RESULT_CANCELED;
        Activity activity = null;
        try {
            require("ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE), "Dedicated emulator required");
            require((getTargetContext().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0,
                    "Install the optimized release APK");
            manager = getTargetContext().getSystemService(NotificationManager.class);
            require(manager.getActiveNotifications().length == 0, "Use a clean test installation");
            activity = startActivitySync(getTargetContext().getPackageManager()
                    .getLaunchIntentForPackage(getTargetContext().getPackageName()));
            for (String kind : Arrays.asList("private_chat_message", "stream_chat_message")) {
                for (String invalid : Arrays.asList(null, "", "bad", "-1", "+1")) deliver(kind, invalid, "101");
            }
            deliver("remove_notification_message", "bad,, -1", "101");
            deliver("private_chat_message", "71401", "101");
            PendingIntent older = notification("71401").contentIntent;
            deliver("stream_chat_message", "71402", "102");
            PendingIntent newer = notification("71402").contentIntent;
            require(active("0") == null, "Invalid ID posted notification zero");
            require(!older.equals(newer), "Different notifications share a PendingIntent");
            if (Build.VERSION.SDK_INT >= 31) require(older.isImmutable() && newer.isImmutable(), "Mutable intent");
            open(activity, older, "dialog/101");
            open(activity, newer, "stream/synthetic-stream/synthetic-topic");
            deliver("private_chat_message", "71401", "103");
            await(() -> "Synthetic 103".equals(notification("71401").extras.getString(Notification.EXTRA_TITLE)));
            require(older.equals(notification("71401").contentIntent), "Update changed intent identity");
            open(activity, older, "dialog/103");
            open(activity, newer, "stream/synthetic-stream/synthetic-topic");
            deliver("private_chat_message", "2147483648", "101");
            deliver("private_chat_message", "9223372036854775808", "102");
            notification("2147483648"); notification("9223372036854775808");
            deliver("remove_notification_message", "71401, bad, 2147483648, 9223372036854775808", "101");
            deliver("private_chat_message", "71403", "101");
            notification("71403");
            await(() -> active("71401") == null && active("2147483648") == null && active("9223372036854775808") == null);
            require(newer.equals(notification("71402").contentIntent), "Cancellation changed another intent");
            open(activity, newer, "stream/synthetic-stream/synthetic-topic");
            result.putString("result", "PASS: optimized FCM callbacks and independent MainActivity targets");
            resultCode = Activity.RESULT_OK;
        } catch (Throwable failure) {
            result.putString("result", "FAIL"); result.putString("failure", failure.toString());
        } finally {
            if (activity != null) { Activity target = activity; runOnMainSync(target::finish); }
            if (manager != null) for (String id : ids) {
                boolean wide = id.length() > 10 || Long.parseLong(id) > Integer.MAX_VALUE;
                manager.cancel(wide ? "workspace-message:" + id : null, wide ? 0 : Integer.parseInt(id));
            }
        }
        finish(resultCode, result);
    }
}
