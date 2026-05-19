package com.example.mymaps.models

import com.google.firebase.firestore.DocumentId
import java.io.Serializable

data class UserMap (
    val title: String = "",
    val places: List<Place> = emptyList(),
    @DocumentId val id: String? = null,
    val userId: String = ""
) : Serializable
