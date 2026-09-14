package com.example.mindmap.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.abs

/** A compact number picker whose selected item is the physical viewport centre. */
@Composable
internal fun MiniWheelPicker(
    range: IntRange,
    selected: Int,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 30.dp,
    visibleCount: Int = 3,
    columnWidth: Dp = 46.dp,
    selectedColor: Color = Color.White,
    unselectedColor: Color = selectedColor.copy(alpha = 0.35f),
    selectedFontSize: Int = 16,
    unselectedFontSize: Int = 13,
    showCenterIndicator: Boolean = true
) {
    require(visibleCount >= 3 && visibleCount % 2 == 1) { "Mini wheel needs an odd visible item count of at least three." }
    val values = remember(range) { range.toList() }
    val listState = rememberLazyListState()
    val flingBehavior = rememberSnapFlingBehavior(listState, snapPosition = SnapPosition.Center)
    val currentSelected by rememberUpdatedState(selected)
    var synchronisingExternalValue by remember { mutableStateOf(true) }

    // Saved/reset values scroll into place only while the user is not dragging.
    LaunchedEffect(selected, values) {
        if (!listState.isScrollInProgress) {
            synchronisingExternalValue = true
            val target = values.indexOf(selected).coerceAtLeast(0)
            val layout = listState.layoutInfo
            val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
            val centeredIndex = layout.visibleItemsInfo.minByOrNull { item ->
                abs((item.offset + item.size / 2f) - viewportCenter)
            }?.index
            if (centeredIndex != target) listState.scrollToItem(target)
            synchronisingExternalValue = false
        }
    }

    // LayoutInfo includes both the actual viewport and every item's pixel
    // position. It avoids the old firstVisibleItemIndex/padding off-by-one bug.
    LaunchedEffect(listState, values) {
        snapshotFlow {
            if (synchronisingExternalValue) return@snapshotFlow null
            val layout = listState.layoutInfo
            val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
            layout.visibleItemsInfo
                .filter { it.index in values.indices }
                .minByOrNull { item -> abs((item.offset + item.size / 2f) - viewportCenter) }
                ?.index
        }
            .distinctUntilChanged()
            .map { index -> index?.let(values::get) }
            .collect { centeredValue ->
                if (centeredValue != null && centeredValue != currentSelected) onSelectedChange(centeredValue)
            }
    }

    Box(modifier = modifier.height(itemHeight * visibleCount), contentAlignment = Alignment.Center) {
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = itemHeight * (visibleCount / 2)),
            modifier = Modifier.width(columnWidth)
        ) {
            items(values) { value ->
                val isSelected = value == selected
                Box(
                    modifier = Modifier
                        .width(columnWidth)
                        .height(itemHeight)
                        .pointerInput(value) { detectTapGestures(onTap = { onSelectedChange(value) }) },
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Text(
                        text = "%02d".format(value),
                        color = if (isSelected) selectedColor else unselectedColor,
                        fontSize = if (isSelected) selectedFontSize.sp else unselectedFontSize.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
        if (showCenterIndicator) {
            Box(
                Modifier.width(columnWidth).height(itemHeight).clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            )
        }
    }
}
