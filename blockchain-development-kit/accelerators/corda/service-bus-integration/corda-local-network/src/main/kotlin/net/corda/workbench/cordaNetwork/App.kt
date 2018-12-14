package net.corda.workbench.cordaNetwork

import net.corda.workbench.commons.event.FileEventStore
import net.corda.workbench.commons.registry.Registry
import org.http4k.server.Jetty
import org.http4k.server.asServer

fun main(args: Array<String>) {

    val registry = Registry()
    val dataDir = System.getProperty("user.home") + "/.corda-transaction-builder/events"
    val es = FileEventStore().load(dataDir)
    registry.store(es)

    val server =  WebController2(registry).asServer(Jetty(1115)).start()
    println ("$server started!")
    


    Javalin(1114).init(registry)
}