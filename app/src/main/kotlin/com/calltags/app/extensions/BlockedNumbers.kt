package com.calltags.app.extensions

import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import android.provider.BlockedNumberContract.BlockedNumbers

// The system block list is only readable and writable by the default dialer, so these throw a
// SecurityException otherwise. Callers run them off the main thread and treat failures as "not blocked".

fun Context.isNumberBlocked(number: String): Boolean {
    return BlockedNumberContract.isBlocked(this, number) || isNumberBlockedByPattern(number)
}

/** Entries containing `*` act as wildcards, e.g. `+1800*`. Android itself ignores these. */
private fun Context.isNumberBlockedByPattern(number: String): Boolean {
    val projection = arrayOf(BlockedNumbers.COLUMN_ORIGINAL_NUMBER)
    val selection = "${BlockedNumbers.COLUMN_ORIGINAL_NUMBER} LIKE '%*%'"
    contentResolver.query(BlockedNumbers.CONTENT_URI, projection, selection, null, null)?.use { cursor ->
        while (cursor.moveToNext()) {
            val pattern = cursor.getString(0) ?: continue
            val regex = pattern.split("*").joinToString(".*") { Regex.escape(it) }
            if (number.matches(regex.toRegex())) return true
        }
    }
    return false
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
