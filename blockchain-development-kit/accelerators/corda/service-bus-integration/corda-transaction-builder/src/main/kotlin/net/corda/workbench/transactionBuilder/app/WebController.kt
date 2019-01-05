package net.corda.workbench.transactionBuilder.app

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.options.MutableDataSet
import net.corda.core.contracts.UniqueIdentifier
import net.corda.workbench.commons.event.EventStore
import net.corda.workbench.commons.registry.Registry
import net.corda.workbench.commons.taskManager.*
import net.corda.workbench.transactionBuilder.clients.AgentClientFactory
import net.corda.workbench.transactionBuilder.clients.LocalNetworkClient
import net.corda.workbench.transactionBuilder.events.EventFactory
import net.corda.workbench.transactionBuilder.events.Repo
import net.corda.workbench.transactionBuilder.readFileAsText
import net.corda.workbench.transactionBuilder.tasks.DeployCordaAppTask
import net.corda.workbench.transactionBuilder.tasks.RealContext
import net.corda.workbench.transactionBuilder.tasks.StartAgentTask

import org.http4k.core.*
import org.http4k.core.body.form
import org.http4k.core.body.formAsMap
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import org.json.JSONArray

import org.json.JSONObject
import org.slf4j.Logger
import java.io.File
import java.io.FileInputStream
import java.util.*

class WebController2(private val registry: Registry) : HttpHandler {
    private val repo = Repo(registry.retrieve(EventStore::class.java))
//    private val processManager = registry.retrieve(ProcessManager::class.java)
//    private val config = registry.retrieve(AppConfig::class.java)

    private val localNetworkClient = registry.retrieve(LocalNetworkClient::class.java)
    private val agentClientFactory = registry.retrieve(AgentClientFactory::class.java)
    private val es = registry.retrieve(EventStore::class.java)

    // hardcoded to a single app for now
    //private val appName = "refrigerated-transportation"
    private val appName = "chat"


    // todo - should be injected in
    private val taskRepos = HashMap<String, TaskRepo>()


//    private val routes = routes(
//            "/" bind Method.GET to {
//                Response(Status.PERMANENT_REDIRECT).header("Location", "/web/home")
//            },
//            "/web" bind routes(
//                    "/home" bind Method.GET to {
//                        val networks = repo.networks()
//                        val page = renderTemplate("home.md",
//                                kotlin.collections.mapOf("networks" to networks))
//                        html(page)
//
//                    },
//
//                    "/style.css" bind Method.GET to {
//                        val css = java.io.FileInputStream("src/main/resources/www/style.css").bufferedReader().use { it.readText() }
//                        css(css)
//                    }
//            )
//
//    )

    private val routes: RoutingHttpHandler = routes(
            "/" bind Method.GET to {
                Response(Status.PERMANENT_REDIRECT).header("Location", "/web/home")
            },
            "/web" bind routes(
                    "/home" bind Method.GET to {
                        val page = renderTemplate("home.md")
                        html(page)

                    },
                    "/networks" bind routes(
                            "/join" bind Method.GET to {
                                val networks = localNetworkClient.networks()
                                val page = renderTemplate("networksJoin.md",
                                        mapOf("networks" to networks))
                                html(page)

                            },
                            "/{network}/join" bind Method.GET to { req ->
                                val network = req.path("network")!!

                                val page = renderTemplate("networkJoin.md",
                                        mapOf("networkName" to network))
                                html(page)
                            },
                            "/{network}/join" bind Method.POST to { req ->
                                val network = req.path("network")!!
                                val context = RealContext(network)
                                val executor = buildExecutor(context)

                                val nodes = localNetworkClient.nodes(network)
                                es.storeEvent(EventFactory.NETWORK_JOINED(network, nodes))

                                val apps = localNetworkClient.cordapps(network)
                                for (app in apps) {
                                    val name = app.name

                                    val data = localNetworkClient.downloadCordapp(network, name)
                                    val jarFile = File(tempDir(context) + "/" + name)
                                    jarFile.writeBytes(data)
                                    es.storeEvent(EventFactory.CORDAPP_DOWNLOAD(network, app.name, app.md5Hash))

                                    val deployTask = DeployCordaAppTask(registry.overide(context), jarFile, name.removeSuffix(".jar"))
                                    executor.exec(deployTask)
                                }

                                val startTask = StartAgentTask(registry.overide(context))
                                executor.exec(startTask)

                                val page = renderTemplate("networkJoined.md",
                                        mapOf("networkName" to network,
                                                "apps" to apps,
                                                "nodes" to repo.nodes(network)))
                                html(page)
                            },
                            "/{network}/nodes" bind Method.GET to { req ->
                                val network = req.path("network")!!
                                repo.nodes(network)

                                val page = renderTemplate("networkNodes.md",
                                        mapOf("networkName" to network,
                                                "nodes" to repo.nodes(network)))
                                html(page)
                            },
                            "/{network}/nodes/{node}" bind Method.GET to { req ->
                                val network = req.path("network")!!
                                val node = req.path("node")!!

                                val agentClient = agentClientFactory.createClient(network, node)

                                // todo - should be reading the list of apps

                                val page = renderTemplate("networkNode.md",
                                        mapOf("networkName" to network,
                                                "nodeName" to node,
                                                "appName" to appName,
                                                "flows" to agentClient.listFlows(appName),
                                                "states" to agentClient.listStates(appName)

                                        ))
                                html(page)
                            },
                            "/{network}/nodes/{node}/apps/{app}/states/{state}/all" bind Method.GET to { req ->
                                val network = req.path("network")!!
                                val node = req.path("node")!!
                                val state = req.path("state")!!
                                val app = req.path("app")!!
                                val agentClient = agentClientFactory.createClient(network, node)

                                val query = agentClient.queryState(appName, state)
                                json(JSONArray(query))
//
                            },
                            "/{network}/nodes/{node}/apps/{app}/flows/{flow}/metadata" bind Method.GET to { req ->
                                val network = req.path("network")!!
                                val node = req.path("node")!!
                                val flow = req.path("flow")!!
                                val app = req.path("app")!!
                                val agentClient = agentClientFactory.createClient(network, node)

                                val query = agentClient.flowMetaData(appName, flow)
                                println(query)

                                val metadata = JSONObject(query).toMap()

                                val page = renderTemplate("flowForm.md",
                                        mapOf("networkName" to network,
                                                "nodeName" to node,
                                                "appName" to app,
                                                "flowName" to flow,
                                                "metadata" to metadata.entries
                                        ))
                                html(page)


                                //j//son(JSONObject(query).toMap())
//
                            },
                            "/{network}/nodes/{node}/apps/{app}/flows/{flow}/run" bind Method.POST to { req ->
                                val network = req.path("network")!!
                                val node = req.path("node")!!
                                val flow = req.path("flow")!!
                                val app = req.path("app")!!

                                val agentClient = agentClientFactory.createClient(network, node)

                                // arg ugly - should be better encapsulated
                                val metadata = agentClient.flowMetaData(appName, flow)
                                val remapper = Remapper(metadata)
                                val rawData = req.formAsMap().mapValues { remapper.remap(it.key,it.value[0]!!)}
                                println(rawData)

                                val result = agentClient.runFlow(app,flow,rawData)

                               json(result as Map<String,Any>)

//
                            }


                    ),
                    "/style.css" bind Method.GET to {
                        val css = java.io.FileInputStream("src/main/resources/www/style.css").bufferedReader().use { it.readText() }
                        css(css)
                    }
            )
    )


    override fun invoke(p1: Request) = routes(p1)

    private fun html(page: String): Response {
        return Response(Status.OK)
                .body(page)
                .header("Content-Type", "text/html; charset=utf-8")

    }

    private fun css(page: String): Response {
        return Response(Status.OK)
                .body(page)
                .header("Content-Type", "text/css; charset=utf-8")

    }

    private fun text(page: String): Response {
        return Response(Status.OK)
                .body(page)
                .header("Content-Type", "text/plain; charset=utf-8")

    }

    private fun json(data: Map<String, Any>): Response {
        return Response(Status.OK)
                .body(JSONObject(data).toString(2))
                .header("Content-Type", "application/json; charset=utf-8")
    }

    private fun json(data: JSONArray): Response {
        return Response(Status.OK)
                .body(data.toString(2))
                .header("Content-Type", "application/json; charset=utf-8")

    }


    private fun notFound(message: String): Response {
        return Response(Status.NOT_FOUND)
                .body(message)
                .header("Content-Type", "text/plain; charset=utf-8")

    }

    private fun renderTemplate(path: String, params: Map<String, Any?> = emptyMap()): String {
        val html = renderMustache(path, params)

        // merge with layout.html.html
        val layout = FileInputStream("src/main/resources/www/layout.html").bufferedReader().use { it.readText() }
        val result = layout.replace("<!--BODYTEXT-->", html, false)
        //println(result)
        return result
    }


    private fun renderMustache(path: String, params: Map<String, Any?>): String {
        try {
            // mustache processing
            val content = readFileAsText("src/main/resources/www/$path", params)

            // markdown processing
            if (path.endsWith(".md")) {
                val options = MutableDataSet()
                val parser = Parser.builder(options).build()
                val renderer = HtmlRenderer.builder(options).build()
                val document = parser.parse(content)
                return renderer.render(document)
            } else {
                return content
            }
        } catch (ex: Exception) {
            return "<pre>" + ex.message!! + "</pre>"
        }
    }


    private fun buildMessageSink(context: TaskContext): ((TaskLogMessage) -> Unit) {
        val repo = taskRepos.getOrPut(context.networkName) {
            SimpleTaskRepo("${context.workingDir}/tasks")
        }
        return {
            try {
                repo.store(it)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }

    }

    private fun buildExecutor(context: TaskContext): BlockingTasksExecutor {
        val messageSink = buildMessageSink(context)
        return BlockingTasksExecutor(messageSink)
    }

    private fun tempDir(ctx: TaskContext): String {
        val tmpDir = "${ctx.workingDir}/tmp"
        File(tmpDir).mkdirs()
        return tmpDir
    }


    data class CreateNetworkRequest(val name: String, val organisations: List<String>)

    class Remapper(xx : Map<String,Any>){
        val metadata =  xx as Map<String,Map<String,Any>>

        fun remap(key: String, value : String) : Any {
            val expectedType = metadata[key]!!["type"] as String
            if (expectedType == "Int"){
                return value.toInt()
            }
            if (expectedType == "Long"){
                return value.toLong()
            }

            // todo - other basic types
//            if (expectedType == "UniqueIdentifier"){
//                return UniqueIdentifier(null, UUID.fromString(value))
//            }
            return value

        }
    }


}