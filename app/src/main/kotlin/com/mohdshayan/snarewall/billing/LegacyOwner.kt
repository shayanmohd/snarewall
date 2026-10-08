package com.mohdshayan.snarewall.billing

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.mohdshayan.snarewall.data.db.AppDatabase

/**
 * Snarewall 1.0.0 was a paid app. Play Billing cannot see who bought the app itself, so [settle]
 * decides from the install (PriorBuyerPolicy) and keeps the answer in shared_prefs/legacy_owner.xml:
 * `decided` and `owner`, where `owner` is a latch that no refund, Play answer or later build clears.
 * The file is backed up and transferred with the database (backup_rules.xml), and a save file can
 * never carry it (SaveCodec.sanitizeSettings keeps only known settings).
 */
object LegacyOwner {
    private const val FILE = "legacy_owner"
    private const val DECIDED = "decided"
    private const val OWNER = "owner"

    /**
     * App.onCreate calls this first, before ServiceLocator.init, so it sees 1.0.0's database before
     * Room can create this build's. Keep this check in every later build: a buyer may skip versions.
     */
    fun settle(context: Context) {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val record = PriorBuyerRecord(prefs.getBoolean(DECIDED, false), prefs.getBoolean(OWNER, false))
        if (record.owner) return
        val readFiles = PriorBuyerPolicy.PAID_UNTIL_MS == 0L && !record.decided
        val info = packageInfo(context)
        val facts = InstallFacts(
            databaseExists = readFiles && context.getDatabasePath(AppDatabase.NAME).exists(),
            firstInstallTime = info.firstInstallTime,
            lastUpdateTime = info.lastUpdateTime,
        )
        val next = PriorBuyerPolicy.next(record, facts)
        // commit(), not apply(): the answer is on disk before Room can create snarewall.db.
        if (next != record) prefs.edit().putBoolean(DECIDED, next.decided).putBoolean(OWNER, next.owner).commit()
    }

    /** The latch, read by UnlockRepository's legacyOwner seam. */
    fun isOwner(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(OWNER, false)

    private fun packageInfo(context: Context): PackageInfo {
        val pm = context.packageManager
        return if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
    }
}
