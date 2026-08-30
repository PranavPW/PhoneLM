# PhoneLM proguard rules
#
# POLICY: release builds are currently NON-minified (isMinifyEnabled=false in
# app/build.gradle.kts), so these rules are inert today. They exist so that:
#   1) the missing-file reference in build.gradle.kts does not break release config;
#   2) when minification is enabled (M5), the JNI boundary is protected:
#      com.phonelm.core.LlamaEngine's native methods are looked up by name from
#      NativeBridge.cpp — renaming them breaks System.loadLibrary("phonelm") at runtime.
# Keep-rules policy for M5: keep com.phonelm.core.LlamaEngine and any classes
# accessed from JNI; keep ONNX Runtime and ObjectBox defaults per their docs.
