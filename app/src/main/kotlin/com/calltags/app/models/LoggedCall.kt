package com.calltags.app.models

// a call stored in our own history database, together with the labels the user gave it
data class LoggedCall(
    val id: Long,
    val number: String,
    val name: String,
    val date: Long,
    val duration: Int,
    val type: Int,
    val labels: List<String>,
)
