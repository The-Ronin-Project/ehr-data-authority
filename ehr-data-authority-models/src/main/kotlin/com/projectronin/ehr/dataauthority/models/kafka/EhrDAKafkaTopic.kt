package com.projectronin.ehr.dataauthority.models.kafka

import com.projectronin.interop.fhir.r4.resource.Resource
import com.projectronin.interop.kafka.model.KafkaTopic
import kotlin.reflect.KClass

data class EhrDAKafkaTopic(
    override val systemName: String,
    override val topicName: String,
    override val dataSchema: String,
    val resourceClass: KClass<out Resource<*>>,
    val eventClass: KClass<out com.projectronin.fhir.r4.Resource>,
) : KafkaTopic {
    override val useLatestOffset: Boolean = false
}
