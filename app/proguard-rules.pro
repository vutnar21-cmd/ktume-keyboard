# Правила R8 / ProGuard для релизной сборки клавиатуры «Кӗсье Тӳме» (org.chuvash.ktume)
# Jetpack Compose, Material 3 и DataStore поставляют собственные consumer-правила внутри AAR.

# Детерминированное переименование пакетов: одинаковый код даёт одинаковый APK
-dontusemixedcaseclassnames
-repackageclasses ''

# Сохраняем номера строк в стектрейсах на случай диагностики крашей без раскрытия локальных путей
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
