package com.example.quickbite

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform