-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable

-keep class com.kutlusoy.tvsig.models.** { *; }
-keep class com.kutlusoy.tvsig.remote.RemoteSettings { *; }
-keep class com.kutlusoy.tvsig.repositories.OfflinePolicy$FileMeta { *; }
-keep class com.kutlusoy.tvsig.TVSigApp { *; }
-keep class com.kutlusoy.tvsig.services.** { *; }

-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

-keep class * implements androidx.viewbinding.ViewBinding {
    public static *** bind(android.view.View);
    public static *** inflate(android.view.LayoutInflater);
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
