package com.calltags.app.models

// a label given to one call. Number labels describe the number itself and also show, faded, on its other calls;
// call labels only ever belong to the call they were given to
data class CallLabel(
    val text: String,
    val isNumberLabel: Boolean = true,
)
