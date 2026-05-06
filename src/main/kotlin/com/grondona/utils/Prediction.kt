package com.grondona.utils

import com.grondona.model.Match
import com.grondona.model.MatchPrediction
import java.util.UUID
import kotlin.collections.firstOrNull

/**
 * Consolidates match predictions for a group into a structure indexed by user.
 *
 * For each user present in this list of [MatchPrediction], this function builds a list of predictions
 * aligned with the provided [matches] list. Each position in the resulting list corresponds to the
 * match at the same index in [matches].
 *
 * If a user has a prediction for a given match, that [MatchPrediction] is included in the result.
 * Otherwise, `null` is placed in that position.
 *
 * The resulting map:
 * - Key: the user's unique identifier ([UUID])
 * - Value: a list of predictions (or `null`) ordered according to [matches]
 *
 * ### Example
 * If `matches = [M1, M2, M3]` and a user has predictions only for `M1` and `M3`,
 * their resulting list will be:
 * `[P1, null, P3]`
 *
 * @param matches the list of matches that defines the order and scope of the output predictions
 * @return a map where each key is a user ID and the value is a list of predictions aligned with [matches],
 *         containing `null` when no prediction exists for a given match
 */
fun List<MatchPrediction>.consolidateGroupMatchPredictions(matches: List<Match>): Map<UUID, List<MatchPrediction?>> =
    this.groupBy { it.user.id!! }.mapValues { (_, userPredictions) ->
        val matchPredictions = userPredictions.groupBy { it.match.id }
        matches.map { match -> matchPredictions[match.id!!]?.firstOrNull() }
    }
