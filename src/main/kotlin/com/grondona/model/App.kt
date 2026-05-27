package com.grondona.model

const val PROD = "prod"
const val LOCAL = "local"

enum class Environment {
    LOCAL, PROD;

    companion object {
        fun fromProfile(profile: String): Environment {
            return when (profile.lowercase()) {
                com.grondona.model.PROD -> Environment.PROD
                com.grondona.model.LOCAL -> Environment.LOCAL
                else -> throw IllegalArgumentException("Unknown environment: $profile")
            }
        }

    }
}
