package com.exordos.workspace.modules.chatchannels

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign

internal class FolderPageState(
    val pageCount: Int,
    initialPage: Int,
    private val scope: CoroutineScope,
    private val onPageSelected: (Int) -> Unit,
) {
    private class Drag(val range: IntRange, val interruptedTarget: Int?) {
        var moved = false
    }

    var page by mutableIntStateOf(initialPage)
        private set
    var position by mutableFloatStateOf(initialPage.toFloat())
        private set

    private var drag: Drag? = null
    private var deferredRepositoryPage: Int? = null
    private var settlingTarget by mutableIntStateOf(-1)
    private var animation: Job? = null
    val isSettling: Boolean get() = settlingTarget >= 0

    fun startDragging() {
        val interrupted = settlingTarget.takeIf { it >= 0 }
        val range = if (interrupted != null) {
            floor(position).toInt()..ceil(position).toInt()
        } else {
            (page - 1).coerceAtLeast(0)..(page + 1).coerceAtMost(pageCount - 1)
        }
        // Reuse the position already on screen when a finger catches a moving page.
        cancel()
        drag = Drag(range, interrupted)
    }

    /** [delta] is measured in pages, with positive values moving to the next folder. */
    fun dragBy(delta: Float): Float {
        val gesture = drag ?: return delta
        val start = position
        position = (start + delta).coerceIn(gesture.range.first.toFloat(), gesture.range.last.toFloat())
        gesture.moved = gesture.moved || position != start
        val atEdge = (delta < 0f && position == 0f) ||
            (delta > 0f && position == (pageCount - 1).toFloat())
        return if (atEdge) position - start else delta
    }

    fun stopDragging(velocity: Float) {
        val gesture = drag ?: return
        drag = null
        val offset = position - page
        val target = when {
            gesture.interruptedTarget != null && !gesture.moved -> gesture.interruptedTarget
            gesture.interruptedTarget != null && abs(velocity) >= 1.5f ->
                if (velocity > 0) gesture.range.last else gesture.range.first
            gesture.interruptedTarget != null -> position.roundToInt()
            abs(offset) >= 1f / 3 || (abs(velocity) >= 3f && velocity * offset > 0f) ->
                page + offset.sign.toInt()
            else -> page
        }
        settle(target, velocity)
    }

    fun syncRepositoryPage(index: Int) {
        if (drag != null || isSettling) {
            deferredRepositoryPage = index
        } else {
            showPage(index)
        }
    }

    fun selectPage(index: Int) {
        cancel()
        deferredRepositoryPage = null
        showPage(index)
        onPageSelected(index)
    }

    private fun showPage(index: Int) {
        position = index.toFloat()
        page = index
    }

    fun cancel() {
        animation?.cancel()
        settlingTarget = -1
        drag = null
    }

    private fun settle(target: Int, velocity: Float) {
        val distance = target - position
        if (distance == 0f) {
            finish(target)
            return
        }
        settlingTarget = target
        val duration = (145 + 210 * abs(distance) / (1 + 0.15f * abs(velocity))).roundToInt()
        animation = scope.launch {
            animate(
                initialValue = position,
                targetValue = target.toFloat(),
                animationSpec = tween(duration, easing = LinearOutSlowInEasing),
            ) { value, _ -> position = value }
            finish(target)
        }
    }

    private fun finish(index: Int) {
        settlingTarget = -1
        val repositoryPage = deferredRepositoryPage
        deferredRepositoryPage = null
        if (page != index) {
            // A completed swipe is a new selection, just like a tab tap.
            showPage(index)
            onPageSelected(index)
        } else {
            showPage(repositoryPage ?: index)
        }
    }
}

@Composable
internal fun FolderPages(
    state: FolderPageState,
    pageKeys: List<String?>,
    pageContent: @Composable (Int) -> Unit,
) {
    var width by remember { mutableIntStateOf(0) }
    val forward = if (LocalLayoutDirection.current == LayoutDirection.Ltr) -1f else 1f
    val overscroll = rememberOverscrollEffect()
    val dragState = rememberDraggableState { pixels ->
        if (width > 0) {
            val consume: (Offset) -> Offset = { delta ->
                val consumed = state.dragBy(delta.x * forward / width)
                Offset(consumed * width * forward, 0f)
            }
            val delta = Offset(pixels, 0f)
            if (overscroll == null) consume(delta) else {
                overscroll.applyToScroll(delta, NestedScrollSource.UserInput, consume)
            }
        }
    }

    Box(
        Modifier.fillMaxSize()
            .clipToBounds()
            .testTag("folder_pages")
            .onSizeChanged { width = it.width }
            .overscroll(overscroll)
            .draggable(
                dragState,
                Orientation.Horizontal,
                enabled = pageKeys.firstOrNull() != null,
                startDragImmediately = state.isSettling,
                onDragStarted = { state.startDragging() },
                onDragStopped = { pixelsPerSecond ->
                    if (width > 0) state.stopDragging(pixelsPerSecond * forward / width)
                    overscroll?.applyToFling(Velocity.Zero) { Velocity.Zero }
                },
            ),
    ) {
        val first = (state.page - 1).coerceAtLeast(0)
        val last = (state.page + 1).coerceAtMost(state.pageCount - 1)
        for (index in first..last) {
            key(pageKeys[index]) {
                Box(Modifier.matchParentSize().offset {
                    IntOffset(((index - state.position) * width).roundToInt(), 0)
                }) {
                    pageContent(index)
                }
            }
        }
    }
}
