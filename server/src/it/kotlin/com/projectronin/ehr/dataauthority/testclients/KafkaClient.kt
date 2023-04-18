package com.projectronin.ehr.dataauthority.testclients

import com.projectronin.fhir.r4.Resource
import com.projectronin.interop.kafka.spring.AdminWrapper
import com.projectronin.interop.kafka.spring.KafkaBootstrapConfig
import com.projectronin.interop.kafka.spring.KafkaCloudConfig
import com.projectronin.interop.kafka.spring.KafkaConfig
import com.projectronin.interop.kafka.spring.KafkaPropertiesConfig
import com.projectronin.interop.kafka.spring.KafkaPublishConfig
import com.projectronin.interop.kafka.spring.KafkaRetrieveConfig
import com.projectronin.interop.kafka.spring.KafkaSaslConfig
import com.projectronin.interop.kafka.spring.KafkaSaslJaasConfig
import com.projectronin.interop.kafka.spring.KafkaSecurityConfig
import com.projectronin.kafka.RoninConsumer
import com.projectronin.kafka.RoninProducer
import com.projectronin.kafka.config.RoninConsumerKafkaProperties
import com.projectronin.kafka.config.RoninProducerKafkaProperties
import com.projectronin.kafka.data.RoninEvent
import com.projectronin.kafka.data.RoninEventResult
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import org.apache.kafka.common.ConsumerGroupState
import java.util.UUID
import kotlin.reflect.KClass

/**
 * Client responsible for managing the test Kafka environment.
 */
object KafkaClient {
    private val mutex = Mutex()
    private val logger = KotlinLogging.logger { }

    private val config = KafkaConfig(
        cloud = KafkaCloudConfig(
            vendor = "oci",
            region = "us-phoenix-1"
        ),
        bootstrap = KafkaBootstrapConfig(servers = "localhost:9092"),
        publish = KafkaPublishConfig(source = "ehr-data-authority-it"),
        retrieve = KafkaRetrieveConfig(groupId = "ehr-data-authority-it", serviceId = "ehr-data-authority"),
        properties = KafkaPropertiesConfig(
            security = KafkaSecurityConfig(protocol = "PLAINTEXT"),
            sasl = KafkaSaslConfig(
                mechanism = "GSSAPI",
                jaas = KafkaSaslJaasConfig("nothing")
            )
        )
    )
    private val adminClient = AdminWrapper(config).client

    private val jobsByResource = mutableMapOf<String, Job>()
    private val eventsByResource = mutableMapOf<String, MutableList<RoninEvent<*>>>()

    /**
     * Setups a monitor for [resourceName] represented by [resourceClass]. This ensures that a Kafka consumer is setup
     * and listening for events related to that resource during a test run.
     *
     * To avoid issues across tests, if you have monitored a resource, you should call [reset] after the completion of your test.
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun monitorResource(resourceName: String, resourceClass: KClass<out Resource>) {
        runBlocking {
            mutex.withLock {
                jobsByResource.computeIfAbsent(resourceName) {
                    logger.info { "Creating Job for $resourceName" }

                    val topic = "oci.us-phoenix-1.ehr-data-authority.$resourceName.v1"
                    val groupId = UUID.randomUUID().toString()

                    var received = false
                    val job = GlobalScope.launch {
                        val consumer = RoninConsumer(
                            topics = listOf(topic),
                            typeMap = mapOf(
                                "ronin.ehr-data-authority.$resourceName.create" to resourceClass,
                                "ronin.ehr-data-authority.$resourceName.update" to resourceClass,
                                "test" to String::class
                            ),
                            kafkaProperties = RoninConsumerKafkaProperties(
                                "bootstrap.servers" to config.bootstrap.servers,
                                "security.protocol" to config.properties.security.protocol,
                                "sasl.mechanism" to config.properties.sasl.mechanism,
                                "sasl.jaas.config" to config.properties.sasl.jaas.config,
                                "group.id" to groupId
                            )
                        )

                        // Launch a separate coroutine for processing so that we can monitor the state of the current thread
                        launch {
                            consumer.process {
                                logger.info { "Received event: $it" }
                                if (it.type == "test") {
                                    logger.info { "Test message received" }
                                    // This exists to verify that the consumer is processing messages correctly before
                                    // returning the control flow back to the caller.
                                    received = true
                                } else {
                                    runBlocking {
                                        mutex.withLock {
                                            logger.info { "Adding event to resource map" }
                                            eventsByResource.computeIfAbsent(resourceName) { mutableListOf() }.add(it)
                                        }
                                    }
                                }
                                RoninEventResult.ACK
                            }
                        }

                        // Iterate until the thread has been killed, at which point we stop the consumer and exit gracefully.
                        while (true) {
                            if (!isActive) {
                                logger.info { "Thread is no longer active. Stopping consumer" }
                                consumer.stop()
                                break
                            }
                        }
                    }

                    // Wait for the topic to be registered and the consumer group to be stable
                    while (true) {
                        val names = adminClient.listTopics().names().get()
                        if (names.any { it == topic }) {
                            val groups = adminClient.listConsumerGroups().valid().get()
                            if (groups.any {
                                it.groupId() == groupId && it.state().get() == ConsumerGroupState.STABLE
                            }
                            ) {
                                logger.info { "Topic and consumer group created" }
                                break
                            } else {
                                logger.info { "Topic found, but consumer group not found or not stable" }
                            }
                        } else {
                            logger.info { "Topic not yet found" }
                        }
                        Thread.sleep(100)
                    }

                    // Then send a test message to ensure the consumer is actually listening
                    val producer = RoninProducer(
                        topic = topic,
                        source = config.publish.source,
                        dataSchema = "schema",
                        kafkaProperties = RoninProducerKafkaProperties(
                            "bootstrap.servers" to config.bootstrap.servers,
                            "security.protocol" to config.properties.security.protocol,
                            "sasl.mechanism" to config.properties.sasl.mechanism,
                            "sasl.jaas.config" to config.properties.sasl.jaas.config
                        )
                    )
                    logger.info { "Sending test message" }
                    producer.send("test", "subject", "Test Data")

                    // Iterate until we have received the test message.
                    while (!received) {
                        logger.info { "Waiting for test message..." }
                        Thread.sleep(100)
                    }

                    logger.info { "Setting job" }
                    job
                }
            }
        }
    }

    /**
     * Resets the Kafka environment currently in use.
     */
    fun reset() {
        runBlocking {
            mutex.withLock {
                jobsByResource.forEach { (resource, job) ->
                    logger.info { "Stopping job for $resource" }
                    job.cancelAndJoin()
                }
                jobsByResource.clear()
                eventsByResource.clear()
            }
        }

        val consumerGroups = adminClient.listConsumerGroups().all().get()
        val groupIds = consumerGroups.map { it.groupId() }.toSet()
        adminClient.deleteConsumerGroups(groupIds)
        val topics = adminClient.listTopics().names().get()
        adminClient.deleteTopics(topics)
    }

    /**
     * Reads the currently processed events for [resourceName] since the last call to [reset] or [readEvents]. By calling
     * this method, you will reset the current queue of processed events.
     */
    fun readEvents(resourceName: String): List<RoninEvent<*>> {
        return runBlocking {
            mutex.withLock {
                val events = eventsByResource[resourceName] ?: mutableListOf()
                eventsByResource[resourceName] = mutableListOf()
                events.toList()
            }
        }
    }
}
