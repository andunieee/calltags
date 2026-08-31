package com.calltags.app.models

// what earlier calls with a number looked like, shown on the in-call screen.
// the "recent" counts cover the last month, [missedSinceTalk] counts missed calls since the last time anyone picked up
data class CallerHistory(
    val lastCall: LoggedCall?,
    val missedSinceTalk: Int,
    val talkedEver: Boolean,
    val recentOutgoing: Int,
    val recentOutgoingUnanswered: Int,
    val recentIncoming: Int,
)
