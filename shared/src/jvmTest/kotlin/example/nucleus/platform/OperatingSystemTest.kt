package example.nucleus.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class OperatingSystemTest {

    @Test
    fun detectsCommonOperatingSystemNames() {
        assertEquals(OperatingSystem.WINDOWS, OperatingSystem.fromName("Windows 11"))
        assertEquals(OperatingSystem.LINUX, OperatingSystem.fromName("Linux"))
        assertEquals(OperatingSystem.LINUX, OperatingSystem.fromName("GNU/Linux"))
        assertEquals(OperatingSystem.MAC, OperatingSystem.fromName("Mac OS X"))
        assertEquals(OperatingSystem.OTHER, OperatingSystem.fromName("FreeBSD"))
    }

    @Test
    fun darwinIsMacEvenThoughItContainsWin() {
        assertEquals(OperatingSystem.MAC, OperatingSystem.fromName("Darwin"))
    }
}
