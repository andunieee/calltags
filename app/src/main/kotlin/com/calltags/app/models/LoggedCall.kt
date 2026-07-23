package com.calltags.app.models

// a call stored in our own history database, together with the labels the user gave it.
// [otherNumberLabels] are number labels given to other calls from the same number and not to this one.
// [blocked] says whether the number is on the system block list now, it is not stored in the database
data class LoggedCall(
    val id: Long,
    val number: String,
    val name: String,
    val date: Long,
    val duration: Int,
    val type: Int,
    val labels: List<CallLabel>,
    val otherNumberLabels: List<String> = emptyList(),
    val blocked: Boolean = false,
)
