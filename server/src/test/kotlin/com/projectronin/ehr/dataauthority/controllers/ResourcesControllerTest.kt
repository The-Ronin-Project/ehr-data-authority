
import com.projectronin.ehr.dataauthority.controllers.ModificationType
import com.projectronin.ehr.dataauthority.controllers.ResourcesController
import com.projectronin.interop.aidbox.AidboxPublishService
import com.projectronin.interop.fhir.r4.resource.Observation
import com.projectronin.interop.fhir.r4.resource.Patient
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class ResourcesControllerTest {

    private lateinit var resourcesController: ResourcesController
    private lateinit var aidboxPublishService: AidboxPublishService
    private val mockPatient = mockk<Patient> {
        every { resourceType } returns "Patient"
        every { id!!.value } returns "1"
    }
    private val mockObservation = mockk<Observation> {
        every { resourceType } returns "Observation"
        every { id!!.value } returns "2"
    }

    @BeforeEach
    fun setUp() {
        aidboxPublishService = mockk()
        resourcesController = ResourcesController(aidboxPublishService)
    }

    @Test
    fun `addNewResources should return a batch resource response for valid resources`() {
        every { aidboxPublishService.publish(any()) } returns true
        val response = resourcesController.addNewResources(listOf(mockPatient, mockObservation))
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals(2, body.succeeded.size)
        assertEquals(0, body.failed.size)
        assertEquals("Patient", body.succeeded[0].resourceType)
        assertEquals("1", body.succeeded[0].resourceId)
        assertEquals(ModificationType.CREATED, body.succeeded[0].modificationType)
        assertEquals("Observation", body.succeeded[1].resourceType)
        assertEquals("2", body.succeeded[1].resourceId)
        assertEquals(ModificationType.CREATED, body.succeeded[1].modificationType)
    }

    @Test
    fun `addNewResources should return a batch resource response for a mix of valid and invalid resources`() {
        every { aidboxPublishService.publish(any()) } returnsMany listOf(true, false)
        val response = resourcesController.addNewResources(listOf(mockPatient, mockObservation))
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals(1, body.succeeded.size)
        assertEquals(1, body.failed.size)
        assertEquals("Patient", body.succeeded[0].resourceType)
        assertEquals("1", body.succeeded[0].resourceId)
        assertEquals(ModificationType.CREATED, body.succeeded[0].modificationType)
        assertEquals("Observation", body.failed[0].resourceType)
        assertEquals("2", body.failed[0].resourceId)
        assertEquals("Error publishing to data store.", body.failed[0].error)
    }
}
