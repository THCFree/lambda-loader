package com.lambda.lambdaLoader.config

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
            val json = configFile.readText()
            gson.fromJson(json, Config::class.java)
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
