package com.calltags.app.helpers

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.CallLog.Calls
import com.calltags.app.models.LoggedCall

/**
 * Our own copy of the call history plus the user-assigned labels.
 *
 * Calls are imported from the system call log (see [importSystemCallLog]) and kept here even if the
 * system log is later cleared, so labels never lose the call they belong to.
 */
class CallHistoryDb private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "call_history.db"
        private const val DB_VERSION = 1
        private const val LABEL_SEPARATOR = "\u001f"
        private const val DEFAULT_LIMIT = 1000
        private const val SUFFIX_MATCH_MIN_LENGTH = 7
        private const val SUFFIX_MATCH_LENGTH = 8

        @Volatile
        private var instance: CallHistoryDb? = null

        fun getInstance(context: Context): CallHistoryDb =
            instance ?: synchronized(this) {
                instance ?: CallHistoryDb(context).also { instance = it }
            }

        fun digitsOf(number: String) = number.filter { it.isDigit() }

        // a query is treated as a phone number only if it has nothing but dialable characters,
        // otherwise "topic 2" would match every number containing a 2
        fun looksLikeNumber(query: String) =
            query.any { it.isDigit() } && query.all { it.isDigit() || it in "+-() ./*#" }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE calls (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                number TEXT NOT NULL,
                digits TEXT NOT NULL,
                name TEXT NOT NULL DEFAULT '',
                date INTEGER NOT NULL,
                duration INTEGER NOT NULL DEFAULT 0,
                type INTEGER NOT NULL,
                UNIQUE (number, date)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX calls_date ON calls (date)")
        db.execSQL(
            """
            CREATE TABLE labels (
                call_id INTEGER NOT NULL REFERENCES calls (id) ON DELETE CASCADE,
                label TEXT NOT NULL COLLATE NOCASE,
                PRIMARY KEY (call_id, label)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX labels_label ON labels (label)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    /** Copies any calls from the system call log we don't have yet. Returns how many were added. */
    @SuppressLint("MissingPermission")
    fun importSystemCallLog(context: Context): Int {
        val db = writableDatabase
        val lastDate = db.rawQuery("SELECT COALESCE(MAX(date), 0) FROM calls", null).use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        }

        val projection = arrayOf(Calls.NUMBER, Calls.CACHED_NAME, Calls.DATE, Calls.DURATION, Calls.TYPE)
        val cursor = try {
            context.contentResolver.query(
                Calls.CONTENT_URI, projection, "${Calls.DATE} >= ?", arrayOf(lastDate.toString()), "${Calls.DATE} ASC"
            )
        } catch (_: SecurityException) {
            null
        } ?: return 0

        var added = 0
        db.beginTransaction()
        try {
            cursor.use {
                while (it.moveToNext()) {
                    val number = it.getString(0).orEmpty()
                    val values = ContentValues().apply {
                        put("number", number)
                        put("digits", digitsOf(number))
                        put("name", it.getString(1).orEmpty())
                        put("date", it.getLong(2))
                        put("duration", it.getInt(3))
                        put("type", it.getInt(4))
                    }
                    if (db.insertWithOnConflict("calls", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L) {
                        added++
                    }
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return added
    }

    /**
     * Calls matching [query] on the number, the cached name or any of their labels, newest first.
     * An empty query returns the latest calls.
     */
    fun search(query: String, limit: Int = DEFAULT_LIMIT): List<LoggedCall> {
        val q = query.trim()
        val where = StringBuilder()
        val args = ArrayList<String>()

        if (q.isNotEmpty()) {
            val like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
            where.append("WHERE c.id IN (SELECT call_id FROM labels WHERE label LIKE ? ESCAPE '\\')")
            where.append(" OR c.name LIKE ? ESCAPE '\\' OR c.number LIKE ? ESCAPE '\\'")
            args += listOf(like, like, like)

            val digits = digitsOf(q)
            if (looksLikeNumber(q) && digits.isNotEmpty()) {
                // for full numbers only the last digits matter, so country/area prefixes don't get in the way
                val needle = if (digits.length >= SUFFIX_MATCH_MIN_LENGTH) digits.takeLast(SUFFIX_MATCH_LENGTH) else digits
                where.append(" OR c.digits LIKE ?")
                args += "%$needle%"
            }
        }

        val sql = """
            SELECT c.id, c.number, c.name, c.date, c.duration, c.type,
                (SELECT group_concat(label, '$LABEL_SEPARATOR')
                    FROM (SELECT label FROM labels WHERE call_id = c.id ORDER BY rowid))
            FROM calls c
            $where
            ORDER BY c.date DESC
            LIMIT $limit
        """.trimIndent()

        return readableDatabase.rawQuery(sql, args.toTypedArray()).use {
            val result = ArrayList<LoggedCall>(it.count)
            while (it.moveToNext()) {
                result += LoggedCall(
                    id = it.getLong(0),
                    number = it.getString(1),
                    name = it.getString(2),
                    date = it.getLong(3),
                    duration = it.getInt(4),
                    type = it.getInt(5),
                    labels = it.getString(6)?.split(LABEL_SEPARATOR).orEmpty()
                )
            }
            result
        }
    }

    fun getLabels(callId: Long): List<String> =
        readableDatabase.rawQuery("SELECT label FROM labels WHERE call_id = ? ORDER BY rowid", arrayOf(callId.toString())).use {
            buildList { while (it.moveToNext()) add(it.getString(0)) }
        }

    /** Distinct labels previously given to any call from [number], most recent first. */
    fun getLabelsForNumber(number: String): List<String> {
        val digits = digitsOf(number)
        if (digits.isEmpty()) return emptyList()

        // long numbers are compared by their last digits so "0119..." and "+55119..." are the same
        val (condition, arg) = if (digits.length >= SUFFIX_MATCH_MIN_LENGTH) {
            "c.digits LIKE ?" to "%" + digits.takeLast(SUFFIX_MATCH_LENGTH)
        } else {
            "c.digits = ?" to digits
        }
        val sql = """
            SELECT l.label FROM labels l JOIN calls c ON c.id = l.call_id
            WHERE $condition
            GROUP BY l.label
            ORDER BY MAX(c.date) DESC
        """.trimIndent()
        return readableDatabase.rawQuery(sql, arrayOf(arg)).use {
            buildList { while (it.moveToNext()) add(it.getString(0)) }
        }
    }

    /** Every label ever used, most used first; handy for autocompletion. */
    fun getAllLabels(): List<String> =
        readableDatabase.rawQuery("SELECT label FROM labels GROUP BY label ORDER BY COUNT(*) DESC, label", null).use {
            buildList { while (it.moveToNext()) add(it.getString(0)) }
        }

    fun addLabel(callId: Long, label: String) {
        val clean = label.trim()
        if (clean.isEmpty()) return
        val values = ContentValues().apply {
            put("call_id", callId)
            put("label", clean)
        }
        writableDatabase.insertWithOnConflict("labels", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun removeLabel(callId: Long, label: String) {
        writableDatabase.delete("labels", "call_id = ? AND label = ?", arrayOf(callId.toString(), label))
    }

    fun deleteCall(callId: Long) {
        writableDatabase.delete("calls", "id = ?", arrayOf(callId.toString()))
    }
}
