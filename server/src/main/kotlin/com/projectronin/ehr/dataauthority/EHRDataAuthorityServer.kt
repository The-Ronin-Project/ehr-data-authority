package com.projectronin.ehr.dataauthority

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

/**
 * Main Spring Boot application for the EHR Data Authority server.
 */
@ComponentScan(
    basePackages = ["com.projectronin.ehr"]
)
@SpringBootApplication
class EHRDataAuthorityServer

fun main(args: Array<String>) {
    runApplication<EHRDataAuthorityServer>(*args)
}
