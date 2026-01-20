package com.lambda.loader

import net.fabricmc.loader.impl.FabricLoaderImpl
import java.net.URI
import java.net.URL

/**
 * Version controller for Lambda Client.
 * Fetches Lambda Client versions matching the current Minecraft version.
 */
class VersionController(
    cache: Cache = Cache(),
    private val minecraftVersionOverride: String? = null
) : BaseMavenVersionController(cache, versionMatchingEnabled = true) {

    override val mavenUrl: String = "https://maven.lambda-client.org"
    override val releasesMetaUrl: URL =
        URI("https://maven.lambda-client.org/releases/com/lambda/lambda/maven-metadata.xml").toURL()
    override val snapshotMetaUrl: URL =
        URI("https://maven.lambda-client.org/snapshots/com/lambda/lambda/maven-metadata.xml").toURL()
    override val artifactPath: String = "com/lambda/lambda"
    override val artifactName: String = "lambda"

    // Get the current Minecraft version from Fabric Loader or use override for testing
    private val minecraftVersion: String by lazy {
        minecraftVersionOverride ?: FabricLoaderImpl.INSTANCE.getGameProvider().rawGameVersion
    }

    /**
     * Returns the Minecraft version to match against artifact versions
     */
    override fun getVersionToMatch(): String = minecraftVersion
}
