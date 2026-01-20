package com.lambda.loader.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

object ConfigManager {

    private val configFile: File = File("lambda", "loader.json")
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    var config: Config = loadConfig()
        private set

    private fun loadConfig(): Config {
        return if (configFile.exists()) {
            try {
                val json = configFile.readText()
                val loadedConfig = gson.fromJson(json, Config::class.java)

                // Merge with defaults to handle new fields
                val defaultConfig = Config()
                val mergedConfig = Config(
                    clientReleaseMode = loadedConfig?.clientReleaseMode ?: defaultConfig.clientReleaseMode,
                    loaderReleaseMode = loadedConfig?.loaderReleaseMode ?: defaultConfig.loaderReleaseMode,
                    debug = loadedConfig?.debug ?: defaultConfig.debug
                )

                // Save merged config to add any new fields
                saveConfig(mergedConfig)
                mergedConfig
            } catch (e: Exception) {
                // If parsing fails, use defaults
                val defaultConfig = Config()
                saveConfig(defaultConfig)
                defaultConfig
            }
        } else {
            val defaultConfig = Config()
            saveConfig(defaultConfig)
            defaultConfig
        }
    }

    fun saveConfig(config: Config) {
        if (!configFile.parentFile.exists()) {
            configFile.parentFile.mkdirs()
        }
        val json = gson.toJson(config)
        configFile.writeText(json)
        this.config = config
    }
}
