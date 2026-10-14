# Jitsi Meet SDK exposes these classes through JavaScript and JNI.
# Rules adapted from the SDK's official Android app ProGuard configuration:
# https://github.com/jitsi/jitsi-meet/blob/master/android/app/proguard-rules.pro
# Preserve bridge contracts while allowing the rest of the app to be optimized.
-keep,allowobfuscation @interface com.facebook.proguard.annotations.DoNotStrip
-keep,allowobfuscation @interface com.facebook.proguard.annotations.KeepGettersAndSetters
-keep @com.facebook.proguard.annotations.DoNotStrip class *
-keepclassmembers class * { @com.facebook.proguard.annotations.DoNotStrip *; }
-keep @com.facebook.proguard.annotations.DoNotStripAny class * { *; }
-keepclassmembers @com.facebook.proguard.annotations.KeepGettersAndSetters class * {
    void set*(***);
    *** get*();
}
-keep class * implements com.facebook.react.bridge.JavaScriptModule { *; }
-keep class * implements com.facebook.react.bridge.NativeModule { *; }
-keepclassmembers,includedescriptorclasses class * { native <methods>; }
-keepclassmembers class * { @com.facebook.react.uimanager.annotations.ReactProp <methods>; }
-keepclassmembers class * { @com.facebook.react.uimanager.annotations.ReactPropGroup <methods>; }
-keep,includedescriptorclasses class com.facebook.react.bridge.** { *; }
-keep,includedescriptorclasses class com.facebook.react.turbomodule.core.** { *; }
-keep class com.facebook.jni.** { *; }
-keep,allowobfuscation @interface com.facebook.yoga.annotations.DoNotStrip
-keep @com.facebook.yoga.annotations.DoNotStrip class *
-keepclassmembers class * { @com.facebook.yoga.annotations.DoNotStrip *; }
-keep class org.webrtc.** { *; }
-keep class org.jitsi.meet.** { *; }
-keep class com.facebook.react.devsupport.** { *; }
-keep public class com.horcrux.svg.** { *; }

# JEmoji reads these classes and their fields from serializedEmojis.ser.
# The resource's class descriptors are invisible to R8's reachability analysis.
-keep class net.fellbaum.jemoji.Emoji { *; }
-keep enum net.fellbaum.jemoji.EmojiGroup { *; }
-keep enum net.fellbaum.jemoji.EmojiSubGroup { *; }
-keep enum net.fellbaum.jemoji.Qualification { *; }

# Preserve source/line references for Play vitals crash retracing.
-keepattributes SourceFile,LineNumberTable
