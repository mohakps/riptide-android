package com.riptide.core

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Smoke test: Robolectric boots and hands out a working Context.
 * Pinned to SDK 34, as in LoopbackTestBase: without it Robolectric
 * falls back to its default SDK 21. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RobolectricSmokeTest {
    @Test
    fun testApplicationContextWorks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertNotNull(context.filesDir)
        assertNotNull(context.getSystemService(Context.CONNECTIVITY_SERVICE))
    }
}
