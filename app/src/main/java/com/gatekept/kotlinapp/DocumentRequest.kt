package com.gatekept.kotlinapp

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude

data class DocumentRequest(
    @get:Exclude var id: String? = null,
    var requestedTopic: String? = null,
    var details: String? = null,
    var requesterName: String? = null,
    var requesterUid: String? = null,
    var status: String? = null,
    var bountyPoints: Int = 0,
    var timestamp: Timestamp? = null
)
