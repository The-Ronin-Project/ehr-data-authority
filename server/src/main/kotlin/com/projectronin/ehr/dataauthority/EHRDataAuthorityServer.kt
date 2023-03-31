package com.projectronin.ehr.dataauthority

import org.ktorm.database.Database
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import javax.sql.DataSource

/**
 * Main Spring Boot application for the EHR Data Authority server.
 */
@ComponentScan(
    basePackages = ["com.projectronin.ehr"]
)
@SpringBootApplication
class EHRDataAuthorityServer {
    @Bean
    fun database(dataSource: DataSource): Database = Database.connectWithSpringSupport(dataSource)
}

fun main(args: Array<String>) {
    runApplication<EHRDataAuthorityServer>(*args)
}
