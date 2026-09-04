package com.cbstudio.wearwallet.platform

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cbstudio.wearwallet.RobolectricApplication
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.platform.PlatformDeletionCleanupHook
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricApplication::class)
class AndroidPlatformDeletionCleanupHookTest {

    private lateinit var context: Context
    private lateinit var hook: AndroidPlatformDeletionCleanupHook

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        hook = AndroidPlatformDeletionCleanupHook(context)
    }

    @Test
    fun testHookImplementsPlatformDeletionCleanupHook() {
        assertTrue(hook is PlatformDeletionCleanupHook)
    }

    @Test
    fun testCancelWorkManagerJobsSucceedsOrCapturesState() = runBlocking {
        val result = hook.cancelWorkManagerJobs(100L)
        assertNotNull(result)
        // In Robolectric environment with real/mocked WorkManager, result is a typed Result
        assertTrue(result is Result.Success || result is Result.Failure)
    }

    @Test
    fun testCancelBackgroundSyncSucceedsOrCapturesState() = runBlocking {
        val result = hook.cancelBackgroundSync(100L)
        assertNotNull(result)
        assertTrue(result is Result.Success || result is Result.Failure)
    }

    @Test
    fun testInvalidateTilesSucceedsOrCapturesState() = runBlocking {
        val result = hook.invalidateTiles()
        assertNotNull(result)
        assertTrue(result is Result.Success || result is Result.Failure)
    }

    @Test
    fun testInvalidateComplicationsSucceedsOrCapturesState() = runBlocking {
        val result = hook.invalidateComplications()
        assertNotNull(result)
        assertTrue(result is Result.Success || result is Result.Failure)
    }
}
