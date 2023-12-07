package com.projectronin.ehr.dataauthority.aidbox

import com.projectronin.interop.fhir.r4.datatype.primitive.Code
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.r4.resource.Bundle
import com.projectronin.interop.fhir.r4.resource.BundleEntry
import com.projectronin.interop.fhir.r4.resource.BundleRequest
import com.projectronin.interop.fhir.r4.resource.Resource
import com.projectronin.interop.fhir.r4.valueset.BundleType
import com.projectronin.interop.fhir.r4.valueset.HttpVerb

/**
 * Creates and returns a FHIR transaction bundle for posting to Aidbox using the AidboxClient batchUpsert() method.
 * @param aidboxURLRest The root URL for Aidbox FHIR REST calls.
 * @param resources The FHIR resources for the bundle.
 */
fun makeBundleForBatchUpsert(
    aidboxURLRest: String,
    resources: List<Resource<*>>,
): Bundle {
    return Bundle(
        id = null,
        type = Code(BundleType.TRANSACTION.code),
        entry = resources.map { makeBundleEntry(aidboxURLRest, HttpVerb.PUT, it) },
    )
}

/**
 * Creates and returns one [BundleEntry] within a FHIR transaction bundle for posting to Aidbox.
 * @param aidboxURLRest The root URL for Aidbox FHIR REST calls.
 * @param method The HTTP verb for the entry, i.e. PUT, DELETE, etc.
 * @param resource The FHIR resource for the entry. The id and resourceType values must be present.
 */
fun makeBundleEntry(
    aidboxURLRest: String,
    method: HttpVerb,
    resource: Resource<*>,
): BundleEntry {
    val fullReference = "/${resource.resourceType}/${resource.id?.value}"
    return BundleEntry(
        fullUrl = Uri("$aidboxURLRest$fullReference"),
        request = BundleRequest(method = Code(method.code), url = Uri(fullReference)),
        resource = resource,
    )
}
