package com.grondona.model.dto.response

import com.grondona.model.ExtendedAwards

data class AwardsResponse(
    val champion: TeamResponse,
    val topScorer: PlayerResponse,
    val bestPlayer: PlayerResponse,
    val bestGoalkeeper: PlayerResponse,
    val bestYoungPlayer: PlayerResponse,
) {
    companion object {
        fun from(awards: ExtendedAwards): AwardsResponse = AwardsResponse(
            champion = TeamResponse.from(awards.champion),
            topScorer = PlayerResponse.from(awards.topScorer),
            bestPlayer = PlayerResponse.from(awards.bestPlayer),
            bestGoalkeeper = PlayerResponse.from(awards.bestGoalkeeper),
            bestYoungPlayer = PlayerResponse.from(awards.bestYoungPlayer),
        )
    }
}
