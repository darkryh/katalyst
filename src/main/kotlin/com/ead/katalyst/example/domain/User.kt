package com.ead.katalyst.example.domain

data class User(
    val id: Long = 0,
    val name: String,
    val email: String,
    val active: Boolean = true
)