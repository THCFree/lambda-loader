import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.0"
    id("fabric-loom") version "1.14-SNAPSHOT"
    id("maven-publish")
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

val targetJavaVersion = 21
java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
    // if it is present.
    // If you remove this line, sources will not be generated.
    withSourcesJar()
}



repositories {
    // Add repositories to retrieve artifacts from in here.
    // You should only use this when depending on other mods because
    // Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
    // See https://docs.gradle.org/current/userguide/declaring_repositories.html
    // for more information about repositories.
}

dependencies {
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${project.property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${project.property("kotlin_loader_version")}")

    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", project.property("minecraft_version"))
    inputs.property("loader_version", project.property("loader_version"))
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version as Any,
            "minecraft_version" to project.property("minecraft_version") as Any,
            "loader_version" to project.property("loader_version") as Any,
            "kotlin_loader_version" to project.property("kotlin_loader_version") as Any
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    // ensure that the encoding is set to UTF-8, no matter what the system default is
    // this fixes some edge cases with special characters not displaying correctly
    // see http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
    // If Javadoc is generated, this must be specified in that task too.
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}

// configure the maven publication
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = project.property("maven_group") as String
            artifactId = "loader"
            version = project.version as String

            from(components["java"])

            pom {
                name.set("Lambda Loader")
                description.set("A Fabric mod loader for Lambda Client")
                url.set("https://github.com/lambda-client/Lambda-loader")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("lambda")
                        name.set("Lambda Client Team")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/lambda-client/Lambda-loader.git")
                    developerConnection.set("scm:git:ssh://github.com/lambda-client/Lambda-loader.git")
                    url.set("https://github.com/lambda-client/Lambda-loader")
                }
            }
        }
    }

    repositories {
        // Publish to local maven repository for testing
        mavenLocal()

        // Publish to remote maven repository
        // Set credentials via environment variables or gradle.properties:
        // MAVEN_URL, MAVEN_USERNAME, MAVEN_PASSWORD
        val mavenUrl = System.getenv("MAVEN_URL") ?: project.findProperty("mavenUrl") as String?
        val mavenUsername = System.getenv("MAVEN_USERNAME") ?: project.findProperty("mavenUsername") as String?
        val mavenPassword = System.getenv("MAVEN_PASSWORD") ?: project.findProperty("mavenPassword") as String?

        if (mavenUrl != null && mavenUsername != null && mavenPassword != null) {
            maven {
                name = "RemoteMaven"
                url = uri(mavenUrl)
                credentials {
                    username = mavenUsername
                    password = mavenPassword
                }
            }
        }
    }
}
