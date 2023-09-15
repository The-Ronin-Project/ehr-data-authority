package com.projectronin.ehr.dataauthority.aidbox

import com.projectronin.ehr.dataauthority.aidbox.auth.AidboxAuthenticationBroker
import com.projectronin.ehr.dataauthority.aidbox.auth.AidboxAuthenticationService
import com.projectronin.ehr.dataauthority.aidbox.auth.AidboxCredentials
import com.projectronin.ehr.dataauthority.change.data.ResourceHashesDAO
import com.projectronin.ehr.dataauthority.change.data.services.DataStorageService
import com.projectronin.ehr.dataauthority.change.data.services.ResourceHashDAOService
import com.projectronin.ehr.dataauthority.change.data.services.StorageMode
import io.ktor.client.HttpClient
import org.ktorm.database.Database
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * Default configuration profile, points to Aidbox client and resource hash db
 */
@Configuration
@Profile("default")
class AidboxConfiguration {
    @Bean
    fun aidboxCredentials(
        @Value("\${aidbox.client.id}")
        clientId: String,
        @Value("\${aidbox.client.secret}")
        clientSecret: String
    ): AidboxCredentials {
        return AidboxCredentials(clientId, clientSecret)
    }

    @Bean
    fun aidboxAuthenticationService(
        httpClient: HttpClient,
        @Value("\${aidbox.url}")
        aidboxURLRest: String,
        aidboxCredentials: AidboxCredentials
    ): AidboxAuthenticationService {
        return AidboxAuthenticationService(httpClient, aidboxURLRest, aidboxCredentials)
    }

    @Bean
    fun aidboxAuthenticationBroker(
        aidboxAuthenticationService: AidboxAuthenticationService
    ): AidboxAuthenticationBroker {
        return AidboxAuthenticationBroker(aidboxAuthenticationService)
    }

    @Bean
    fun dataStorageService(
        httpClient: HttpClient,
        @Value("\${aidbox.url}")
        aidboxURLRest: String,
        authenticationBroker: AidboxAuthenticationBroker
    ): DataStorageService {
        return AidboxClient(httpClient, aidboxURLRest, authenticationBroker)
    }

    @Bean
    fun resourceHashDao(
        database: Database
    ): ResourceHashDAOService {
        return ResourceHashesDAO(database)
    }

    @Bean
    fun storageMode(): StorageMode = StorageMode.AIDBOX
}
