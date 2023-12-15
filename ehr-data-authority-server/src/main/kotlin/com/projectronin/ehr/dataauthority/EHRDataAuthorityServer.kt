package com.projectronin.ehr.dataauthority

import com.fasterxml.jackson.databind.ObjectMapper
import com.projectronin.interop.common.http.spring.HttpSpringConfig
import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.datalake.spring.DatalakeSpringConfig
import com.projectronin.interop.kafka.spring.KafkaSpringConfig
import com.projectronin.interop.rcdm.validate.spring.ValidationSpringConfig
import org.ktorm.database.Database
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import javax.sql.DataSource

/**
 * Main Spring Boot application for the EHR Data Authority server.
 */
@ComponentScan(
    basePackages = [
        "com.projectronin.ehr",
    ],
)
@Import(
    KafkaSpringConfig::class,
    HttpSpringConfig::class,
    DatalakeSpringConfig::class,
    ValidationSpringConfig::class,
)
@SpringBootApplication
@EnableTransactionManagement
class EHRDataAuthorityServer {
    @Bean
    fun database(dataSource: DataSource): Database = Database.connectWithSpringSupport(dataSource)

    @Bean
    fun transactionManager(dataSource: DataSource): PlatformTransactionManager = DataSourceTransactionManager(dataSource)

    @Primary
    @Bean
    fun objectMapper(): ObjectMapper = JacksonManager.nonAbsentObjectMapper
}

fun main(args: Array<String>) {
    runApplication<EHRDataAuthorityServer>(*args)
}
