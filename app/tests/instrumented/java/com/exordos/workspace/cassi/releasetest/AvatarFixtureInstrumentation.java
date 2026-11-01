package com.exordos.workspace.cassi.releasetest;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
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
            Class<?> tokensClass = loader.loadClass("com.exordos.workspace.data.TokenPair");
            Object tokens = tokensClass.getConstructor(String.class, String.class)
                    .newInstance("cassi-avatar-57-access", "cassi-avatar-57-refresh");
            if (sessions.isEmpty()) {
                Class<?> configClass = loader.loadClass("com.exordos.workspace.data.ServerConfig");
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
            if ("true".equals(arguments.getString("seedProtectedBodies"))) seedProtectedBodies();
            result.putString("result", "PASS: synthetic avatar session saved");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("result", "FAIL");
            result.putString("failure", failure.toString());
            if (failure.getCause() != null) result.putString("cause", failure.getCause().toString());
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    /** Uses the production crypto and secure store; exports ciphertext only for the host peer. */
    private void seedProtectedBodies() throws Exception {
        String prefix = "com.exordos.workspace.data.crypto.";
        String stream = "00000000-0000-4000-8000-000000000069";
        String key = "00000000-0000-4000-8002-000000000069";
        Class<?> ownerClass = loader.loadClass(prefix + "ChatKeyOwner");
        Class<?> scopeClass = loader.loadClass(prefix + "ChatKeyScope");
        Class<?> addressClass = loader.loadClass(prefix + "ChatKeyAddress");
        Object owner = ownerClass.getConstructor(String.class, String.class, String.class).newInstance(
                "http://127.0.0.1:19557", "project", "00000000-0000-4000-8000-000000000057");
        Object store = loader.loadClass(prefix + "EncryptedChatKeyStore")
                .getConstructor(android.content.Context.class).newInstance(getTargetContext());
        Object identity = store.getClass().getMethod("ensureDeviceIdentity", ownerClass, String.class)
                .invoke(store, owner, "CASSI fixture");
        Object scope = scopeClass.getConstructor(ownerClass, String.class).newInstance(owner,
                identity.getClass().getMethod("getDeviceId").invoke(identity));
        Object address = addressClass.getConstructor(scopeClass, String.class, String.class).newInstance(scope, stream, key);
        Object crypto = loader.loadClass(prefix + "WorkspaceChatCrypto").getConstructor().newInstance();
        byte[] groupKey = (byte[]) crypto.getClass().getMethod("createGroupKey").invoke(crypto);
        try {
            require(Boolean.TRUE.equals(store.getClass().getMethod("writeChatKey", addressClass,
                    byte[].class, boolean.class, Long.class, boolean.class)
                    .invoke(store, address, groupKey, true, null, false)), "Synthetic chat key persistence failed");
            Class<?> contextClass = loader.loadClass(prefix + "ChatMessageContext");
            Object context = contextClass.getConstructor(String.class, String.class).newInstance(stream, key);
            JSONArray payloads = new JSONArray();
            for (String text : new String[] {"Protected outgoing message", "Protected incoming message"}) {
                byte[] encrypted = (byte[]) crypto.getClass().getMethod("encryptMessage", byte[].class,
                        byte[].class, contextClass).invoke(crypto, groupKey, text.getBytes(StandardCharsets.UTF_8), context);
                payloads.put(new JSONObject().put("kind", "e2ee").put("key_uuid", key)
                        .put("content", android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP)));
            }
            File output = new File(getTargetContext().getExternalFilesDir(null), "protected-body-payloads.json");
            try (FileOutputStream file = new FileOutputStream(output)) {
                file.write(payloads.toString().getBytes(StandardCharsets.UTF_8));
            }
        } finally { java.util.Arrays.fill(groupKey, (byte) 0); }
    }
}
