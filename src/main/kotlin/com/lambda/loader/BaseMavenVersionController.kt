package com.lambda.loader

import com.lambda.loader.config.ConfigManager
import com.lambda.loader.config.ReleaseMode
import java.io.File
import java.net.URI
import java.net.URL
import java.util.logging.Logger
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.system.exitProcess

/**
 * Base class for version controllers that fetch artifacts from Maven repositories.
 * Provides common functionality for checking versions, downloading artifacts, and caching.
 */
abstract class BaseMavenVersionController(
    protected val cache: Cache = Cache(),
    protected val versionMatchingEnabled: Boolean = true
) {
    protected val logger: Logger = Logger.getLogger("Lambda-Loader")

    /**
     * Override this to specify which release mode to use.
     * By default, uses the client release mode for backwards compatibility.
     */
    protected open fun getReleaseMode(): ReleaseMode = ConfigManager.config.clientReleaseMode

    /**
     * The base Maven repository URL
     */
    abstract val mavenUrl: String

    /**
     * URL to the releases maven-metadata.xml
     */
    abstract val releasesMetaUrl: URL

    /**
     * URL to the snapshots maven-metadata.xml
     */
    abstract val snapshotMetaUrl: URL

    /**
     * The artifact group and name path (e.g., "com/lambda/lambda")
     */
    abstract val artifactPath: String

    /**
     * The artifact name (e.g., "lambda")
     */
    abstract val artifactName: String

    /**
     * Override this to provide version matching logic.
     * Return null to accept all versions (no filtering).
     */
    protected open fun getVersionToMatch(): String? = null

    protected fun checkReleasesVersion(): String? {
        return try {
            val xml = releasesMetaUrl.readText()
            parseLatestVersionForMinecraft(xml)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    protected fun checkSnapshotVersion(): String? {
        return try {
            val xml = snapshotMetaUrl.readText()
            parseLatestVersionForMinecraft(xml)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    protected open fun parseLatestVersionForMinecraft(xml: String): String? {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(xml.byteInputStream())

            // Get all versions
            val versionNodes = document.getElementsByTagName("version")
            val versions = mutableListOf<String>()

            for (i in 0 until versionNodes.length) {
                val version = versionNodes.item(i).textContent
                versions.add(version)
            }

            val versionToMatch = getVersionToMatch()

            if (ConfigManager.config.debug) {
                if (versionToMatch != null) {
                    logger.info("Target version: $versionToMatch")
                }
                logger.info("Available Maven versions: ${versions.joinToString(", ")}")
            }

            val matchingVersions = if (versionToMatch != null && versionMatchingEnabled) {
                // Filter versions for the target version
                versions.filter { version ->
                    // Extract MC version from artifact version (after +, before - or end)
                    val mcVersionInArtifact = version.substringAfter("+").substringBefore("-")

                    // Normalize both versions for comparison (remove extra dots)
                    val normalizedArtifactVersion = mcVersionInArtifact.replace(".", "")
                    val normalizedTargetVersion = versionToMatch.replace(".", "")

                    normalizedArtifactVersion == normalizedTargetVersion
                }
            } else {
                // No filtering, use all versions
                versions
            }

            if (matchingVersions.isEmpty()) {
                if (ConfigManager.config.debug) {
                    val versionMsg = versionToMatch?.let { "for version $it" } ?: ""
                    logger.warning("No ${artifactName} versions found $versionMsg")
                    logger.warning("Available versions: ${versions.joinToString(", ")}")
                }
                return null
            }

            // Get the latest matching version (last in the list)
            val latestVersion = matchingVersions.last()
            if (ConfigManager.config.debug) {
                val versionMsg = versionToMatch?.let { "for version $it" } ?: ""
                logger.info("Found latest ${artifactName} version $versionMsg: $latestVersion")
            }
            latestVersion
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    data class SnapshotInfo(
        val version: String,
        val timestamp: String,
        val buildNumber: String
    )

    protected fun getLatestSnapshotInfo(): SnapshotInfo? {
        return try {
            val version = checkSnapshotVersion() ?: return null
            val snapshotMetaUrl = URI("$mavenUrl/snapshots/$artifactPath/$version/maven-metadata.xml").toURL()
            val xml = snapshotMetaUrl.readText()
            parseSnapshotInfo(xml, version)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    protected fun parseSnapshotInfo(xml: String, version: String): SnapshotInfo? {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(xml.byteInputStream())

            val timestampNodes = document.getElementsByTagName("timestamp")
            val buildNumberNodes = document.getElementsByTagName("buildNumber")

            if (timestampNodes.length > 0 && buildNumberNodes.length > 0) {
                val timestamp = timestampNodes.item(0).textContent
                val buildNumber = buildNumberNodes.item(0).textContent
                SnapshotInfo(version, timestamp, buildNumber)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    protected fun getSnapshotJarUrl(): String? {
        val snapshotInfo = getLatestSnapshotInfo() ?: return null
        val baseVersion = snapshotInfo.version.replace("-SNAPSHOT", "")
        val timestamp = snapshotInfo.timestamp
        val buildNumber = snapshotInfo.buildNumber
        return "$mavenUrl/snapshots/$artifactPath/${snapshotInfo.version}/$artifactName-$baseVersion-$timestamp-$buildNumber.jar"
    }

    protected fun getSnapshotChecksumUrl(): String? {
        val jarUrl = getSnapshotJarUrl() ?: return null
        return "$jarUrl.md5"
    }

    protected fun getReleaseJarUrl(): String? {
        val version = checkReleasesVersion() ?: return null
        return "$mavenUrl/releases/$artifactPath/$version/$artifactName-$version.jar"
    }

    protected fun getReleaseChecksumUrl(): String? {
        val jarUrl = getReleaseJarUrl() ?: return null
        return "$jarUrl.md5"
    }

    protected fun getJarUrl(): String? {
        return when (getReleaseMode()) {
            ReleaseMode.STABLE -> {
                val releaseUrl = getReleaseJarUrl()
                if (releaseUrl == null) {
                    val versionMsg = getVersionToMatch()?.let { "for version $it" } ?: ""
                    logger.warning("No stable version found $versionMsg, falling back to snapshot")
                    getSnapshotJarUrl()
                } else {
                    releaseUrl
                }
            }
            ReleaseMode.SNAPSHOT -> getSnapshotJarUrl()
        }
    }

    protected fun getChecksumUrl(): String? {
        return when (getReleaseMode()) {
            ReleaseMode.STABLE -> {
                val releaseChecksumUrl = getReleaseChecksumUrl()
                if (releaseChecksumUrl == null) {
                    val versionMsg = getVersionToMatch()?.let { "for version $it" } ?: ""
                    logger.warning("No stable version checksum found $versionMsg, falling back to snapshot")
                    getSnapshotChecksumUrl()
                } else {
                    releaseChecksumUrl
                }
            }
            ReleaseMode.SNAPSHOT -> getSnapshotChecksumUrl()
        }
    }

    protected fun getCacheFileName(): String? {
        return when (getReleaseMode()) {
            ReleaseMode.STABLE -> {
                val version = checkReleasesVersion()
                if (version == null) {
                    val versionMsg = getVersionToMatch()?.let { "for version $it" } ?: ""
                    logger.warning("No stable cache filename found $versionMsg, falling back to snapshot")
                    val snapshotInfo = getLatestSnapshotInfo() ?: return null
                    val baseVersion = snapshotInfo.version.replace("-SNAPSHOT", "")
                    "$artifactName-$baseVersion-${snapshotInfo.timestamp}-${snapshotInfo.buildNumber}.jar"
                } else {
                    "$artifactName-$version.jar"
                }
            }

            ReleaseMode.SNAPSHOT -> {
                val snapshotInfo = getLatestSnapshotInfo() ?: return null
                val baseVersion = snapshotInfo.version.replace("-SNAPSHOT", "")
                "$artifactName-$baseVersion-${snapshotInfo.timestamp}-${snapshotInfo.buildNumber}.jar"
            }
        }
    }

    protected fun downloadChecksum(): String? {
        return try {
            val checksumUrl = getChecksumUrl() ?: return null
            URI(checksumUrl).toURL().readText().trim().split(" ").first()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    protected fun isLatestVersionCached(): Boolean {
        val fileName = getCacheFileName() ?: return false
        val expectedChecksum = downloadChecksum() ?: return false
        return cache.checkVersionChecksum(fileName, expectedChecksum)
    }

    protected fun downloadJar(): ByteArray? {
        return try {
            val jarUrl = getJarUrl() ?: return null
            if (ConfigManager.config.debug) {
                logger.info("Downloading JAR from: $jarUrl")
            }
            URI(jarUrl).toURL().readBytes()
        } catch (e: Exception) {
            logger.severe("Failed to download JAR: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    protected fun ensureLatestVersionCached(): Boolean {
        return try {
            // Check if already cached with valid checksum
            if (isLatestVersionCached()) {
                if (ConfigManager.config.debug) {
                    logger.info("Latest version is already cached with valid checksum")
                }
                return true
            }

            if (ConfigManager.config.debug) {
                logger.info("Latest version not cached or checksum invalid, downloading...")
            }

            // Get the file name for caching
            val fileName = getCacheFileName() ?: return false

            // Download the JAR
            val jarData = downloadJar() ?: run {
                logger.severe("Failed to download JAR")
                return false
            }

            // Download the expected checksum
            val expectedChecksum = downloadChecksum() ?: run {
                logger.severe("Failed to download checksum")
                return false
            }

            // Verify downloaded data matches checksum
            val actualChecksum = cache.checksumBytes(jarData)
            if (actualChecksum != expectedChecksum) {
                logger.severe("Checksum mismatch! Expected: $expectedChecksum, Got: $actualChecksum")
                return false
            }

            // Cache the verified JAR
            cache.cacheVersion(fileName, jarData)
            if (ConfigManager.config.debug) {
                logger.info("Successfully cached version: $fileName")
            }

            // Verify it was cached correctly
            val verified = cache.checkVersionChecksum(fileName, expectedChecksum)
            if (ConfigManager.config.debug) {
                if (verified) {
                    logger.fine("Cache verification successful")
                } else {
                    logger.warning("Cache verification failed")
                }
            }
            verified
        } catch (e: Exception) {
            logger.severe("Error ensuring latest version cached: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Ensures the latest version for the current release mode is cached and returns the JAR file.
     * Automatically checks if cached, validates checksum, and downloads if needed.
     *
     * @return The cached JAR file, or null if download/caching failed
     */
    fun getOrDownloadLatestVersion(): File? {
        return try {
            // Ensure the latest version is cached
            if (!ensureLatestVersionCached()) {
                // Check if both stable and snapshot versions are unavailable
                val stableVersion = checkReleasesVersion()
                val snapshotVersion = checkSnapshotVersion()

                if (stableVersion == null && snapshotVersion == null) {
                    val versionMsg = getVersionToMatch()?.let { "for version $it" } ?: ""
                    logger.severe("═══════════════════════════════════════════════════════════")
                    logger.severe("FATAL ERROR: No ${artifactName} version found!")
                    if (versionMsg.isNotEmpty()) {
                        logger.severe("Target version: $versionMsg")
                    }
                    logger.severe("Neither STABLE nor SNAPSHOT versions are available.")
                    logger.severe("Please check:")
                    logger.severe("  1. Your internet connection")
                    logger.severe("  2. Maven repository availability at: $mavenUrl")
                    logger.severe("  3. If ${artifactName} supports the required version")
                    logger.severe("═══════════════════════════════════════════════════════════")
                } else {
                    logger.severe("Failed to ensure latest version is cached")
                }
                return null
            }

            // Get the cached filename
            val fileName = getCacheFileName()
            if (fileName == null) {
                logger.severe("Failed to get cache filename after successful caching")
                return null
            }

            // Get the cached version file
            val jarFile = cache.getCachedVersion(fileName)
            if (jarFile == null) {
                logger.severe("JAR file does not exist after caching: $fileName")
                exitProcess(1)
            }

            if (ConfigManager.config.debug) {
                logger.info("Latest version ready: ${jarFile.absolutePath}")
            }
            jarFile
        } catch (e: Exception) {
            logger.severe("Failed to get or download latest version: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}
