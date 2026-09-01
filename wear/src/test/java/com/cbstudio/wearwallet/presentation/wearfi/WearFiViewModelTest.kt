package com.cbstudio.wearwallet.presentation.wearfi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class WearFiViewModelTest {

    private lateinit var viewModel: WearFiViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = WearFiViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init should load empty data`() = runTest {
        testScheduler.advanceUntilIdle()
        
        val state = viewModel.challenges.value
        assertTrue(state.isEmpty())
        assertFalse(viewModel.isLoading.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `refresh should not change state in maintenance mode`() = runTest {
        viewModel.refresh()
        testScheduler.advanceUntilIdle()
        
        assertTrue(viewModel.challenges.value.isEmpty())
        assertFalse(viewModel.isLoading.value)
    }
    
    @Test
    fun `startChallenge and claimReward should be safe to call`() = runTest {
        // Assert no exception is thrown
        viewModel.startChallenge("123")
        viewModel.claimReward("456")
    }
}
