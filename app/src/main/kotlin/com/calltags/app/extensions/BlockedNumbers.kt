package com.calltags.app.extensions

import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import android.provider.BlockedNumberContract.BlockedNumbers

// The system block list is only readable and writable by the default dialer, so these throw a
// SecurityException otherwise. Callers run them off the main thread and treat failures as "not blocked".

fun Context.isNumberBlocked(number: String): Boolean {
    return BlockedNumberContract.isBlocked(this, number) || getBlockedPatterns().any { number.matches(it) }
}

/** Entries containing `*` act as wildcards, e.g. `+1800*`. Android itself ignores these. */
private fun Context.getBlockedPatterns(): List<Regex> {
    val projection = arrayOf(BlockedNumbers.COLUMN_ORIGINAL_NUMBER)
    val selection = "${BlockedNumbers.COLUMN_ORIGINAL_NUMBER} LIKE '%*%'"
    return contentResolver.query(BlockedNumbers.CONTENT_URI, projection, selection, null, null)?.use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                val pattern = cursor.getString(0) ?: continue
                add(pattern.split("*").joinToString(".*") { Regex.escape(it) }.toRegex())
            }
        }
    }.orEmpty()
}

/**
 * [isNumberBlocked] for checking many numbers at once: the wildcard patterns are read only once and each
 * number is looked up only once. Unlike [isNumberBlocked] it never throws, numbers just count as not blocked.
 * Not thread safe, meant to be used for a single list load.
 */
class BlockedNumberChecker(private val context: Context) {
    private val patterns by lazy {
        try {
            context.getBlockedPatterns()
        } catch (_: SecurityException) {
            emptyList()
        }
    }
    private val cache = HashMap<String, Boolean>()

    fun isBlocked(number: String): Boolean {
        if (number.isEmpty()) return false
        return cache.getOrPut(number) {
            val listed = try {
                BlockedNumberContract.isBlocked(context, number)
            } catch (_: SecurityException) {
                // only the default phone app can read the block list
                false
            }
            listed || patterns.any { number.matches(it) }
        }
    }
}

/** Returns false (after telling the user) if the number could not be added. */
fun Context.addBlockedNumber(number: String): Boolean {
    val values = ContentValues().apply { put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number) }
    return try {
        contentResolver.insert(BlockedNumbers.CONTENT_URI, values)
        true
    } catch (e: SecurityException) {
        showErrorToast(e)
        false
    } catch (e: IllegalArgumentException) {
        showErrorToast(e)
        false
    }
}

/** Returns false (after telling the user) if the number could not be removed. */
fun Context.deleteBlockedNumber(number: String): Boolean {
    return try {
        BlockedNumberContract.unblock(this, number)
        true
    } catch (e: SecurityException) {
        showErrorToast(e)
        false
    }
}
