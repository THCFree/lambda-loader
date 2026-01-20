package com.lambda.loader

import com.lambda.loader.config.ConfigManager
import com.lambda.loader.config.ReleaseMode
import java.net.URI
import java.net.URL

/**
 * Version controller for Lambda-Loader self-updates.
 * Fetches loader updates from a separate Maven repository.
 */
class LoaderVersionController(
    cache: Cache = Cache(),
    /**
     * The maven repository URL for the loader artifacts.
     * Can be overridden for testing or custom repositories.
     */
    loaderMavenUrl: String = "https://maven.thcfree.dev"
) : BaseMavenVersionController(cache, versionMatchingEnabled = false) {

    override val mavenUrl: String = loaderMavenUrl
    override val releasesMetaUrl: URL =
        URI("$mavenUrl/releases/com/lambda/loader/maven-metadata.xml").toURL()
    override val snapshotMetaUrl: URL =
        URI("$mavenUrl/snapshots/com/lambda/loader/maven-metadata.xml").toURL()
    override val artifactPath: String = "com/lambda/loader"
    override val artifactName: String = "loader"

    /**
     * Loader doesn't need version matching - we always want the latest version
     */
    override fun getVersionToMatch(): String? = null

    /**
     * Use the loader-specific release mode from config
     */
    override fun getReleaseMode(): ReleaseMode = ConfigManager.config.loaderReleaseMode

    /**
     * Get the current loader version from the manifest or build properties.
     * This can be used to check if an update is available.
     */
    fun getCurrentLoaderVersion(): String? {
        return try {
            // Try to read version from package implementation version
            val version = this::class.java.`package`?.implementationVersion
            if (version != null) {
                logger.info("Current loader version: $version")
                return version
            }

            // Alternative: read from a properties file
            val props = this::class.java.classLoader.getResourceAsStream("loader.properties")
            if (props != null) {
                val properties = java.util.Properties()
                properties.load(props)
                return properties.getProperty("version")
            }

            logger.warning("Could not determine current loader version")
            null
        } catch (e: Exception) {
            logger.warning("Error reading loader version: ${e.message}")
            null
        }
    }

    /**
     * Check if an update is available by comparing current version with latest available.
     * Returns the latest version if an update is available, null otherwise.
     */
    fun checkForUpdate(): String? {
        return try {
            val currentVersion = getCurrentLoaderVersion()
            val latestVersion = when (getReleaseMode()) {
                ReleaseMode.STABLE -> checkReleasesVersion()
                ReleaseMode.SNAPSHOT -> checkSnapshotVersion()
            }

            if (latestVersion == null) {
                logger.warning("Could not fetch latest loader version")
                return null
            }

            if (currentVersion == null) {
                logger.info("Latest loader version available: $latestVersion")
                return latestVersion
            }

            if (currentVersion != latestVersion) {
                logger.info("Loader update available: $currentVersion -> $latestVersion")
                return latestVersion
            } else {
                logger.info("Loader is up to date: $currentVersion")
                return null
            }
        } catch (e: Exception) {
            logger.severe("Error checking for loader update: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}
