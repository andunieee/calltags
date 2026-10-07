package com.calltags.app.models

sealed class Events {
    data object RefreshCallLog : Events()
}
