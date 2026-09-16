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

rootProject.name = "MyStreamingApp"
include(":app")

// Temporarily comment out the includeBuild reference since streampack-core-src doesn't exist
// includeBuild("/home/carlo/streampack-core-src") {
//     dependencySubstitution {
//         substitute(module("io.github.thibaultbee.streampack:streampack-core"))
//             .using(project(":streampack-core"))
//     }
// }
