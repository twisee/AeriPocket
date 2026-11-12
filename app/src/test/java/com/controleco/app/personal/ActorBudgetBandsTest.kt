package com.controleco.app.personal

import com.controleco.app.ui.personal.ActorPoseSpec
import com.controleco.app.ui.personal.actorStageForProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class ActorBudgetBandsTest {
    private val poses = listOf(0, 25, 50, 75, 100).mapIndexed { i, id ->
        id to ActorPoseSpec("pose_$id.png", minBudgetPercent = i * 20)
    }.toMap()

    @Test fun eachArtworkGetsTwentyPercentOfTheBudgetRange() {
        for ((progress, stage) in listOf(0f to 0, .1f to 0, .2f to 25, .3f to 25,
            .4f to 50, .5f to 50, .6f to 75, .7f to 75, .8f to 100, .9f to 100, 1f to 100)) {
            assertEquals("Budget progress $progress", stage, actorStageForProgress(progress, poses))
        }
    }
    @Test fun boundariesSwitchExactlyAtTwentyFortySixtyAndEightyPercent() {
        for ((boundary, before, after) in listOf(Triple(.2f, 0, 25), Triple(.4f, 25, 50),
            Triple(.6f, 50, 75), Triple(.8f, 75, 100))) {
            assertEquals(before, actorStageForProgress(java.lang.Math.nextDown(boundary), poses))
            assertEquals(after, actorStageForProgress(boundary, poses))
        }
    }
    @Test fun fallingSpendingReturnsToEarlierPoseAndOverspendingKeepsLastPose() {
        assertEquals(100, actorStageForProgress(1.5f, poses))
        assertEquals(75, actorStageForProgress(.79f, poses))
        assertEquals(50, actorStageForProgress(.59f, poses))
        assertEquals(25, actorStageForProgress(.39f, poses))
        assertEquals(0, actorStageForProgress(-.1f, poses))
    }
}
