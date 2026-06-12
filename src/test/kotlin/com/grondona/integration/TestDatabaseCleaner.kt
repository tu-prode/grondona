package com.grondona.integration

import com.grondona.utils.Clock
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class TestDatabaseCleaner(
    private val entityManager: EntityManager
) {

    @Transactional
    fun cleanAll() {
        Clock.sync(LocalDateTime.now())
        entityManager.createNativeQuery("DELETE FROM match_predictions").executeUpdate()
        entityManager.createNativeQuery("DELETE FROM award_predictions").executeUpdate()
        entityManager.createNativeQuery("DELETE FROM group_users").executeUpdate()
        entityManager.createNativeQuery("DELETE FROM matches").executeUpdate()
        entityManager.createNativeQuery("DELETE FROM players").executeUpdate()
        entityManager.createNativeQuery("DELETE FROM teams").executeUpdate()
        entityManager.createNativeQuery("DELETE FROM groups").executeUpdate()
        entityManager.createNativeQuery("DELETE FROM tournaments").executeUpdate()
        entityManager.createNativeQuery("DELETE FROM users").executeUpdate()
    }
}
