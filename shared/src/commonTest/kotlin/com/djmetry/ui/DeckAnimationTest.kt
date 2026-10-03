package com.djmetry.ui

import com.djmetry.ui.screens.stampVoteAlpha
import kotlin.test.*

/** Штамп «Голос» на карточке колоды: проявляется при свайпе вверх и гаснет при уводе вбок. */
class DeckAnimationTest {
    @Test
    fun voteStampFollowsVerticalSwipe() {
        val t = 100f
        assertEquals(0f, stampVoteAlpha(0f, 0f, t))
        assertEquals(1f, stampVoteAlpha(0f, -200f, t), "вверх — проявился")
        assertEquals(0f, stampVoteAlpha(0f, 120f, t), "вниз — не появляется")
        assertTrue(stampVoteAlpha(150f, -100f, t) < stampVoteAlpha(0f, -100f, t), "вбок — гаснет")
    }
}
