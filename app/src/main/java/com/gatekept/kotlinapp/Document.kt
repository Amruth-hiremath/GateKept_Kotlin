package com.gatekept.kotlinapp

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude

data class Document(
    @get:Exclude var id: String? = null,
    var title: String? = null,
    var description: String? = null,
    var courseCode: String? = null,
    var courseName: String? = null,
    var category: String? = null,
    var examType: String? = null,
    var school: String? = null,
    var program: String? = null,
    var academicYear: String? = null,
    var paperYear: String? = null,
    var tags: List<String>? = null,
    var status: String? = null,
    var fileType: String? = null,
    var fileUrl: String? = null,
    var uploaderName: String? = null,
    var upvotes: Int = 0,
    var timestamp: Timestamp? = null,
    var uploaderUid: String? = null,
    val embedding: List<Float> = emptyList(),
    val contentSnippet: String? = null
)
