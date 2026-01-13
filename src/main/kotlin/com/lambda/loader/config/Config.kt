package com.lambda.loader.config

data class Config(
    val releaseMode: ReleaseMode = ReleaseMode.STABLE,
    val debug: Boolean = false
)

enum class ReleaseMode {
    STABLE,
    SNAPSHOT
}
