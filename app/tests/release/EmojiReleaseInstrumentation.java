package com.exordos.workspace.cassi.releasetest;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.util.Map;

/** Loads the optimized app's real serialized resource and classes, without AndroidX test dependencies. */
public final class EmojiReleaseInstrumentation extends Instrumentation {
    // The complete serialized database shipped by the pinned JEmoji 1.7.6 dependency.
    private static final int EXPECTED_EMOJI_COUNT = 5225;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if ((getTargetContext().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                throw new AssertionError("Install the non-debuggable release APK before running this test");
            }
            final ClassLoader appClasses = getTargetContext().getClassLoader();
            InputStream resource = appClasses.getResourceAsStream("jemoji/serializedEmojis.ser");
            if (resource == null) throw new AssertionError("Bundled emoji database is missing");
            try (ObjectInputStream input = new ObjectInputStream(resource) {
                @Override protected Class<?> resolveClass(ObjectStreamClass descriptor)
                        throws ClassNotFoundException {
                    return Class.forName(descriptor.getName(), false, appClasses);
                }
            }) {
                Map<?, ?> emojis = (Map<?, ?>) input.readObject();
                if (emojis.size() != EXPECTED_EMOJI_COUNT) {
                    throw new AssertionError("Emoji database is incomplete: expected "
                            + EXPECTED_EMOJI_COUNT + ", got " + emojis.size());
                }
                Object grinning = emojis.get("😀");
                if (grinning == null) throw new AssertionError("Unicode emoji was not deserialized");
                Class<?> emojiClass = appClasses.loadClass("net.fellbaum.jemoji.Emoji");
                if (!"😀".equals(emojiClass.getMethod("getEmoji").invoke(grinning))) {
                    throw new AssertionError("Serialized emoji fields were not retained");
                }
                if (!((java.util.List<?>) emojiClass.getMethod("getGithubAliases").invoke(grinning))
                        .contains(":grinning:")) {
                    throw new AssertionError("Serialized emoji aliases were not retained");
                }
                for (String getter : new String[] {"getGroup", "getSubgroup", "getQualification"}) {
                    if (emojiClass.getMethod(getter).invoke(grinning) == null) {
                        throw new AssertionError("Serialized enum was not retained: " + getter);
                    }
                }
                result.putInt("emoji_count", emojis.size());
            }
            result.putString("result", "PASS: optimized emoji database and model graph");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("result", "FAIL");
            result.putString("failure", failure.toString());
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
