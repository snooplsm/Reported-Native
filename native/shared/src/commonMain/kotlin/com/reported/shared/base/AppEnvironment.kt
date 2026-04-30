package com.reported.shared.base

enum class AppEnvironment(val baseUrl: String) {
    Development("https://reported-stats.herokuapp.com/prod/"),
    Staging("https://reported-stats.herokuapp.com/staging/"),
    Production("https://reported-stats.herokuapp.com/prod/")
}

