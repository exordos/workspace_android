package com.exordos.workspace.modules.chatchannels

import androidx.activity.ComponentActivity
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberPlatformOverscrollFactory
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderPagesGestureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var state: FolderPageState
    private val viewport get() = compose.onNodeWithTag("folder_pages")
    private val unconsumed = mutableListOf<Float>()

    @Test fun twoPagesFollowTheFingerAndFinishMovingBeforeSelectionChanges() {
        displayPages()
        viewport.performTouchInput {
            down(center.copy(x = width * 0.86f))
            moveTo(center.copy(x = width * 0.50f), delayMillis = 160)
        }
        val middle = position()
        viewport.performTouchInput { moveTo(center.copy(x = width * 0.17f), delayMillis = 180) }
        val released = position()
        assertTrue(released > middle + 0.2f)
        compose.onNodeWithTag("page-1").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        try {
            viewport.performTouchInput { up() }
            compose.mainClock.advanceTimeBy(48)
            assertTrue(position() > released)
            assertEquals(0, selectedPage())
            compose.mainClock.advanceTimeBy(650)
            assertEquals(1f, position())
            assertEquals(1, selectedPage())
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun touchingDuringSettleGrabsTheSameSlideAndTheNextMovementFollowsTheFinger() {
        displayPages()
        releaseTowardNext()
        try {
            compose.mainClock.advanceTimeBy(48)
            val grabbed = position()
            viewport.performTouchInput { down(center.copy(x = width * 0.86f)) }
            try {
                compose.mainClock.advanceTimeBy(300)
                assertEquals("The slide must hold at the grabbed position", grabbed, position(), 0.001f)
                viewport.performTouchInput { moveTo(center.copy(x = width * 0.78f), delayMillis = 48) }
                assertEquals(grabbed + 0.08f, position(), 0.005f)
                viewport.performTouchInput { moveTo(center.copy(x = width * 0.12f), delayMillis = 120) }
                assertEquals("The active pair must not skip a folder", 1f, position(), 0.001f)
                assertEquals(0, selectedPage())
            } finally {
                viewport.performTouchInput { up() }
            }
            compose.mainClock.advanceTimeBy(400)
            assertEquals(1, selectedPage())
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun oneFingerCanReverseASettlingSlideMoreThanOnce() {
        displayPages()
        releaseTowardNext()
        try {
            compose.mainClock.advanceTimeBy(48)
            val grabbed = position()
            viewport.performTouchInput { down(center.copy(x = width * 0.12f)) }
            try {
                viewport.performTouchInput { moveTo(center.copy(x = width * 0.32f), delayMillis = 80) }
                assertEquals(grabbed - 0.20f, position(), 0.005f)
                viewport.performTouchInput { moveTo(center.copy(x = width * 0.22f), delayMillis = 80) }
                assertEquals(grabbed - 0.10f, position(), 0.005f)
                viewport.performTouchInput { moveTo(center.copy(x = width * 0.90f), delayMillis = 160) }
                assertTrue(position() < 0.3f)
                assertEquals(0, selectedPage())
            } finally {
                viewport.performTouchInput { up() }
            }
            compose.mainClock.advanceTimeBy(400)
            assertEquals(0f, position())
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun liftingWithoutMovementResumesTheSlideAndLeavesTheNextGestureReady() {
        displayPages()
        releaseTowardNext()
        try {
            compose.mainClock.advanceTimeBy(80)
            assertEquals(0, selectedPage())
            viewport.performTouchInput { down(center.copy(x = width * 0.84f)) }
            try {
                compose.mainClock.advanceTimeBy(600)
                assertEquals(0, selectedPage())
            } finally {
                viewport.performTouchInput { up() }
            }
            compose.mainClock.advanceTimeBy(400)
            assertEquals(1, selectedPage())
            viewport.performTouchInput { swipeLeft() }
            compose.mainClock.advanceTimeBy(400)
            assertEquals(2, selectedPage())
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun outwardGesturesFeedPlatformOverscrollOnTheFirstAndLastPage() {
        displayPages()
        viewport.performTouchInput { swipeRight() }
        assertEquals(0, selectedPage())
        assertTrue(compose.runOnIdle { unconsumed.any { it > 1f } })

        compose.runOnIdle {
            state.selectPage(2)
            unconsumed.clear()
        }
        viewport.performTouchInput { swipeLeft() }
        assertEquals(2, selectedPage())
        assertTrue(compose.runOnIdle { unconsumed.any { it < -1f } })
    }

    @Test fun theOnlyFolderOverscrollsInBothDirections() {
        displayPages(pageCount = 1)
        viewport.performTouchInput { swipeRight() }
        viewport.performTouchInput { swipeLeft() }
        assertTrue(compose.runOnIdle { unconsumed.any { it > 1f } && unconsumed.any { it < -1f } })
        assertEquals(0, selectedPage())
    }

    private fun displayPages(pageCount: Int = 3) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            val pages = remember { FolderPageState(pageCount, 0, scope) {} }
            state = pages
            val platform = rememberPlatformOverscrollFactory()
            val overscroll = remember(platform) {
                platform.let { factory ->
                    object : OverscrollFactory {
                        override fun createOverscrollEffect(): OverscrollEffect {
                            val effect = factory.createOverscrollEffect()
                            return object : OverscrollEffect by effect {
                                override fun applyToScroll(
                                    delta: Offset,
                                    source: NestedScrollSource,
                                    performScroll: (Offset) -> Offset,
                                ): Offset = effect.applyToScroll(delta, source) { available ->
                                    val consumed = performScroll(available)
                                    if (source == NestedScrollSource.UserInput) {
                                        unconsumed += available.x - consumed.x
                                    }
                                    consumed
                                }
                            }
                        }

                        override fun equals(other: Any?) = this === other
                        override fun hashCode() = System.identityHashCode(this)
                    }
                }
            }
            CompositionLocalProvider(LocalOverscrollFactory provides overscroll) {
                FolderPages(pages, List(pageCount) { it.toString() }) { index ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Folder ${index + 1}", Modifier.testTag("page-$index"))
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun releaseTowardNext() {
        viewport.performTouchInput {
            down(center.copy(x = width * 0.86f))
            moveTo(center.copy(x = width * 0.52f), delayMillis = 160)
            moveTo(center.copy(x = width * 0.18f), delayMillis = 180)
        }
        compose.mainClock.autoAdvance = false
        viewport.performTouchInput { up() }
    }

    private fun position() = compose.runOnIdle { state.position }
    private fun selectedPage() = compose.runOnIdle { state.page }
}
