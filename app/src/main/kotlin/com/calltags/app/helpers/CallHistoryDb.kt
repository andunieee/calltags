package com.calltags.app.helpers

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.CallLog.Calls
import com.calltags.app.models.CallLabel
import com.calltags.app.models.CallerHistory
import com.calltags.app.models.HourRange
import com.calltags.app.models.LabelProperties
import com.calltags.app.models.LabelUsage
import com.calltags.app.models.LoggedCall
import com.calltags.app.models.LoggedCall.Companion.PLACEHOLDER_TYPE
import com.calltags.app.models.Screening

/**
 * Our own copy of the call history plus the user-assigned labels.
 *
 * Calls are imported from the system call log (see [importSystemCallLog]) and kept here even if the
 * system log is later cleared, so labels never lose the call they belong to.
 *
 * Every label belongs to one call. Number labels (`number_label = 1`) also describe the number itself, see
 * [getNumberLabelIndex]; call labels only belong to their call.
 *
 * Number labels can have [LabelProperties], kept by label text in `label_properties`. A label without a row there
 * has the defaults.
 */
@Suppress("TooManyFunctions")
class CallHistoryDb private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "call_history.db"
        private const val DB_VERSION = 3
        private const val LABEL_SEPARATOR = "\u001f"
        private const val DEFAULT_LIMIT = 1000
        private const val DIALPAD_LIMIT = 50
        private const val DIALPAD_SCAN_LIMIT = 1000
        private const val SUFFIX_MATCH_MIN_LENGTH = 7
        private const val SUFFIX_MATCH_LENGTH = 8
        private const val RECENT_MILLIS = 30L * 24 * 60 * 60 * 1000

        // [suffixOf] in SQL, for the digits column of calls
        private const val SUFFIX_SQL = "(CASE WHEN length(digits) >= $SUFFIX_MATCH_MIN_LENGTH " +
            "THEN substr(digits, -$SUFFIX_MATCH_LENGTH) ELSE digits END)"

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

        // for full numbers only the last digits matter, so country/area prefixes don't get in the way
        private fun suffixOf(digits: String) =
            if (digits.length >= SUFFIX_MATCH_MIN_LENGTH) digits.takeLast(SUFFIX_MATCH_LENGTH) else digits
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
                number_label INTEGER NOT NULL DEFAULT 1,
                PRIMARY KEY (call_id, label)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX labels_label ON labels (label)")
        createLabelProperties(db)
    }

    private fun createLabelProperties(db: SQLiteDatabase) {
        // hours are minutes of the day, NULL for all day
        db.execSQL(
            """
            CREATE TABLE label_properties (
                label TEXT PRIMARY KEY COLLATE NOCASE,
                suggest INTEGER NOT NULL DEFAULT 0,
                block INTEGER NOT NULL DEFAULT 0,
                block_from INTEGER,
                block_to INTEGER,
                ring_only INTEGER NOT NULL DEFAULT 0,
                ring_only_from INTEGER,
                ring_only_to INTEGER,
                highlight INTEGER NOT NULL DEFAULT 0,
                hide INTEGER NOT NULL DEFAULT 0,
                color INTEGER
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // labels from before the distinction existed become number labels, the default for new ones too
            db.execSQL("ALTER TABLE labels ADD COLUMN number_label INTEGER NOT NULL DEFAULT 1")
        }
        if (oldVersion < 3) {
            // the system block list moves into a label separately, see migrateSystemBlockList
            createLabelProperties(db)
        }
    }

    /** Copies any calls from the system call log we don't have yet. Returns how many were added. */
    @SuppressLint("MissingPermission")
    fun importSystemCallLog(context: Context): Int {
        val db = writableDatabase
        // placeholders are dated when they were made, calls before that may still be missing
        val lastDate = db.rawQuery(
            "SELECT COALESCE(MAX(date), 0) FROM calls WHERE type != $PLACEHOLDER_TYPE", null
        ).use {
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
    fun search(query: String, limit: Int = DEFAULT_LIMIT, labeledOnly: Boolean = false): List<LoggedCall> {
        val q = query.trim()
        val conditions = ArrayList<String>()
        val args = ArrayList<String>()

        if (q.isNotEmpty()) {
            val like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
            val match = StringBuilder()
            match.append("c.id IN (SELECT call_id FROM labels WHERE label LIKE ? ESCAPE '\\')")
            match.append(" OR c.name LIKE ? ESCAPE '\\' OR c.number LIKE ? ESCAPE '\\'")
            args += listOf(like, like, like)

            val digits = digitsOf(q)
            if (looksLikeNumber(q) && digits.isNotEmpty()) {
                match.append(" OR c.digits LIKE ?")
                args += "%${suffixOf(digits)}%"
            }
            conditions += "($match)"
        }

        if (labeledOnly) {
            conditions += "EXISTS (SELECT 1 FROM labels WHERE call_id = c.id)"
        }

        val where = if (conditions.isEmpty()) "" else conditions.joinToString(" AND ", prefix = "WHERE ")

        val sql = """
            SELECT c.id, c.number, c.name, c.date, c.duration, c.type, c.digits,
                (SELECT group_concat(number_label || label, '$LABEL_SEPARATOR')
                    FROM (SELECT number_label, label FROM labels WHERE call_id = c.id ORDER BY rowid))
            FROM calls c
            $where
            ORDER BY c.date DESC
            LIMIT $limit
        """.trimIndent()

        val index = getNumberLabelIndex()
        return readableDatabase.rawQuery(sql, args.toTypedArray()).use {
            val result = ArrayList<LoggedCall>(it.count)
            while (it.moveToNext()) {
                // each label comes prefixed with its number_label flag, '1' or '0'
                val labels = it.getString(7)?.split(LABEL_SEPARATOR).orEmpty()
                    .map { encoded -> CallLabel(encoded.substring(1), isNumberLabel = encoded[0] == '1') }
                val digits = it.getString(6)
                val otherNumberLabels = if (digits.isEmpty()) emptyList() else {
                    index[suffixOf(digits)].orEmpty().filter { label ->
                        labels.none { own -> own.text.equals(label, ignoreCase = true) }
                    }
                }
                result += LoggedCall(
                    id = it.getLong(0),
                    number = it.getString(1),
                    name = it.getString(2),
                    date = it.getLong(3),
                    duration = it.getInt(4),
                    type = it.getInt(5),
                    labels = labels,
                    otherNumberLabels = otherNumberLabels,
                )
            }
            result
        }
    }

    /**
     * Maps each number, by its [suffixOf] digits, to the distinct number labels any of its calls got, most recent
     * first. Call labels are never part of it.
     *
     * It is derived from the labels table on every call rather than stored, so it can't go stale when labels
     * change. Labels are typed by hand, so there are few enough of them to read them all.
     */
    fun getNumberLabelIndex(): Map<String, List<String>> {
        val sql = """
            SELECT c.digits, l.label FROM labels l JOIN calls c ON c.id = l.call_id
            WHERE l.number_label = 1 AND c.digits != ''
            GROUP BY c.digits, l.label
            ORDER BY MAX(c.date) DESC
        """.trimIndent()
        val index = HashMap<String, MutableList<String>>()
        readableDatabase.rawQuery(sql, null).use {
            while (it.moveToNext()) {
                val labels = index.getOrPut(suffixOf(it.getString(0))) { ArrayList() }
                val label = it.getString(1)
                // the same number can be stored with different prefixes, so the same label may come twice
                if (labels.none { known -> known.equals(label, ignoreCase = true) }) labels += label
            }
        }
        return index
    }

    /**
     * Suggestions for what's being typed on the dialpad: one entry per number, newest first, holding its latest
     * call, with its own labels and the number labels of the other calls. The typed digits are matched against the number and, read as
     * [T9] letters, against the name and the labels.
     *
     * Runs on every keystroke, so only the latest [DIALPAD_SCAN_LIMIT] calls are looked at.
     * Numbers whose latest call [exclude] returns true for are left out.
     */
    fun searchDialpad(
        input: String,
        limit: Int = DIALPAD_LIMIT,
        exclude: (LoggedCall) -> Boolean = { false },
    ): List<LoggedCall> {
        val digits = digitsOf(input)
        if (digits.isEmpty()) return emptyList()
        val numberNeedle = suffixOf(digits)

        val byNumber = LinkedHashMap<String, LoggedCall>()
        for (call in search("", DIALPAD_SCAN_LIMIT)) {
            val callDigits = digitsOf(call.number)
            if (callDigits.isEmpty()) continue
            val key = suffixOf(callDigits)
            val latest = byNumber[key]
            byNumber[key] = latest?.copy(name = latest.name.ifEmpty { call.name }) ?: call
        }

        return byNumber.values.asSequence()
            .filter { call ->
                digitsOf(call.number).contains(numberNeedle) ||
                    T9.matches(call.name, digits) ||
                    call.labels.any { T9.matches(it.text, digits) } ||
                    call.otherNumberLabels.any { T9.matches(it, digits) }
            }
            .filterNot(exclude)
            .take(limit)
            .toList()
    }

    /** Distinct number labels previously given to any call from [number], most recent first. */
    fun getLabelsForNumber(number: String): List<String> {
        val digits = digitsOf(number)
        if (digits.isEmpty()) return emptyList()
        // long numbers are compared by their last digits so "0119..." and "+55119..." are the same
        return getNumberLabelIndex()[suffixOf(digits)].orEmpty()
    }

    /**
     * Sums up the earlier calls with [number], matched by [suffixOf] like labels are.
     * Returns null for an empty number, and a [CallerHistory] without [CallerHistory.lastCall] if there were none.
     */
    fun getCallerHistory(number: String, now: Long = System.currentTimeMillis()): CallerHistory? {
        val digits = digitsOf(number)
        if (digits.isEmpty()) return null

        val sql = """
            SELECT id, number, name, date, duration, type FROM calls
            WHERE $SUFFIX_SQL = ? AND type != $PLACEHOLDER_TYPE
            ORDER BY date DESC
        """.trimIndent()
        val calls = readableDatabase.rawQuery(sql, arrayOf(suffixOf(digits))).use {
            buildList {
                while (it.moveToNext()) {
                    add(
                        LoggedCall(
                            id = it.getLong(0),
                            number = it.getString(1),
                            name = it.getString(2),
                            date = it.getLong(3),
                            duration = it.getInt(4),
                            type = it.getInt(5),
                            labels = emptyList(),
                        )
                    )
                }
            }
        }

        val isMissed = { call: LoggedCall ->
            call.type == Calls.MISSED_TYPE || call.type == Calls.REJECTED_TYPE || call.type == Calls.VOICEMAIL_TYPE
        }
        val isTalk = { call: LoggedCall ->
            call.type == Calls.INCOMING_TYPE || call.type == Calls.ANSWERED_EXTERNALLY_TYPE ||
                (call.type == Calls.OUTGOING_TYPE && call.duration > 0)
        }
        val recent = calls.filter { now - it.date < RECENT_MILLIS }
        val recentOutgoing = recent.filter { it.type == Calls.OUTGOING_TYPE }
        return CallerHistory(
            lastCall = calls.firstOrNull(),
            missedSinceTalk = calls.takeWhile { !isTalk(it) }.count(isMissed),
            talkedEver = calls.any(isTalk),
            recentOutgoing = recentOutgoing.size,
            recentOutgoingUnanswered = recentOutgoing.count { it.duration == 0 },
            recentIncoming = recent.count { isMissed(it) || isTalk(it) && it.type != Calls.OUTGOING_TYPE },
        )
    }

    /** Every label ever used, most used first; handy for autocompletion. */
    fun getAllLabels(): List<String> =
        readableDatabase.rawQuery("SELECT label FROM labels GROUP BY label ORDER BY COUNT(*) DESC, label", null).use {
            buildList { while (it.moveToNext()) add(it.getString(0)) }
        }

    fun addLabel(callId: Long, label: String, isNumberLabel: Boolean = true) {
        val clean = label.trim()
        if (clean.isEmpty()) return
        val values = ContentValues().apply {
            put("call_id", callId)
            put("label", clean)
            put("number_label", if (isNumberLabel) 1 else 0)
        }
        writableDatabase.insertWithOnConflict("labels", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun removeLabel(callId: Long, label: String) {
        writableDatabase.delete("labels", "call_id = ? AND label = ?", arrayOf(callId.toString(), label))
    }

    /**
     * Deletes [call] from the history, and from the system call log so [importSystemCallLog] doesn't bring it back.
     * Its call labels go with it, but its number labels are moved to the number's latest remaining call (or a
     * placeholder) so the number keeps them.
     */
    fun deleteCall(context: Context, call: LoggedCall) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("calls", "id = ?", arrayOf(call.id.toString()))
            call.labels.filter { it.isNumberLabel }.forEach { addNumberLabel(call.number, it.text) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (call.type == PLACEHOLDER_TYPE) return
        try {
            context.contentResolver.delete(
                Calls.CONTENT_URI, "${Calls.DATE} = ? AND ${Calls.NUMBER} = ?", arrayOf(call.date.toString(), call.number)
            )
        } catch (_: SecurityException) {
            // not allowed to write the call log, the call may be imported again if it is the latest one
        }
    }

    /**
     * Gives [number] the number label [label], on its latest call. A number we never had a call with gets a
     * placeholder call to hold the label. Returns false for an empty number.
     */
    fun addNumberLabel(number: String, label: String, now: Long = System.currentTimeMillis()): Boolean {
        val digits = digitsOf(number)
        if (digits.isEmpty()) return false
        val db = writableDatabase
        val latest = db.rawQuery(
            "SELECT id FROM calls WHERE $SUFFIX_SQL = ? ORDER BY date DESC LIMIT 1", arrayOf(suffixOf(digits))
        ).use { if (it.moveToFirst()) it.getLong(0) else null }

        val callId = latest ?: db.insert("calls", null, ContentValues().apply {
            put("number", number)
            put("digits", digits)
            put("date", now)
            put("type", PLACEHOLDER_TYPE)
        })
        if (callId == -1L) return false
        addLabel(callId, label, isNumberLabel = true)
        return true
    }

    /** Takes the number label [label] off every call from [number], so the number no longer has it. */
    fun removeNumberLabel(number: String, label: String) {
        val digits = digitsOf(number)
        if (digits.isEmpty()) return
        writableDatabase.delete(
            "labels",
            "label = ? AND number_label = 1 AND call_id IN (SELECT id FROM calls WHERE $SUFFIX_SQL = ?)",
            arrayOf(label, suffixOf(digits))
        )
    }

    /** Properties of every label that has any, by lowercase label text. */
    fun getLabelProperties(): Map<String, LabelProperties> =
        readableDatabase.rawQuery("SELECT * FROM label_properties", null).use {
            buildMap {
                while (it.moveToNext()) {
                    val properties = readLabelProperties(it)
                    put(properties.label.lowercase(), properties)
                }
            }
        }

    private fun readLabelProperties(cursor: android.database.Cursor): LabelProperties {
        fun int(column: String) = cursor.getColumnIndexOrThrow(column).let { if (cursor.isNull(it)) null else cursor.getInt(it) }
        fun flag(column: String) = int(column) == 1
        fun hours(from: String, to: String): HourRange? {
            return HourRange(int(from) ?: return null, int(to) ?: return null)
        }
        return LabelProperties(
            label = cursor.getString(cursor.getColumnIndexOrThrow("label")),
            suggest = flag("suggest"),
            block = flag("block"),
            blockHours = hours("block_from", "block_to"),
            ringOnly = flag("ring_only"),
            ringOnlyHours = hours("ring_only_from", "ring_only_to"),
            highlight = flag("highlight"),
            hide = flag("hide"),
            color = int("color"),
        )
    }

    /** Saves [properties], replacing what its label had. With [onlyIfNew] a label that has properties is left alone. */
    fun setLabelProperties(properties: LabelProperties, onlyIfNew: Boolean = false) {
        val values = ContentValues().apply {
            put("label", properties.label)
            put("suggest", if (properties.suggest) 1 else 0)
            put("block", if (properties.block) 1 else 0)
            put("block_from", properties.blockHours?.from)
            put("block_to", properties.blockHours?.to)
            put("ring_only", if (properties.ringOnly) 1 else 0)
            put("ring_only_from", properties.ringOnlyHours?.from)
            put("ring_only_to", properties.ringOnlyHours?.to)
            put("highlight", if (properties.highlight) 1 else 0)
            put("hide", if (properties.hide) 1 else 0)
            put("color", properties.color)
        }
        val conflict = if (onlyIfNew) SQLiteDatabase.CONFLICT_IGNORE else SQLiteDatabase.CONFLICT_REPLACE
        writableDatabase.insertWithOnConflict("label_properties", null, values, conflict)
    }

    /**
     * Every number label, with its properties, most numbers first and then the most recently given. Labels that
     * have properties but aren't on any number anymore come last.
     */
    fun getLabelUsage(): List<LabelUsage> {
        val properties = getLabelProperties()
        val sql = """
            SELECT l.label, COUNT(DISTINCT $SUFFIX_SQL), MAX(c.date)
            FROM labels l JOIN calls c ON c.id = l.call_id
            WHERE l.number_label = 1
            GROUP BY l.label
            ORDER BY COUNT(DISTINCT $SUFFIX_SQL) DESC, MAX(c.date) DESC
        """.trimIndent()
        val used = readableDatabase.rawQuery(sql, null).use {
            buildList {
                while (it.moveToNext()) {
                    val label = it.getString(0)
                    add(LabelUsage(properties[label.lowercase()] ?: LabelProperties(label), it.getInt(1), it.getLong(2)))
                }
            }
        }
        val unused = properties.values
            .filter { p -> used.none { it.properties.label.equals(p.label, ignoreCase = true) } }
            .sortedBy { it.label.lowercase() }
            .map { LabelUsage(it, numbers = 0, lastUsed = 0) }
        return used + unused
    }

    /**
     * What to do with a call from [number] at [minuteOfDay]: blocked if one of its number labels blocks then,
     * otherwise silenced if any label at all rings only then and the number has none of those, otherwise rung.
     */
    fun screen(number: String, minuteOfDay: Int): Screening {
        val properties = getLabelProperties()
        val labels = getLabelsForNumber(number).mapNotNull { properties[it.lowercase()] }
        if (labels.any { it.blocksAt(minuteOfDay) }) return Screening.BLOCK

        val ringOnly = properties.values.filter { it.ringsOnlyAt(minuteOfDay) }
        if (ringOnly.isNotEmpty() && labels.none { it in ringOnly }) return Screening.SILENCE
        return Screening.RING
    }

    /** Whether [number] has a label that blocks calls, at least at some hours. */
    fun isNumberBlocked(number: String): Boolean {
        val properties = getLabelProperties()
        return getLabelsForNumber(number).any { properties[it.lowercase()]?.block == true }
    }
}
