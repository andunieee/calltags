package com.calltags.app.models

// a call stored in our own history database, together with the labels the user gave it.
// [otherNumberLabels] are number labels given to other calls from the same number and not to this one.
// A [type] of [PLACEHOLDER_TYPE] is no real call, just something to hang number labels on for a number we never
// had a call with, e.g. one that was on the system block list
data class LoggedCall(
    val id: Long,
    val number: String,
    val name: String,
    val date: Long,
    val duration: Int,
    val type: Int,
    val labels: List<CallLabel>,
    val otherNumberLabels: List<String> = emptyList(),
) {
    /** Every number label this call's number has, its own first. */
    val numberLabels get() = labels.filter { it.isNumberLabel }.map { it.text } + otherNumberLabels

    companion object {
        // not one of the CallLog.Calls types
        const val PLACEHOLDER_TYPE = 0
    }
}
