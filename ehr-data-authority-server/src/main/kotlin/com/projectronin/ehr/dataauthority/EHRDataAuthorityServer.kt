package com.projectronin.ehr.dataauthority

import com.projectronin.interop.kafka.spring.KafkaSpringConfig
import org.ktorm.database.Database
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import javax.sql.DataSource

/**
 * Main Spring Boot application for the EHR Data Authority server.
 */
@ComponentScan(
    basePackages = [
        "com.projectronin.ehr",
        "com.projectronin.interop.aidbox",
        "com.projectronin.interop.fhir.ronin",
        "com.projectronin.interop.validation",
        "com.projectronin.interop.datalake",
        "com.projectronin.interop.ehr"
    ]
)
@Import(KafkaSpringConfig::class)
@SpringBootApplication
class EHRDataAuthorityServer {
    @Bean
    fun database(dataSource: DataSource): Database = Database.connectWithSpringSupport(dataSource)
}

fun main(args: Array<String>) {
    runApplication<EHRDataAuthorityServer>(*args)
}
