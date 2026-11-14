package com.calltags.app.extensions

import android.content.Context
import android.provider.BlockedNumberContract
import android.provider.BlockedNumberContract.BlockedNumbers
import com.calltags.app.R
import com.calltags.app.helpers.CallHistoryDb
import com.calltags.app.models.LabelProperties

/**
 * Blocking used to go through the system block list, now it is a label property. This moves every number on the
 * system list to a "Blocked" number label that blocks calls, and empties the list, so that removing the label
 * really unblocks. Wildcard entries like `+1800*` can't be a label on a number and are dropped.
 *
 * Only the default dialer can read the list, so this returns false until it can be done; it's safe to run again.
 */
fun Context.migrateSystemBlockList(): Boolean {
    if (config.blockListMigrated) return true
    val db = CallHistoryDb.getInstance(this)
    val label = getString(R.string.blocked)
    db.setLabelProperties(LabelProperties(label, suggest = true, block = true), onlyIfNew = true)

    val numbers = try {
        contentResolver.query(
            BlockedNumbers.CONTENT_URI, arrayOf(BlockedNumbers.COLUMN_ORIGINAL_NUMBER), null, null, null
        )?.use { cursor ->
            buildList { while (cursor.moveToNext()) cursor.getString(0)?.let(::add) }
        }.orEmpty()
    } catch (_: SecurityException) {
        return false
    }

    numbers.filterNot { it.contains('*') }.forEach { db.addNumberLabel(it, label) }
    try {
        numbers.forEach { BlockedNumberContract.unblock(this, it) }
    } catch (_: SecurityException) {
        return false
    }
    config.blockListMigrated = true
    return true
}

/** The label that blocks calls at all hours, the one the block buttons give. Made if there is none. */
fun Context.getBlockingLabel(): String {
    val db = CallHistoryDb.getInstance(this)
    val blocking = db.getLabelProperties().values.filter { it.block && it.blockHours == null }
    val default = getString(R.string.blocked)
    blocking.firstOrNull { it.label.equals(default, ignoreCase = true) }?.let { return it.label }
    blocking.firstOrNull()?.let { return it.label }
    // "Blocked" may exist with blocking turned off or limited to some hours
    val existing = db.getLabelProperties()[default.lowercase()] ?: LabelProperties(default, suggest = true)
    db.setLabelProperties(existing.copy(block = true, blockHours = null))
    return existing.label
}
