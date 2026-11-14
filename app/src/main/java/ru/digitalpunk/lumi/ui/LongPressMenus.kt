package ru.digitalpunk.lumi.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.engine.BackHistory
import ru.digitalpunk.lumi.engine.visibleHistoryRows
import ru.digitalpunk.lumi.privacy.LocalAppColorScheme
import ru.digitalpunk.lumi.ui.tabgrid.cardTitle

/** A 48 dp round bar button, like `IconButton`, that also takes a long press; a null [onLongClick] gives it none. */
@Composable
fun LongPressIconButton(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    longClickLabel: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Box(
        modifier.minimumInteractiveComponentSize().size(48.dp).clip(CircleShape).combinedClickable(
            enabled = enabled,
            role = Role.Button,
            onLongClickLabel = longClickLabel,
            onLongClick = onLongClick,
            onClick = onClick,
        ),
        contentAlignment = Alignment.Center,
    ) { CompositionLocalProvider(LocalContentColor provides color, content = content) }
}

/** A bar menu of one group, in the app's own colours, also under the private tab's dark bar. */
@Composable
fun LumiDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) = LumiGroupedMenu(expanded, onDismiss, modifier, scrollState) { group(content) }

/** Collects the groups of a [LumiGroupedMenu]; an empty group draws an empty box, so add a group only when it has rows. */
class MenuGroups {
    internal val list = mutableListOf<@Composable ColumnScope.() -> Unit>()

    fun group(content: @Composable ColumnScope.() -> Unit) {
        list += content
    }
}

/** A bar menu as a stack of rounded group surfaces (Material 3 Expressive), in the app's own colours. */
@Composable
fun LumiGroupedMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    groups: MenuGroups.() -> Unit,
) {
    val list = MenuGroups().apply(groups).list
    // Where the popup landed, to choose the offset sign (see [MenuShadowRoom]); the first frame uses start-aligned, below.
    var anchor by remember { mutableStateOf(Rect.Zero) }
    var shell by remember { mutableStateOf(Rect.Zero) }
    // A zero-size probe beside the popup: its parent is the anchor the popup is placed against.
    if (expanded) Spacer(Modifier.onGloballyPositioned { anchor = it.parentLayoutCoordinates?.screenRect() ?: Rect.Zero })
    val atEnd = abs(shell.left - anchor.left) > abs(shell.right - anchor.right)
    val above = shell.center.y < anchor.center.y
    MaterialTheme(colorScheme = LocalAppColorScheme.current ?: MaterialTheme.colorScheme) {
        // The stock menu stays as a transparent shell: it keeps the placing, dismiss, animation and scroll; the groups draw the surface.
        DropdownMenu(
            expanded,
            onDismiss,
            modifier.onGloballyPositioned { shell = it.screenRect() }.trimStockPadding(),
            // The offset is not mirrored when the menu aligns to the anchor's end or opens above it, so its sign follows the side.
            offset = DpOffset(if (atEnd) MenuShadowRoom else -MenuShadowRoom, if (above) MenuShadowRoom else -MenuShadowRoom),
            scrollState = scrollState,
            shape = RectangleShape,
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(Modifier.padding(MenuShadowRoom), Arrangement.spacedBy(MenuGroupGap)) {
                list.forEachIndexed { i, content ->
                    Surface(
                        shape = groupShape(groupSlot(i, list.size), MaterialTheme.shapes.large, MenuJointCorner),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = MenuDefaults.TonalElevation,
                        shadowElevation = MenuDefaults.ShadowElevation,
                    ) { Column(Modifier.padding(MenuGroupPadding), content = content) }
                }
            }
        }
    }
}

/** Where a group sits in the stack; it decides which corners are round. */
enum class GroupSlot { Only, First, Middle, Last }

internal fun groupSlot(index: Int, count: Int): GroupSlot = when {
    count == 1 -> GroupSlot.Only
    index == 0 -> GroupSlot.First
    index == count - 1 -> GroupSlot.Last
    else -> GroupSlot.Middle
}

/** The outer corners of the stack take [outer]; the corners where two groups meet take [joint]. */
internal fun groupShape(slot: GroupSlot, outer: CornerBasedShape, joint: CornerSize): Shape {
    val top = if (slot == GroupSlot.Only || slot == GroupSlot.First) outer.topStart else joint
    val bottom = if (slot == GroupSlot.Only || slot == GroupSlot.Last) outer.bottomStart else joint
    return outer.copy(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom)
}

// The theme has no 6 dp slot, and its Shapes must not change: small feeds the chips, extraSmall the text fields and snackbars.
private val MenuJointCorner = CornerSize(6.dp)
private val MenuGroupGap = 4.dp
private val MenuGroupPadding = 4.dp

/**
 * The shell clips what it draws, so the groups sit this far inside it to keep their shadow. The menu offset moves the
 * shell out by the same amount, so the visible groups meet the anchor where the stock menu did.
 */
private val MenuShadowRoom = 4.dp

/** The stock menu's 8 dp above and below its rows, outside the scrolled part; [trimStockPadding] cuts it off. */
private val MenuStockPadding = 8.dp

/** What the scrolled part adds around the rows, top and bottom: shadow room and group padding, plus the stock padding the max height still counts. */
private val MenuVerticalPadding = (MenuStockPadding + MenuShadowRoom + MenuGroupPadding) * 2

/** Draws the shell without the stock vertical padding, which would stay as a transparent gap between the groups and the anchor. */
private fun Modifier.trimStockPadding() = layout { measurable, constraints ->
    val trim = MenuStockPadding.roundToPx()
    val placeable = measurable.measure(constraints)
    layout(placeable.width, (placeable.height - 2 * trim).coerceAtLeast(0)) { placeable.place(0, -trim) }
}

private fun LayoutCoordinates.screenRect() = Rect(positionOnScreen(), size.toSize())

/** Menu rows are rounded to the medium shape, so the ripple and highlight follow the group's inner curve. */
@Composable
internal fun Modifier.menuItemShape(): Modifier = clip(MaterialTheme.shapes.medium)

/** The tab's back history above the Back button: oldest at the top, the current page at the bottom, marked and in view. */
@Composable
fun TabHistoryMenu(history: BackHistory, expanded: Boolean, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val scroll = rememberScrollState()
    // Menus start at the top; the nearest entries are at the bottom.
    LaunchedEffect(expanded) {
        if (expanded) scroll.scrollTo(snapshotFlow { scroll.maxValue }.first { it > 0 })
    }
    val untitled = stringResource(R.string.tab_untitled)
    LumiDropdownMenu(
        expanded,
        onDismiss,
        Modifier.heightIn(max = 48.dp * visibleHistoryRows(history.rows.size) + MenuVerticalPadding),
        scroll,
    ) {
        history.rows.forEach { row ->
            val label = cardTitle(row.title, row.url, untitled)
            if (row.isCurrent) {
                val current = stringResource(R.string.cd_history_current_item, label)
                val onColor = MaterialTheme.colorScheme.onSecondaryContainer
                DropdownMenuItem(
                    text = { Text(label, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = onDismiss,
                    leadingIcon = { Icon(Icons.Filled.Check, contentDescription = null) },
                    colors = MenuDefaults.itemColors(textColor = onColor, leadingIconColor = onColor),
                    modifier = Modifier.menuItemShape().background(MaterialTheme.colorScheme.secondaryContainer).semantics {
                        contentDescription = current
                        selected = true
                    },
                )
            } else {
                DropdownMenuItem(
                    text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        onDismiss()
                        onPick(row.index)
                    },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_history), contentDescription = null) },
                    modifier = Modifier.menuItemShape(),
                )
            }
        }
    }
}

/** New tab, new private tab and close tab: the long press on Tabs (Classic) and More (Compact). */
@Composable
fun QuickActionsMenu(expanded: Boolean, onDismiss: () -> Unit, tabs: TabActions) {
    LumiDropdownMenu(expanded, onDismiss) {
        MenuItem(stringResource(R.string.menu_new_tab), { Icon(Icons.Filled.Add, null) }) { onDismiss(); tabs.newTab() }
        MenuItem(stringResource(R.string.menu_new_private_tab), { Icon(painterResource(R.drawable.ic_domino_mask), null) }) {
            onDismiss(); tabs.newPrivateTab()
        }
        MenuItem(stringResource(R.string.menu_close_tab), { Icon(Icons.Filled.Close, null) }) { onDismiss(); tabs.closeTab() }
    }
}

/** A menu row with a leading icon; the icon is decoration, the text is what TalkBack reads. */
@Composable
fun MenuItem(text: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, onClick = onClick, leadingIcon = icon, modifier = Modifier.menuItemShape())
}
