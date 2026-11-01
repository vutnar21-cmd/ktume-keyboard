# Правила R8 / ProGuard для релизной сборки клавиатуры «Кӗске Тӳме» (org.chuvash.ktume)
# Jetpack Compose, Material 3 и DataStore поставляют собственные consumer-правила внутри AAR.

# Детерминированное переименование пакетов для воспроизводимых сборок (F-Droid Reproducible Builds)
-dontusemixedcaseclassnames
-repackageclasses ''

# Сохраняем номера строк в стектрейсах на случай диагностики крашей без раскрытия локальных путей
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
