package com.dbpsecurity.dbpcheckin.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    @SerialName("id")
    val id: String,

    @SerialName("name")
    val name: String? = null,

    @SerialName("email")
    val email: String? = null,

    @SerialName("phone")
    val phone: String? = null,

    @SerialName("tehsil")
    val tehsil: String? = null,

    @SerialName("image_url")
    val imageUrl: String? = null,

    @SerialName("role")
    val role: String = "employee",

    @SerialName("group_id")
    val groupId: String? = null
)

