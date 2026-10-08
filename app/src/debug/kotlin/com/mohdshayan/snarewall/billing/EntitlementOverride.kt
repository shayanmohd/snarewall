package com.mohdshayan.snarewall.billing

/**
 * Debug builds only, for screenshots and UI checks. Set it before the app starts (it is read once per
 * process, through the legacyOwner seam in ServiceLocator):
 *   adb shell setprop debug.snarewall.full 1    forces the full game open
 *   adb shell setprop debug.snarewall.full 0    forces the free tier
 *   adb shell setprop debug.snarewall.full ""   uses the real answer
 * A debug build carries the .debug package suffix, which Google Play never sells to, so its Play
 * entitlement is always locked and this answer alone decides. The release file always returns null.
 */
object EntitlementOverride {
    fun forced(): Boolean? = when (systemProperty("debug.snarewall.full").trim()) {
        "1" -> true
        "0" -> false
        else -> null
    }

    private fun systemProperty(key: String): String = try {
        Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java)
            .invoke(null, key) as String
    } catch (e: Exception) {
        ""
    }
}
