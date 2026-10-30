# Native speech bindings are called through JNI.
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }
