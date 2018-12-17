package net.corda.workbench.cordaNetwork.web


import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.util.options.MutableDataSet
import com.vladsch.flexmark.parser.Parser
import io.javalin.Context
import net.corda.workbench.commons.event.EventStore

import net.corda.workbench.commons.registry.Registry
import net.corda.workbench.commons.taskManager.*
import net.corda.workbench.cordaNetwork.events.Repo
import net.corda.workbench.cordaNetwork.tasks.CreateNetworkTask
import net.corda.workbench.cordaNetwork.tasks.CreateNodesTask
import net.corda.workbench.cordaNetwork.tasks.RealContext
import net.corda.workbench.transactionBuilder.readFileAsText

import org.http4k.core.*
import org.http4k.core.body.formAsMap
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import org.json.JSONArray
import java.io.FileInputStream

class WebController2(private val registry: Registry) : HttpHandler {
    val repo = Repo(registry.retrieve(EventStore::class.java))

    // todo - should be injected in
    private val taskRepos = HashMap<String, TaskRepo>()


    private val routes = routes(
            "/" bind Method.GET to {
                Response(Status.PERMANENT_REDIRECT).header("Location", "/web/home")
            },
            "/web" bind routes(
                    "/home" bind Method.GET to {
                        val networks = repo.networks()
                        val page = renderTemplate("home.md",
                                mapOf("networks" to networks))
                        html(page)

                    },

                    "/networks/create" bind Method.GET to { req ->
                        val page = renderTemplate("createNetworkForm.html", emptyMap())
                        html(page)

                    },
                    "/networks/create" bind Method.POST to { req ->
                        val createRequest = unpackCreateNetworkForm(req)

                        val context = RealContext(createRequest.name)
                        val executor = buildExecutor(context)

                        executor.exec(CreateNetworkTask(registry.overide(context)))
                        executor.exec(CreateNodesTask(registry.overide(context), createRequest.organisations))

                        val page = renderTemplate("networkCreated.md",
                                mapOf("networkName" to createRequest.name))
                        html(page)

                    },
                    "/networks/{network}" bind Method.GET to { req ->
                        val network = req.path("network")!!
                        val nodes = repo.nodes(network)
                        val page = renderTemplate("network.md",
                                mapOf("nodes" to nodes, "name" to network))
                        html(page)

                    },
                    "/style.css" bind Method.GET to {
                        val css = FileInputStream("src/main/resources/www/style.css").bufferedReader().use { it.readText() }
                        css(css)
                    }


//                    "{network}/{app}" bind Method.GET to { req ->
//                        val network = req.path("network")!!
//                        val appName = req.path("app")!!
//                        val app = appRepo.findApp(network, appName)
//                        if (app != null) {
//                            val flows = FlowMetaDataExtractor("net.corda.workbench.refrigeratedTransportation").allFlows()
//
//                            val page = renderTemplate("appdetails.md",
//                                    mapOf("app" to app, "flows" to flows))
//                            html(page)
//
//                        } else {
//                            notFound("App $appName doesn't exist")
//                        }
//                    },
//                    "{network}/{app}/{flow}/{metadata}" bind Method.GET to { req ->
//                        val network = req.path("network")!!
//                        val appName = req.path("app")!!
//                        val app = appRepo.findApp(network, appName)
//
//                        if (app != null) {
//                            val flowName = req.path("flow")!!
//
//                            val params = FlowMetaDataExtractor("net.corda.workbench.refrigeratedTransportation")
//                                    .primaryConstructorMetaData(flowName)
//
//                            val page = renderTemplate("metadata.md",
//                                    mapOf("app" to app, "params" to params.entries))
//                            html(page)
//                        } else {
//                            notFound("App $appName doesn't exist")
//                        }
//
//                    },
//                    "{network}/{app}/{flow}/run" bind Method.POST to { req ->
//
//
//                        val flowName = req.path("flow")!!
//
//                        println(flowName)
//
//                        val shortFlowName = flowName.split(".").last()
//                        println("short flow is ${shortFlowName}")
//
//
//                        val nodeConfig = lookupNodeConfig(req)
//                        val helper = RPCHelper("corda-local-network:${nodeConfig.port}")
//                        helper.connect()
//                        val client = helper.cordaRPCOps()!!
//                        val resolver = RpcPartyResolver(helper)
//
//                        println("resolver: $resolver")
//
//                        // todo - fix to pass multiple packages
//                        val reporter = StringReporter()
//                        val runner = FlowRunner("net.corda.workbench.refrigeratedTransportation",
//                                resolver,
//                                LiveRpcCaller(client),
//                                reporter)
//
//                        println("runner: $runner")
//
//
//                        val data = req.formAsMap()
//
//
//                        val d1 = HashMap<String, Any>()
//                        for (x in data.entries) {
//                            println(x.key + " - " + x.value.size)
//
//                            val value = x.value[0]
//
//                            if (StringUtil.isNumeric(value)) {
//                                d1[x.key] = value!!.toInt()
//                            } else {
//                                d1[x.key] = value!!
//                            }
//                        }
//
//
//                        for (x in d1.entries) {
//                            println(x.key + " - " + x.value)
//                        }
//
//
//                        val result = runner.run<Any>(shortFlowName, d1)
//
//                        text(reporter.result)
//
//
//                    }

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
        println(result)
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


    private fun unpackCreateNetworkForm(req: Request): CreateNetworkRequest {
        try {
            val params = req.formAsMap()

            val name = params["networkName"]!!.single()!!
            val organisations = params["organisations"]!!
                    .single()!!
                    .split("\n")
                    .map { it.trim() }

            return CreateNetworkRequest(name, organisations + listOf("O=Notary,L=London,C=GB"))
        } catch (ex: Exception) {
            throw RuntimeException("Incorrect params passed to Create Network - '${ex.message}'", ex)
        }
    }


    data class CreateNetworkRequest(val name: String, val organisations: List<String>)


}