package com.exordos.workspace.cassi.releasetest;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Bundle;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Seeds only a dedicated emulator's debug installation, before a same-certificate release update.
 * No production keep rules, account credentials, or application test hooks are required. */
public final class AvatarFixtureInstrumentation extends Instrumentation {
    private Bundle arguments;
    private ClassLoader loader;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        this.arguments = arguments;
        start();
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    /** Invoke a Kotlin suspend function from a framework-only driver. */
    private Object suspend(Method method, Object receiver, Object... parameters) throws Exception {
        Class<?> continuation = loader.loadClass("kotlin.coroutines.Continuation");
        Object emptyContext = loader.loadClass("kotlin.coroutines.EmptyCoroutineContext").getField("INSTANCE").get(null);
        Object suspended = loader.loadClass("kotlin.coroutines.intrinsics.IntrinsicsKt")
                .getMethod("getCOROUTINE_SUSPENDED").invoke(null);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Object> outcome = new AtomicReference<>();
        Object completion = Proxy.newProxyInstance(loader, new Class<?>[] {continuation}, (proxy, called, values) -> {
            if (called.getName().equals("getContext")) return emptyContext;
            if (called.getName().equals("resumeWith")) { outcome.set(values[0]); finished.countDown(); return null; }
            return null;
        });
        Object[] args = java.util.Arrays.copyOf(parameters, parameters.length + 1);
        args[parameters.length] = completion;
        Object value = method.invoke(receiver, args);
        if (value == suspended) {
            require(finished.await(10, TimeUnit.SECONDS), "Fixture operation timed out");
            value = outcome.get();
        }
        loader.loadClass("kotlin.ResultKt").getMethod("throwOnFailure", Object.class).invoke(null, value);
        return value;
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            require("ranchu".equals(Build.HARDWARE) &&
                    "true".equals(arguments.getString("allowCassiFixtureSeed")), "Dedicated emulator opt-in required");
            require((getTargetContext().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0,
                    "Seed the debug app before installing the optimized release");
            waitForIdleSync();
            loader = getTargetContext().getClassLoader();
            Class<?> continuation = loader.loadClass("kotlin.coroutines.Continuation");
            Object app = getTargetContext().getApplicationContext();
            Object repository = app.getClass().getMethod("getServerRepository").invoke(app);
            Object flow = repository.getClass().getMethod("getServersFlow").invoke(repository);
            List<?> sessions = (List<?>) suspend(loader.loadClass("kotlinx.coroutines.flow.FlowKt")
                    .getMethod("first", loader.loadClass("kotlinx.coroutines.flow.Flow"), continuation), null, flow);
            for (Object session : sessions) require("cassi-avatar-57-fixture".equals(
                    session.getClass().getMethod("getId").invoke(session)), "Existing user sessions must be preserved");
            Class<?> tokensClass = loader.loadClass("ru.genesiscorporation.workspace.beta.data.TokenPair");
            Object tokens = tokensClass.getConstructor(String.class, String.class)
                    .newInstance("cassi-avatar-57-access", "cassi-avatar-57-refresh");
            if (sessions.isEmpty()) {
                Class<?> configClass = loader.loadClass("ru.genesiscorporation.workspace.beta.data.ServerConfig");
                Object config = configClass.getConstructor(String.class, String.class, String.class,
                        String.class, boolean.class, String.class).newInstance("cassi-avatar-57-fixture",
                        "http://127.0.0.1:19557", "", "Avatar fixture", false, "project");
                suspend(repository.getClass().getMethod("addServer", configClass, tokensClass, continuation),
                        repository, config, tokens);
            }
            suspend(repository.getClass().getMethod("saveTokens", String.class, tokensClass, continuation),
                    repository, "cassi-avatar-57-fixture", tokens);
            suspend(repository.getClass().getMethod("setSelectedServerId", String.class, continuation),
                    repository, "cassi-avatar-57-fixture");
            // A force-stop deliberately bypasses Activity lifecycle flushing. Wait for
            // EncryptedSharedPreferences' pending apply() before replacing the APK.
            require(getTargetContext().getSharedPreferences("secure_tokens", 0).edit().commit(),
                    "Synthetic token persistence failed");
            Object saved = repository.getClass().getMethod("tokensFor", String.class)
                    .invoke(repository, "cassi-avatar-57-fixture");
            require(saved != null && "cassi-avatar-57-access".equals(
                    saved.getClass().getMethod("getAccessToken").invoke(saved)), "Synthetic token verification failed");
            result.putString("result", "PASS: synthetic avatar session saved");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("result", "FAIL");
            result.putString("failure", failure.toString());
            if (failure.getCause() != null) result.putString("cause", failure.getCause().toString());
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
