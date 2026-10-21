# kotlinx.serialization: pertahankan serializer kelas backup (aturan library sudah
# mencakup sebagian besar; ini pengaman untuk companion serializer()).
-keepclassmembers @kotlinx.serialization.Serializable class id.abati.autocare.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class id.abati.autocare.** {
    kotlinx.serialization.KSerializer serializer(...);
}
