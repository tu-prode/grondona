package com.grondona.model.dto.response

import com.grondona.model.Group
import com.grondona.model.GroupUser
import com.grondona.model.MatchOutcome
import com.grondona.model.MatchPrediction
import com.grondona.model.PredictionStatus
import com.grondona.model.User
import com.grondona.model.UserPermissions
import java.util.UUID

data class AuthenticatedUserResponse(
    val token: String,
    val userId: UUID,
    val username: String,
    val email: String,
    val fullname: String,
    val permissions: UserPermissions,
    val shouldResetPassword: Boolean? = null,
)

data class UserResponse(
    val id: UUID,
    val fullname: String,
    val username: String,
    val email: String,
    val permissions: UserPermissions,
    val uniquePredictions: Boolean,
    val joinRequests: List<JoinRequestResponse> = emptyList(),
    val profiles: List<PredictionProfileResponse> = emptyList()
) {
    companion object {
        fun from(user: User): UserResponse = UserResponse(
            id = user.id!!,
            fullname = user.fullname,
            username = user.username,
            email = user.email,
            permissions = user.permissions,
            uniquePredictions = user.hasUniquePredictions,
        )

        fun withJoinRequests(user: User, joinRequests: List<GroupUser>): UserResponse = UserResponse(
            id = user.id!!,
            fullname = user.fullname,
            username = user.username,
            email = user.email,
            permissions = user.permissions,
            uniquePredictions = user.hasUniquePredictions,
            joinRequests = joinRequests.groupBy { it.group }.map { (group, users) ->
                JoinRequestResponse.from(group, users.map { it.user })
            }
        )
    }

    fun withJoinRequests(joinRequests: List<GroupUser>): UserResponse = copy(
        joinRequests = joinRequests.groupBy { it.group }.map { (group, users) ->
            JoinRequestResponse.from(group, users.map { it.user })
        }
    )

    fun withProfiles(profiles: List<PredictionProfileResponse>): UserResponse = copy(profiles = profiles)
}

data class StatusProfileResponse(
    val missing: Int,
    val incorrect: Int,
    val partial: Int,
    val correct: Int,
    val bonus: Int,
)

data class QuotasProfileResponse(
    val quota: Float,
    val prediction: MatchPredictionResponse,
)

data class PredictionProfileResponse(
    val group: GroupResponse,
    val totalPoints: Float,
    val quotasPoints: Float,
    val awardsPoints: Float? = null,
    val commonMatches: StatusProfileResponse,
    val highlightedMatches: StatusProfileResponse,
    val topSucceededQuota: QuotasProfileResponse? = null,
    val topFailedQuota: QuotasProfileResponse? = null,
)
