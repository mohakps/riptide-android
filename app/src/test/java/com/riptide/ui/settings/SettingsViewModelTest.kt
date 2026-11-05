package com.riptide.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsViewModelTest {
    @Test
    fun testUpdateDeviceNameTrims() {
        val vm = SettingsViewModel()
        vm.setDeviceName("  Nothing Phone (2)  ")
        assertEquals("Nothing Phone (2)", vm.deviceName.value)
    }
}
