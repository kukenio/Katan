package org.katan.service.account.http.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import kotlinx.serialization.Serializable

@Serializable
internal data class RegisterRequest(
    @field:NotBlank(message = "Username cannot be blank")
    @field:Size(
        min = 2,
        max = 48,
        message = "Username must have a minimum length of {min} and at least {max} characters"
    )
    val username: String,

    @field:NotBlank(message = "Password cannot be blank")
    @field:Size(
        min = 8,
        message = "Password must have a minimum length of 8"
    )
    val password: String
)
