package com.projectronin.ehr.dataauthority.model

data class FoundResourceIdentifiers(
    val udpId: String,
    val identifiers: List<Identifier>
)
