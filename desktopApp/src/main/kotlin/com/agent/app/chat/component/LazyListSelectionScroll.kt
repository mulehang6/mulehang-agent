package com.agent.app.chat.component

import androidx.compose.foundation.lazy.LazyListState

/** 返回把键盘目标刚好带入视口所需的起始索引；目标已可见时不滚动。 */
internal fun minimalScrollStartForSelection(
    selectedIndex: Int,
    firstVisibleIndex: Int?,
    lastVisibleIndex: Int?,
): Int? = when {
    firstVisibleIndex == null || lastVisibleIndex == null -> selectedIndex
    selectedIndex < firstVisibleIndex -> selectedIndex
    selectedIndex > lastVisibleIndex ->
        (selectedIndex - (lastVisibleIndex - firstVisibleIndex)).coerceAtLeast(0)

    else -> null
}

/** 键盘选择只在目标越出视口时滚动，鼠标选择不调用此入口。 */
internal suspend fun LazyListState.ensureItemVisibleMinimal(index: Int) {
    val visibleItems = layoutInfo.visibleItemsInfo
    minimalScrollStartForSelection(
        selectedIndex = index,
        firstVisibleIndex = visibleItems.firstOrNull()?.index,
        lastVisibleIndex = visibleItems.lastOrNull()?.index,
    )?.let { startIndex -> scrollToItem(startIndex) }
}
