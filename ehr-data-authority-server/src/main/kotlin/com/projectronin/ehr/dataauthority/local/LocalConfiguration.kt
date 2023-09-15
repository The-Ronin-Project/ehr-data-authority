package com.projectronin.ehr.dataauthority.local

import com.projectronin.ehr.dataauthority.change.data.services.DataStorageService
import com.projectronin.ehr.dataauthority.change.data.services.ResourceHashDAOService
import com.projectronin.ehr.dataauthority.change.data.services.StorageMode
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * Local configuration profile, points to LocalStorage client - resource localStorage map and local resource hash map
 */
@Configuration
@Profile("local")
class LocalConfiguration {
    @Bean
    fun dataStorageService(): DataStorageService {
        return LocalStorageClient()
    }

    @Bean
    fun resourceHashDao(): ResourceHashDAOService {
        return LocalStorageMapHashDAO()
    }

    @Bean
    fun storageMode(): StorageMode = StorageMode.LOCAL
}
