package net.corda.workbench.cordaNetwork.tasks

import net.corda.workbench.commons.taskManager.BaseTask
import net.corda.workbench.commons.taskManager.ExecutionContext
import net.corda.workbench.commons.taskManager.TaskContext
import net.corda.workbench.cordaNetwork.runCommand
import org.apache.logging.log4j.core.impl.ThrowableFormatOptions.FILE_NAME
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.channels.Channels
import java.nio.channels.ReadableByteChannel

/**
 * Runs the network bootstrapper, streaming results
 */
class NetworkBootstrapperTask(val ctx: TaskContext, private val version: String = "3.2") : BaseTask() {

    override fun exec(executionContext: ExecutionContext) {
        val downloadTask = NetworkBootstrapperDownloadTask(ctx, version)
        downloadTask.exec(executionContext)

        "java -jar ${downloadTask.fileName} ${ctx.workingDir}".runCommand()
    }

}
