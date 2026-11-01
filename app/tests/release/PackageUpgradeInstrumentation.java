package com.exordos.workspace.cassi.releasetest;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import org.json.JSONObject;

/** Framework-only driver: seed an old debug APK, install -r the optimized candidate, then verify. */
public final class PackageUpgradeInstrumentation extends Instrumentation {
    private Bundle arguments;

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); this.arguments = arguments; start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            waitForIdleSync();
            Context context = getTargetContext();
            require("com.exordos.workspace".equals(context.getPackageName()), "Wrong target application");
            File record = new File(context.getFilesDir(), "cassi-package-upgrade.json");
            if ("seed".equals(arguments.getString("mode"))) {
                require((context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0,
                    "Seeding is restricted to the old debug fixture");
                require(!context.getApplicationInfo().className.equals("com.exordos.workspace.WorkspaceApplication"),
                    "Seed must run before the package migration");
                JSONObject state = new JSONObject();
                state.put("uid", context.getApplicationInfo().uid);
                state.put("firstInstallTime", context.getPackageManager().getPackageInfo(context.getPackageName(), 0).firstInstallTime);
                state.put("settings", digest(settings(context)));
                state.put("tokens", digest(tokens(context)));
                Files.write(record.toPath(), state.toString().getBytes(StandardCharsets.UTF_8));
                result.putString("stream", "Seeded synthetic account fingerprints\n");
            } else if ("verify".equals(arguments.getString("mode"))) {
                require((context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0,
                    "Verification requires the optimized release APK");
                require(context.getApplicationInfo().className.equals("com.exordos.workspace.WorkspaceApplication"),
                    "Candidate source namespace was not migrated");
                JSONObject state = new JSONObject(new String(Files.readAllBytes(record.toPath()), StandardCharsets.UTF_8));
                require(state.getInt("uid") == context.getApplicationInfo().uid, "Update changed application UID");
                require(state.getLong("firstInstallTime") == context.getPackageManager().getPackageInfo(context.getPackageName(), 0).firstInstallTime,
                    "Update changed first install time");
                require(state.getString("settings").equals(digest(settings(context))), "Saved account settings changed during replacement");
                require(state.getString("tokens").equals(digest(tokens(context))), "Encrypted token storage changed during replacement");
                result.putString("stream", "PASS: same UID, first install time, saved accounts and encrypted tokens\n");
            } else throw new IllegalArgumentException("Use mode=seed or mode=verify");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            while (error.getCause() != null && error.getCause() != error) error = error.getCause();
            result.putString("stream", "Package upgrade regression failed: " + error.getClass().getSimpleName() + ": " + error.getMessage());
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private static File settings(Context context) {
        return new File(context.getFilesDir(), "datastore/settings.preferences_pb");
    }

    private static File tokens(Context context) {
        return new File(context.getApplicationInfo().dataDir, "shared_prefs/secure_tokens.xml");
    }

    private static String digest(File file) throws Exception {
        require(file.isFile() && file.length() > 0, "Missing seeded account storage");
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath()));
        StringBuilder value = new StringBuilder();
        for (byte item : bytes) value.append(String.format("%02x", item & 0xff));
        return value.toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
