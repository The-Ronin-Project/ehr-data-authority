package com.projectronin.ehr.dataauthority.validation

import com.projectronin.interop.fhir.r4.resource.Resource
import com.projectronin.interop.fhir.ronin.validation.ValidationClient
import com.projectronin.interop.fhir.validate.ProfileValidator
import mu.KotlinLogging
import org.springframework.stereotype.Component

/**
 * Manages the process for performing validation and reporting issues within the EHR Data Authority.
 */
@Component
class ValidationManager(private val validationClient: ValidationClient, validatorMappings: List<ValidatorMapping<*>>) {
    private val logger = KotlinLogging.logger { }

    private val validatorsByResource = validatorMappings.associate { it.resourceClass to it.validator }

    /**
     * Validates the [resource] for the [tenantId]. Any resource that contains errors or warnings will be reported to the
     * Data Ingestion Validation Error Management Service. The response will indicate that the resource [PassedValidation]
     * or [FailedValidation].
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Resource<T>> validateResource(resource: Resource<T>, tenantId: String): ValidationResponse {
        val validator = validatorsByResource[resource::class] as? ProfileValidator<T>
            ?: return FailedValidation("No validator found for resource ${resource::class.simpleName}")

        val validation = validator.validate(resource as T)
        if (validation.hasIssues()) {
            logger.info { "Validation issues found for resource ${resource.resourceType}/${resource.id!!.value}" }
            validationClient.reportIssues(validation, resource, tenantId)
        }

        return if (validation.hasErrors()) {
            FailedValidation(validation.getErrorString()!!)
        } else {
            PassedValidation
        }
    }
}
