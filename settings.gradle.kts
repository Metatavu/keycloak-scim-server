import java.io.File

pluginManagement {
    repositories {
        val mirrorRepoUrl = providers
            .gradleProperty("mirrorRepoUrl")
            .orElse("https://azdops.serviceware.net/sw/Platform/_packaging/azdops/maven/v1")
            .get()

        var repoUser: String? = providers.gradleProperty("mirrorRepoUsername").orNull
        var repoPassword: String? = providers.gradleProperty("mirrorRepoPassword").orNull

        if (repoUser.isNullOrBlank() || repoPassword.isNullOrBlank()) {
            repoUser = System.getenv("AZURE_ARTIFACTS_USERNAME")
            repoPassword = System.getenv("AZURE_ARTIFACTS_PASSWORD")
        }

        if (repoUser.isNullOrBlank() || repoPassword.isNullOrBlank()) {
            val m2Settings = File(System.getProperty("user.home"), ".m2/settings.xml")
            if (m2Settings.exists()) {
                try {
                    val document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(m2Settings)
                    val xpath = javax.xml.xpath.XPathFactory.newInstance().newXPath()
                    val serverNode = xpath.evaluate("/*[local-name()='settings']/*[local-name()='servers']/*[local-name()='server'][*[local-name()='id']='azdops']", document, javax.xml.xpath.XPathConstants.NODE)
                    if (serverNode != null) {
                        repoUser = xpath.evaluate("*[local-name()='username']", serverNode).trim()
                        repoPassword = xpath.evaluate("*[local-name()='password']", serverNode).trim()
                    }
                } catch (_: Exception) {
                    // Keep credentials null and let Gradle fail with clear HTTP auth error if required.
                }
            }
        }

        mavenLocal()
        maven(url = uri(mirrorRepoUrl)) {
            name = "azdops"
            if (!repoUser.isNullOrBlank() && !repoPassword.isNullOrBlank()) {
                credentials {
                    username = repoUser
                    password = repoPassword
                }
                authentication {
                    create<BasicAuthentication>("basic")
                }
            }
        }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        val mirrorRepoUrl = providers
            .gradleProperty("mirrorRepoUrl")
            .orElse("https://azdops.serviceware.net/sw/Platform/_packaging/azdops/maven/v1")
            .get()

        var repoUser: String? = providers.gradleProperty("mirrorRepoUsername").orNull
        var repoPassword: String? = providers.gradleProperty("mirrorRepoPassword").orNull

        if (repoUser.isNullOrBlank() || repoPassword.isNullOrBlank()) {
            repoUser = System.getenv("AZURE_ARTIFACTS_USERNAME")
            repoPassword = System.getenv("AZURE_ARTIFACTS_PASSWORD")
        }

        if (repoUser.isNullOrBlank() || repoPassword.isNullOrBlank()) {
            val m2Settings = File(System.getProperty("user.home"), ".m2/settings.xml")
            if (m2Settings.exists()) {
                try {
                    val document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(m2Settings)
                    val xpath = javax.xml.xpath.XPathFactory.newInstance().newXPath()
                    val serverNode = xpath.evaluate("/*[local-name()='settings']/*[local-name()='servers']/*[local-name()='server'][*[local-name()='id']='azdops']", document, javax.xml.xpath.XPathConstants.NODE)
                    if (serverNode != null) {
                        repoUser = xpath.evaluate("*[local-name()='username']", serverNode).trim()
                        repoPassword = xpath.evaluate("*[local-name()='password']", serverNode).trim()
                    }
                } catch (_: Exception) {
                    // Keep credentials null and let Gradle fail with clear HTTP auth error if required.
                }
            }
        }

        mavenLocal()
        maven(url = uri(mirrorRepoUrl)) {
            name = "azdops"
            if (!repoUser.isNullOrBlank() && !repoPassword.isNullOrBlank()) {
                credentials {
                    username = repoUser
                    password = repoPassword
                }
                authentication {
                    create<BasicAuthentication>("basic")
                }
            }
        }
    }
}

include("test-event-listener")