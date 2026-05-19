package com.example.mymaps.models

import java.io.Serializable

data class Place(
    val title: String = "",
    val description: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val colorHue: Float = 0.0f,
    val creationTimestamp: Long = System.currentTimeMillis()
) : Serializable
