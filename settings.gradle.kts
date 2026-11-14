// Gradle ayar dosyası: hangi depoların kullanılacağını ve projenin hangi modüllerden
// oluştuğunu tanımlar. Blast Block üç katmanlı (üç modüllü) bir projedir:
//   :domain -> oyun kuralları (saf Java, Android'den bağımsız)
//   :data   -> kayıt katmanı (yarım oyun, rekor, ayarlar)
//   :app    -> sunum katmanı (ekranlar, çizim, ViewModel'ler)
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "BlastBlock"
include(":app")
include(":domain")
include(":data")
