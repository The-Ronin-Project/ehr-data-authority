package com.projectronin.ehr.dataauthority.models

data class FoundResourceIdentifiers(
    val udpId: String,
    val identifiers: List<Identifier>,
)
