package com.projectronin.ehr.dataauthority.validation

sealed interface ValidationResponse

object PassedValidation : ValidationResponse

data class FailedValidation(
    val failureMessage: String,
) : ValidationResponse
