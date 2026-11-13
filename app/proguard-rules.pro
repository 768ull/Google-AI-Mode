# The app uses Android framework APIs and Jetpack WebKit directly.
# Keep this file for app-specific rules as the project grows.

# MediaPipe neural text-to-speech is invoked through JNI.
-keep class com.google.mediapipe.tasks.audio.texttospeech.** { *; }
