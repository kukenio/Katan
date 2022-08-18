package org.katan.service.unit.instance.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.model.PullResponseItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.LogManager
import org.katan.config.KatanConfig
import org.katan.event.EventScope
import org.katan.model.instance.InstanceStatus
import org.katan.model.instance.InstanceUpdateCode
import org.katan.model.instance.UnitInstance
import org.katan.model.net.Connection
import org.katan.model.net.NetworkException
import org.katan.model.unit.ImageUpdatePolicy
import org.katan.service.id.IdService
import org.katan.service.network.NetworkService
import org.katan.service.unit.instance.InstanceNotFoundException
import org.katan.service.unit.instance.UnitInstanceService
import org.katan.service.unit.instance.docker.model.DockerUnitInstanceImpl
import org.katan.service.unit.instance.repository.InstanceEntity
import org.katan.service.unit.instance.repository.UnitInstanceRepository
import kotlin.reflect.jvm.jvmName

/**
 * This implementation does not directly change the repository because the repository is handled by
 * the Docker events listener.
 */
internal class DockerUnitInstanceServiceImpl(
    private val idService: IdService,
    private val networkService: NetworkService,
    private val dockerClient: DockerClient,
    private val unitInstanceRepository: UnitInstanceRepository,
    private val config: KatanConfig,
    eventsDispatcher: EventScope
) : UnitInstanceService,
    CoroutineScope by CoroutineScope(
        SupervisorJob() +
            CoroutineName(DockerUnitInstanceServiceImpl::class.jvmName)
    ) {

    private companion object {
        private val logger = LogManager.getLogger(DockerUnitInstanceServiceImpl::class.java)

        private const val BASE_LABEL = "org.katan.instance."
    }

    init {
        DockerEventScope(dockerClient, eventsDispatcher, coroutineContext)
    }

    override suspend fun getInstance(id: Long): UnitInstance {
        // TODO cache service
        return unitInstanceRepository.findById(id)?.toDomain() ?: throw InstanceNotFoundException()
    }

    override suspend fun deleteInstance(instance: UnitInstance) {
        require(instance is DockerUnitInstanceImpl)

        // TODO fix coroutine scope of both runtime remove and repository delete
        unitInstanceRepository.delete(instance.id)
        withContext(IO) {
            dockerClient.removeContainerCmd(instance.containerId!!)
                .withRemoveVolumes(true)
                .withForce(true)
                .exec()
        }
    }

    private suspend fun startInstance(containerId: String, currentStatus: InstanceStatus) {
        check(!isRunning(currentStatus)) {
            "Unit instance is already running, cannot be started again, stop it first"
        }

        withContext(IO) {
            dockerClient.startContainerCmd(containerId).exec()
        }
    }

    private suspend fun stopInstance(containerId: String, currentStatus: InstanceStatus) {
        check(isRunning(currentStatus)) {
            "Unit instance is not running, cannot be stopped"
        }

        withContext(IO) {
            dockerClient.stopContainerCmd(containerId).exec()
        }
    }

    private suspend fun killInstance(containerId: String) {
        withContext(IO) {
            dockerClient.killContainerCmd(containerId).exec()
        }
    }

    private suspend fun restartInstance(instance: UnitInstance) {
        // container will be deleted so restart command will fail
        if (tryUpdateImage(instance.containerId!!, instance.updatePolicy)) {
            return
        }

        withContext(IO) {
            dockerClient.restartContainerCmd(instance.containerId!!).exec()
        }
    }

    private suspend fun tryUpdateImage(
        containerId: String,
        imageUpdatePolicy: ImageUpdatePolicy
    ): Boolean {
        // fast path -- ignore image update if policy is set to Never
        if (imageUpdatePolicy == ImageUpdatePolicy.Never) {
            return false
        }

        logger.debug("Trying to update container image")

        val inspect = withContext(IO) {
            dockerClient.inspectContainerCmd(containerId).exec()
        } ?: throw RuntimeException("Failed to inspect container: $containerId")

        val currImage = inspect.config.image ?: return false

        // fast path -- version-specific tag
        if (currImage.substringAfterLast(":") == "latest") {
            return false
        }

        logger.debug("Removing image \"$currImage\"...")
        withContext(IO) {
            dockerClient.removeImageCmd(currImage).exec()
        }

        pullContainerImage(currImage).collect {
            logger.info("Pulling image... $it")
        }
        return true
    }

    private suspend fun updateInstance(id: Long, status: InstanceStatus) {
        unitInstanceRepository.update(id) {
            this.status = status.value
        }
    }

    // TODO check for parameters invalid property types
    override suspend fun updateInternalStatus(
        instance: UnitInstance,
        code: InstanceUpdateCode
    ) {
        val containerId = requireNotNull(instance.containerId) {
            "Cannot update non-initialized instance container"
        }

        when (code) {
            InstanceUpdateCode.Start -> startInstance(
                containerId,
                instance.status
            )

            InstanceUpdateCode.Stop -> stopInstance(containerId, instance.status)
            InstanceUpdateCode.Restart -> restartInstance(instance)
            InstanceUpdateCode.Kill -> killInstance(containerId)
        }
    }

    override suspend fun createInstance(image: String, host: String?, port: Int?): UnitInstance {
        val instanceId = idService.generate()
        val name = generateContainerName(instanceId)

        // we'll try to create the container using the given image if the image is not available,
        // it will pull the image and then try to create the container again
        return try {
            val containerId = createContainer(instanceId, image, name)
            resumeCreateInstance(instanceId, containerId, host, port, InstanceStatus.Created)
        } catch (e: NotFoundException) {
            var status: InstanceStatus = InstanceStatus.ImagePullNeeded
            val instance = registerInstance(instanceId, status)

            logger.info("Preparing to pull image")
            pullImageAndUpdateInstance(instanceId, image) { status = it }
            logger.info("Image pulled!")

            val containerId = createContainer(instanceId, image, name)
            resumeCreateInstance(instanceId, containerId, host, port, status, instance)

            instance
        }
    }

    private suspend fun resumeCreateInstance(
        instanceId: Long,
        containerId: String,
        host: String?,
        port: Int?,
        status: InstanceStatus,
        fallbackInstance: UnitInstance? = null
    ): UnitInstance {
        var finalStatus: InstanceStatus = status
        logger.info("Connecting $instanceId to ${config.docker.network.name}...")

        val connection = try {
            networkService.connect(
                config.docker.network.name,
                config.docker.network.driver,
                containerId,
                host,
                port
            )
        } catch (e: NetworkException) {
            finalStatus = InstanceStatus.NetworkAssignmentFailed
            fallbackInstance?.let { updateInstance(it.id, finalStatus) }
            logger.error("Unable to connect the instance ($instanceId) to the network.", e)
            null
        }

        logger.info("Connected $instanceId to ${config.docker.network.name} @ $connection")

        // fallback instance can set if instance was not created asynchronously
        if (fallbackInstance == null) {
            return registerInstance(instanceId, finalStatus, containerId, connection)
        }

        return fallbackInstance
    }

    private suspend fun registerInstance(
        instanceId: Long,
        status: InstanceStatus,
        containerId: String? = null,
        connection: Connection? = null
    ): UnitInstance {
        val instance = DockerUnitInstanceImpl(
            id = instanceId,
            status = status,
            updatePolicy = ImageUpdatePolicy.Always,
            containerId = containerId,
            connection = connection
        )

        unitInstanceRepository.create(instance)
        return instance
    }

    private suspend fun pullImageAndUpdateInstance(
        instanceId: Long,
        image: String,
        onUpdate: (InstanceStatus) -> Unit
    ) = pullContainerImage(image).onStart {
        with(InstanceStatus.ImagePullInProgress) {
            onUpdate(this)
            updateInstance(instanceId, this)
        }
        logger.info("Image pull started")
    }.onCompletion { error ->
        val status =
            if (error == null) InstanceStatus.ImagePullCompleted else InstanceStatus.ImagePullFailed

        if (error != null) {
            logger.error("Failed to pull image.", error)
            error.printStackTrace()
        }

        onUpdate(status)
        updateInstance(instanceId, status)
        logger.info("Image pull completed")
    }.collect {
        logger.info("Pulling ($image): $it")
    }

    private fun generateContainerName(id: Long): String {
        return buildString {
            append("katan")
            append("-${config.nodeId}-")
            append(id)
        }
    }

    /**
     * Creates a Docker container using the given [image] suspending the coroutine until the
     * container creation workflow is completed.
     */
    private fun createContainer(instanceId: Long, image: String, name: String): String {
        logger.info("Creating container with ($image) to $instanceId...")
        return dockerClient.createContainerCmd(image)
            .withName(name)
            .withEnv(mapOf("EULA" to "true").map { (k, v) -> "$k=$v" })
            .withLabels(createDefaultContainerLabels(instanceId))
            .exec().id
    }

    private fun createDefaultContainerLabels(instanceId: Long): Map<String, String> {
        return mapOf(
            "id" to instanceId.toString()
        ).mapKeys { key -> BASE_LABEL + key }
    }

    /**
     * Pulls a Docker image from suspending the current coroutine until that image pulls completely.
     */
    private suspend fun pullContainerImage(image: String): Flow<String> {
        return callbackFlow {
            dockerClient.pullImageCmd(image)
                .exec(object : ResultCallback.Adapter<PullResponseItem>() {
                    override fun onNext(value: PullResponseItem) {
                        trySendBlocking(value.toString())
                            .onFailure {
                                // TODO handle downstream unavailability properly
                                logger.error("Downstream closed", it)
                            }
                    }

                    override fun onError(error: Throwable) {
                        cancel(CancellationException("Docker API error", error))
                    }

                    override fun onComplete() {
                        channel.close()
                    }
                })

            awaitClose()
        }
    }

    private fun isRunning(status: InstanceStatus): Boolean {
        return status == InstanceStatus.Running ||
            status == InstanceStatus.Restarting ||
            status == InstanceStatus.Stopping ||
            status == InstanceStatus.Paused
    }

    private suspend fun InstanceEntity.toDomain(): UnitInstance {
        return DockerUnitInstanceImpl(
            id = getId(),
            updatePolicy = ImageUpdatePolicy.getById(updatePolicy),
            containerId = containerId,
            status = toStatus(status),
            connection = networkService.createConnection(host, port?.toInt())
        )
    }

    private fun toStatus(value: String): InstanceStatus {
        return when (value.lowercase()) {
            "created" -> InstanceStatus.Created
            "network-assignment-failed" -> InstanceStatus.NetworkAssignmentFailed
            "unavailable" -> InstanceStatus.Unavailable
            "image-pull" -> InstanceStatus.ImagePullInProgress
            "image-pull-needed" -> InstanceStatus.ImagePullNeeded
            "image-pull-failed" -> InstanceStatus.ImagePullFailed
            "image-pull-completed" -> InstanceStatus.ImagePullCompleted
            "dead" -> InstanceStatus.Dead
            "paused" -> InstanceStatus.Paused
            "exited" -> InstanceStatus.Running
            "stopped" -> InstanceStatus.Stopping
            "starting" -> InstanceStatus.Removing
            "removing" -> InstanceStatus.Stopping
            "restarting" -> InstanceStatus.Restarting
            else -> InstanceStatus.Unknown
        }
    }
}
