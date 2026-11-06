package example.nucleus.platform

/**
 * Central OS detection, so the scattered `System.getProperty("os.name")` checks live in one place
 * and every platform-specific branch reads the same way.
 */
enum class OperatingSystem {
    WINDOWS,
    LINUX,
    MAC,
    OTHER;

    companion object {
        fun fromName(osName: String): OperatingSystem {
            val name = osName.lowercase()
            return when {
                // Darwin contiene "win": comprobar Mac antes que Windows.
                name.contains("mac") || name.contains("darwin") -> MAC
                name.contains("win") -> WINDOWS
                name.contains("linux") || name.contains("nix") || name.contains("nux") -> LINUX
                else -> OTHER
            }
        }
    }
}

object Platform {
    val current: OperatingSystem by lazy {
        OperatingSystem.fromName(System.getProperty("os.name").orEmpty())
    }

    val isWindows: Boolean get() = current == OperatingSystem.WINDOWS
    val isLinux: Boolean get() = current == OperatingSystem.LINUX
    val isMac: Boolean get() = current == OperatingSystem.MAC
}
