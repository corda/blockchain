package net.corda.workbench.transactionBuilder

import com.fasterxml.jackson.core.JsonProcessingException
import io.github.classgraph.ClassGraph
import io.github.classgraph.Resource
import net.corda.core.utilities.loggerFor
import org.slf4j.Logger
import java.nio.charset.StandardCharsets
import java.util.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.File
import kotlin.collections.HashMap

class CordaAppLoader {
    private val configs = ArrayList<CordaAppConfig>()

    companion object {
        private val logger: Logger = loggerFor<CordaAppLoader>()
    }

    /**
     * Scan CordApps for config files
     */
    fun scan(customerLoader: ClassLoader? = null): CordaAppLoader {
        configs.clear()

        val classGraph = ClassGraph()
        if (customerLoader != null) {
            classGraph.addClassLoader(customerLoader)
        }
        classGraph.whitelistPathsNonRecursive("META-INF/services")
                .scan()
                .use { scanResult ->
                    scanResult.getResourcesWithExtension("json")
                            .forEachByteArray { res: Resource,
                                                content: ByteArray ->
                                processJson(res.path, String(content, StandardCharsets.UTF_8), res.classpathElementFile)
                            }
                }
        return this
    }

    /**
     * All loaded files
     */
    fun allApps(): List<CordaAppConfig> {
        return configs
    }


    private fun processJson(fileName: String, json: String, sourceFile: File) {
        val mapper = ObjectMapper().registerModule(KotlinModule())

        if ("META-INF/services/net.corda.workbench.Registry.json" == fileName) {
            logger.info("Loading workbench config from ${sourceFile.name}")

            try {
                val config = mapper.readValue<CordaAppConfig>(json, CordaAppConfig::class.java)
                configs.add(config)
            } catch (jsonEx: JsonProcessingException) {
                logger.error("JsonProcessingException: ", jsonEx)
            } catch (ex: Exception) {
                logger.error("Exception: ", ex)
            }
        }
    }


}

/**
 * All the config that can be held in the JSON
 */
data class CordaAppConfig(val id: UUID,
                          val name: String,
                          val scannablePackages: List<String>,
                          val summary: String? = null,
                          val version: String? = null,
                          val slug: String? = null,
                          val slugs: List<String>? = null,
                          val authors: List<String> = emptyList(),
                          val url: String? = null)


