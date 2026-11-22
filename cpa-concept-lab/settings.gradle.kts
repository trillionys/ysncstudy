pluginManagement {
    repositories {
        google()
        // Maven Central 미러(요청 제한 시 대비). 일반 환경에서는 mavenCentral() 만으로도 충분하다.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Maven Central 미러(요청 제한 시 대비). 일반 환경에서는 mavenCentral() 만으로도 충분하다.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}
rootProject.name = "CPAConceptLab"
include(":app")
