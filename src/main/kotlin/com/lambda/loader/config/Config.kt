package com.lambda.loader.config

data class Config(
    val clientReleaseMode: ReleaseMode = ReleaseMode.STABLE,
    val loaderReleaseMode: ReleaseMode = ReleaseMode.STABLE,
    val debug: Boolean = false
) {
    // Backwards compatibility: if using old code that accesses releaseMode
    @Deprecated("Use clientReleaseMode instead", ReplaceWith("clientReleaseMode"))
    val releaseMode: ReleaseMode
        get() = clientReleaseMode
}

enum class ReleaseMode {
    STABLE,
    SNAPSHOT
}
