package com.projectronin.ehr.dataauthority.models

import com.projectronin.interop.fhir.r4.datatype.Identifier as FhirIdentifier

/***
 *  One of several classes called identifier in interops, this one used solely within the Data Authority
 */
data class Identifier(
    val system: String,
    val value: String,
) {
    companion object {
        fun fromToken(token: String): Identifier {
            val split = token.split("|")
            assert(split.size == 2) { "'token' must be a string with one '|'" }
            return Identifier(split[0], split[1])
        }

        fun fromFhirIdentifiers(fhirIdentifiers: List<FhirIdentifier>): List<Identifier> {
            return fhirIdentifiers.mapNotNull {
                it.system?.value?.let { system ->
                    it.value?.value?.let { value ->
                        Identifier(system, value)
                    }
                }
            }
        }
    }

    fun toToken(): String {
        return "$system|$value"
    }
}
