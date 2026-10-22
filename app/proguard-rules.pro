# Add project specific ProGuard rules here.
# ONNX Runtime — keep all ORT classes
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
