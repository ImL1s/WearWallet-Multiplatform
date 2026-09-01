package com.cbstudio.wearwallet.presentation.biometric

import com.cbstudio.wearwallet.domain.biometric.BiometricAuthService
import com.cbstudio.wearwallet.core.domain.model.RiskLevel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BiometricAuthViewModelTest : KoinTest {

    private lateinit var viewModel: BiometricAuthViewModel
    private lateinit var biometricAuthService: BiometricAuthService
    
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        biometricAuthService = mockk(relaxed = true)
        
        startKoin {
            modules(module {
                single { biometricAuthService }
            })
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        stopKoin()
    }

    @Test
    fun `init should check biometric support`() = runTest {
        // Given
        every { biometricAuthService.isSupported() } returns true
        
        // When
        viewModel = BiometricAuthViewModel()
        
        // Then
        assertTrue(viewModel.uiState.value.sensorStatus["生物識別"] ?: false)
        assertEquals("生物識別可用", viewModel.uiState.value.statusMessage)
    }

    @Test
    fun `startAuthentication unsupported should fail immediately`() = runTest {
        // Given
        every { biometricAuthService.isSupported() } returns false
        viewModel = BiometricAuthViewModel()
        
        // When
        viewModel.startAuthentication()
        testScheduler.advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value
        assertEquals(AuthState.COMPLETED, state.authState)
        assertFalse(state.authResult?.isAuthenticated ?: true)
        assertEquals("生物識別不可用", state.statusMessage)
    }

    @Test
    fun `startAuthentication success flow`() = runTest {
        // Given
        every { biometricAuthService.isSupported() } returns true
        every { biometricAuthService.authenticate() } returns true
        
        viewModel = BiometricAuthViewModel()
        
        // When
        viewModel.startAuthentication()
        
        // Advance time for simulated progress (10 * 300ms + 5 * 200ms = 4000ms total + execution time)
        testScheduler.advanceTimeBy(5000)
        testScheduler.runCurrent()
        
        // Then
        val state = viewModel.uiState.value
        assertEquals(AuthState.COMPLETED, state.authState)
        assertTrue(state.authResult?.isAuthenticated ?: false)
        assertEquals("身份驗證成功", state.statusMessage)
    }

    @Test
    fun `startAuthentication failure flow`() = runTest {
        // Given
        every { biometricAuthService.isSupported() } returns true
        every { biometricAuthService.authenticate() } returns false
        
        viewModel = BiometricAuthViewModel()
        
        // When
        viewModel.startAuthentication()
        testScheduler.advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value
        assertEquals(AuthState.COMPLETED, state.authState)
        assertFalse(state.authResult?.isAuthenticated ?: true)
        assertTrue(state.statusMessage.contains("身份驗證失敗"))
    }
    
    @Test
    fun `cancelAuthentication should reset state`() = runTest {
        viewModel = BiometricAuthViewModel()
        viewModel.cancelAuthentication()
        testScheduler.advanceUntilIdle()
        
        assertEquals(AuthState.IDLE, viewModel.uiState.value.authState)
        assertEquals("認證已取消", viewModel.uiState.value.statusMessage)
    }
}
