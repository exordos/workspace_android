package com.exordos.workspace.cassi.releasetest;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;

/** The optimized counterpart of SdkBootstrapTest; vendor-kept SDK APIs need no AndroidX runner. */
public final class SdkReleaseInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        Activity host = null;
        Activity conference = null;
        ActivityMonitor monitor = null;
        try {
            require((getTargetContext().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0,
                "Install the optimized release APK");
            ClassLoader loader = getTargetContext().getClassLoader();
            Intent launch = getTargetContext().getPackageManager().getLaunchIntentForPackage(getTargetContext().getPackageName());
            require(launch != null, "Missing launcher");
            host = startActivitySync(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Object builder = loader.loadClass("org.jitsi.meet.sdk.JitsiMeetConferenceOptions$Builder").getConstructor().newInstance();
            call(builder, "setServerURL", new URL("https://cassi-release-smoke.invalid"));
            call(builder, "setRoom", "cassi-local-smoke");
            call(builder, "setAudioMuted", true);
            call(builder, "setVideoMuted", true);
            call(builder, "setFeatureFlag", "welcomepage.enabled", false);
            call(builder, "setFeatureFlag", "prejoinpage.enabled", false);
            Object options = call(builder, "build");
            Activity launchingHost = host;
            monitor = addMonitor("org.jitsi.meet.sdk.JitsiMeetActivity", null, false);
            runOnMainSync(() -> {
                try { call(loader.loadClass("org.jitsi.meet.sdk.JitsiMeetActivity"), "launch", launchingHost, options); }
                catch (Exception error) { throw new IllegalStateException(error); }
            });
            conference = waitForMonitorWithTimeout(monitor, 15_000);
            require(conference != null, "SDK activity did not open");
            Method managerGetter = loader.loadClass("org.jitsi.meet.sdk.ReactInstanceManagerHolder")
                .getDeclaredMethod("getReactInstanceManager");
            managerGetter.setAccessible(true);
            Class<?> reactContext = loader.loadClass("com.facebook.react.bridge.ReactContext");
            long deadline = SystemClock.elapsedRealtime() + 30_000;
            boolean ready = false;
            Activity sdk = conference;
            boolean[] mounted = {false};
            while ((!ready || !mounted[0]) && SystemClock.elapsedRealtime() < deadline) {
                Object manager = managerGetter.invoke(null);
                if (manager != null) for (Field field : manager.getClass().getDeclaredFields()) {
                    if (!reactContext.isAssignableFrom(field.getType())) continue;
                    field.setAccessible(true);
                    Object context = field.get(manager);
                    if (context != null && Boolean.TRUE.equals(call(context, "hasActiveCatalystInstance"))) {
                        ready = Boolean.TRUE.equals(call(call(context, "getCatalystInstance"), "hasRunJSBundle"));
                    }
                }
                runOnMainSync(() -> mounted[0] = mounted(sdk.getWindow().getDecorView()));
                if (!ready || !mounted[0]) SystemClock.sleep(100);
            }
            require(ready, "Native bridge or bundled JavaScript did not initialize");
            require(mounted[0], "SDK view was not mounted");
            result.putString("stream", "PASS: optimized native bridge, bundled JavaScript and mounted Jitsi view\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "SDK release regression failed: " + error);
            finish(Activity.RESULT_CANCELED, result);
        } finally {
            if (monitor != null) removeMonitor(monitor);
            Activity closingConference = conference;
            Activity closingHost = host;
            runOnMainSync(() -> {
                if (closingConference != null) closingConference.finish();
                if (closingHost != null) closingHost.finish();
            });
        }
    }

    private static Object call(Object receiver, String name, Object... arguments) throws Exception {
        Class<?> type = receiver instanceof Class<?> ? (Class<?>) receiver : receiver.getClass();
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
            try { return method.invoke(receiver instanceof Class<?> ? null : receiver, arguments); }
            catch (IllegalArgumentException incompatibleOverload) { /* Try the matching SDK overload. */ }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    private static boolean mounted(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            if (view.getClass().getName().equals("org.jitsi.meet.sdk.JitsiMeetView"))
                return group.getChildCount() > 0 && group.getChildAt(0) instanceof ViewGroup
                    && ((ViewGroup) group.getChildAt(0)).getChildCount() > 0 && view.isAttachedToWindow();
            for (int i = 0; i < group.getChildCount(); i++) if (mounted(group.getChildAt(i))) return true;
        }
        return false;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
