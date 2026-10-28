package com.exordos.workspace.cassi.releasetest;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

/** Verifies the real optimized SDK hook and build ID without uploading a test crash. */
public final class CrashlyticsReleaseInstrumentation extends Instrumentation {
    private Bundle arguments;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        this.arguments = arguments;
        start();
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private boolean containsHandler(Object handler, String expected, Set<Object> seen) throws Exception {
        if (handler == null || !seen.add(handler)) return false;
        if (handler.getClass().getName().equals(expected)) return true;
        // A runner or another SDK may wrap the process handler. Inspect only app-owned handlers.
        for (Class<?> type = handler.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().startsWith("java.") || type.getName().startsWith("android.")) continue;
            for (Field field : type.getDeclaredFields()) {
                if (!Thread.UncaughtExceptionHandler.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                if (containsHandler(field.get(handler), expected, seen)) return true;
            }
        }
        return false;
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        int code = Activity.RESULT_CANCELED;
        try {
            require("ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE),
                    "Use a dedicated emulator");
            ApplicationInfo app = getTargetContext().getPackageManager().getApplicationInfo(
                    getTargetContext().getPackageName(), PackageManager.GET_META_DATA);
            require((app.flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0, "Install the optimized release");
            require(app.metaData == null || app.metaData.getBoolean("firebase_crashlytics_collection_enabled", true),
                    "Release manifest disabled automatic collection");
            String expectedId = arguments.getString("mappingId");
            String expectedHandler = arguments.getString("handlerClass");
            require(expectedId != null && !expectedId.isEmpty(), "Supply the candidate mapping ID");
            require(expectedHandler != null && !expectedHandler.isEmpty(), "Supply the R8 handler class");
            int id = getTargetContext().getResources().getIdentifier(
                    "com.google.firebase.crashlytics.mapping_file_id", "string",
                    getTargetContext().getPackageName());
            require(id != 0 && expectedId.equals(getTargetContext().getString(id)),
                    "Packaged Crashlytics build ID differs from the candidate mapping ID");
            long deadline = SystemClock.uptimeMillis() + 15000;
            boolean installed;
            do {
                installed = containsHandler(Thread.getDefaultUncaughtExceptionHandler(), expectedHandler, new HashSet<>());
                if (!installed) SystemClock.sleep(50);
            } while (!installed && SystemClock.uptimeMillis() < deadline);
            require(installed, "Crashlytics did not install its uncaught exception handler under R8");
            result.putString("result", "PASS: optimized Crashlytics hook and matching mapping ID");
            code = Activity.RESULT_OK;
        } catch (Throwable failure) {
            result.putString("result", "FAIL");
            result.putString("failure", failure.toString());
        }
        finish(code, result);
    }
}
