package com.gatekept.kotlinapp

import com.google.firebase.Timestamp

data class Comment(
    var id: String? = null,
    val authorName: String? = null,
    val text: String? = null,
    val upvotes: Int = 0,
    val timestamp: Timestamp? = null
)
