# Keep MediaPipe TTS entry points and their JNI-facing API.
-keep class com.google.mediapipe.tasks.audio.texttospeech.** { *; }

# LiteRT-LM setup serializes BaseOptions into MediaPipe protobuf messages.
# Protobuf reflection requires the original generated field names (including
# ExternalFile.fileContent_); R8 obfuscation changes them and breaks TTS setup.
-keep class com.google.mediapipe.tasks.core.** { *; }
-keep class com.google.mediapipe.calculator.proto.** { *; }
-keep class com.google.protobuf.** { *; }
