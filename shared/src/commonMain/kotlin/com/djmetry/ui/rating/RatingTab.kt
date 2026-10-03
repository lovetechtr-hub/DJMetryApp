package com.djmetry.ui.rating

import com.djmetry.ui.screens.actionError
import androidx.compose.material3.minimumInteractiveComponentSize
import com.djmetry.ui.components.CountryFlag
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp

import kotlinx.datetime.toLocalDateTime
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djmetry.LocalAppContainer
import com.djmetry.data.repository.RatingChange
import com.djmetry.data.repository.RatingRow
import com.djmetry.data.repository.RatingPage
import com.djmetry.data.repository.RatingQuery
import com.djmetry.data.repository.RatingRange
import com.djmetry.data.repository.RatingType
import com.djmetry.data.repository.rangeLabel
import com.djmetry.ui.settings.SearchPickerDialog
import com.djmetry.data.repository.podiumSplit
import com.djmetry.i18n.Strings
import com.djmetry.ui.artist.ArtistName
import com.djmetry.ui.artist.LocalArtistNavigator
import com.djmetry.ui.artist.compactCount
import com.djmetry.ui.components.AutoSizeText
import com.djmetry.ui.components.CoverImage
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.layout.LayoutClass
import com.djmetry.ui.layout.LocalBottomClearance
import com.djmetry.ui.layout.LocalLayoutClass
import com.djmetry.ui.layout.readableWidth
import com.djmetry.ui.screens.actionErrorKey
import com.djmetry.ui.screens.formatDelta
import com.djmetry.ui.screens.formatScore
import com.djmetry.ui.theme.DJMetryColors
import kotlinx.coroutines.launch

private val Gold = Color(0xFFF5C542)
private val Silver = Color(0xFFC0C7D6)
private val Bronze = Color(0xFFCD8B4E)

/** Ширина колонки места: «1000» при крупном шрифте подбирает кегль, но колонка одна и та же — строки ровные. */
internal val RANK_COLUMN = 40.dp

/** Подпись под местом: «2» (+ стрелка-иконка вверх/вниз), «+6.45»; null — ничего не изменилось. Без символов-стрелок — иконки Material. */
internal fun changeLabel(change: RatingChange?): String? = when (change) {
    null -> null
    is RatingChange.Places -> kotlin.math.abs(change.delta).toString()
    is RatingChange.Growth -> (if (change.score >= 0) "+" else "") + formatDelta(change.score)
    RatingChange.New -> "NEW"
}

internal fun changeIsUp(change: RatingChange?): Boolean = when (change) {
    is RatingChange.Places -> change.delta > 0
    is RatingChange.Growth -> change.score >= 0
    RatingChange.New, null -> true
}

/** Страны в фильтре — как на сайте: популярные сверху, остальные — через поиск по справочнику. */
internal val POPULAR_COUNTRIES = listOf("US", "GB", "DE", "NL", "FR", "BE", "ES", "IT", "BR", "MX", "CA", "AU", "SE", "NO", "PL", "UA", "TR")

/** Карточка выбранного артиста справа — только если окно шире этого (иначе строка открывает карточку артиста). */
internal const val DETAIL_PANEL_MIN_DP = 1250f

/**
 * Рейтинг — вариант A «Капсула и шкала сотен» + таблица «Чистый список» с подиумом.
 * Капсула: DJMetry / Ambient / DJ Mag / Итоги года. Шкала: сотни и Talents (или годы). Фильтры страна и жанр —
 * чипы со шторкой (телефон, планшет-портрет) или постоянная панель слева (альбом, десктоп).
 */
@Composable
fun RatingTab() {
    val container = LocalAppContainer.current
    val openArtist = LocalArtistNavigator.current
    // Запрос, страница, диапазоны и прокрутка — во ViewModel: возврат на вкладку и поворот ничего не сбрасывают
    val vm = com.djmetry.ui.search.appViewModel<RatingViewModel>()
    val listState = vm.listState
    var query by vm::query
    var attempt by vm::attempt
    var ranges by vm::ranges
    LaunchedEffect(query.type) {
        if (vm.rangesFor == query.type && ranges != null) return@LaunchedEffect
        ranges = null
        // Шкала не загрузилась — не вечный скелетон: показываем текущее деление и тихо повторяем
        for (wait in listOf(0L, 2_000L, 5_000L, 15_000L)) {
            kotlinx.coroutines.delay(wait)
            val r = container.rating.ranges(query.type).getOrNull()
            if (r != null) { ranges = r; vm.rangesFor = query.type; return@LaunchedEffect }
            if (ranges == null) ranges = listOf(query.range)
        }
    }
    // Смена фильтра — старые строки остаются и затемняются (спека), первая загрузка — скелетон
    var shown by vm::shown
    var busy by vm::busy
    LaunchedEffect(query, attempt) {
        if (vm.shownFor == query to attempt && shown != null) return@LaunchedEffect
        busy = true
        shown = container.rating.load(query, refresh = attempt > 0)
        vm.shownFor = query to attempt
        busy = false
    }
    // Наверх — только при настоящей смене запроса, а не при каждом входе на вкладку
    var scrolledFor by vm::scrolledFor
    LaunchedEffect(query) { if (query != scrolledFor) { listState.scrollToItem(0); scrolledFor = query } }
    // DJ Mag: год по умолчанию — последний из ответа (новый выходит осенью; в январе прошлого может ещё не быть)
    LaunchedEffect(ranges) {
        val years = ranges.orEmpty().filterIsInstance<RatingRange.Year>()
        if (query.type == RatingType.DJMag && years.isNotEmpty() && query.range !in years) query = query.copy(range = years.maxBy { it.year })
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = LocalLayoutClass.current == LayoutClass.Expanded
        val detail = wide && maxWidth.value >= DETAIL_PANEL_MIN_DP
        var selected by remember(query) { mutableStateOf<RatingRow?>(null) }
        val onRow: (RatingRow) -> Unit = { row -> if (detail) selected = row else row.spotifyArtistId?.let(openArtist) }
        val header: @Composable () -> Unit = {
            RatingHeader(query, ranges, chips = !wide, onQuery = { query = it })
        }
        if (wide) {
            Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.width(300.dp).windowInsetsPadding(WindowInsets.statusBars).padding(top = 12.dp).verticalScroll(rememberScrollState())) {
                    FilterPanel(query) { query = it }
                }
                Column(Modifier.weight(1f).widthIn(max = 980.dp)) {
                    RatingList(listState, shown, busy, header, wide = true, onRow = onRow, onRetry = { attempt++ })
                }
                if (detail) {
                    val pick = selected ?: shown?.getOrNull()?.rows?.firstOrNull()
                    Box(Modifier.width(320.dp).windowInsetsPadding(WindowInsets.statusBars).padding(top = 12.dp)) {
                        when {
                            pick != null -> DetailPanel(pick)
                            shown == null -> DetailPanelSkeleton()
                        }
                    }
                }
            }
        } else {
            Box(Modifier.readableWidth()) {
                RatingList(listState, shown, busy, header, wide = false, onRow = onRow, onRetry = { attempt++ })
            }
        }
    }
}

/**
 * Шапка, вариант A (design/navigation/header-variants.html). Узко (телефон): «Рейтинг DJMetry ▾» — источник меню в
 * заголовке, ниже переключатель диапазона «‹ 1–100 ›» и круглые фильтры страна / жанр. Широко: капсула источников и
 * переключатель в одну строку (фильтры — чипами или постоянной панелью слева на десктопе).
 */
@Composable
private fun RatingHeader(query: RatingQuery, ranges: List<RatingRange>?, chips: Boolean, onQuery: (RatingQuery) -> Unit) {
    val i18n = useI18n()
    val onType: (RatingType) -> Unit = { t ->
        // Новый тип — первое деление его шкалы; фильтры — только у рейтингов по Score
        onQuery(RatingQuery(type = t, range = defaultRange(t), genre = query.genre.takeIf { t.byScore && query.type == t }, country = query.country.takeIf { t.byScore }))
    }
    BoxWithConstraints(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = 12.dp, bottom = 6.dp)) {
        val narrow = maxWidth < HEADER_CAPSULE_MIN_DP.dp
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (narrow) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(i18n.t(Strings.TAB_RATING), style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold), color = DJMetryColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(10.dp))
                    TypeMenu(query.type, onType, Modifier.weight(1f))
                }
            } else {
                if (chips) AutoSizeText(i18n.t(Strings.TAB_RATING), TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold), color = DJMetryColors.Text, minFontSize = 20.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) { TypeCapsule(query.type, onType) }
                    RangeStepper(ranges, query.range) { onQuery(query.copy(range = it)) }
                }
            }
            if (narrow || (chips && query.type.byScore)) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (narrow) RangeStepper(ranges, query.range) { onQuery(query.copy(range = it)) }
                Spacer(Modifier.weight(1f))
                if (query.type.byScore) {
                    if (narrow) FilterButtons(query, onQuery) else FilterChips(query, onQuery)
                }
            }
        }
    }
}

/** Уже этого — источник меню в заголовке и фильтры кнопками; шире — капсула. */
internal const val HEADER_CAPSULE_MIN_DP = 560

/** Названия источников: в меню и капсуле. «Итоги года» — перевод. */
@Composable
private fun typeLabel(t: RatingType, long: Boolean = false): String {
    val i18n = useI18n()
    return when (t) {
        RatingType.DJMetry -> "DJMetry"
        RatingType.Ambient -> "Ambient"
        RatingType.DJMag -> if (long) "DJ Mag Top 100" else "DJ Mag"
        RatingType.Year -> i18n.t(if (long) Strings.RT_YEAR else Strings.RT_YEAR_SHORT)
    }
}

private fun typeIcon(t: RatingType): ImageVector = when (t) {
    RatingType.DJMetry -> Icons.Outlined.Hub
    RatingType.Ambient -> Icons.Outlined.Waves
    RatingType.DJMag -> Icons.Outlined.Newspaper
    RatingType.Year -> Icons.Outlined.EmojiEvents
}

/** «DJMetry ▾» рядом с заголовком: меню источников — длина названий на любом языке не важна. */
@Composable
private fun TypeMenu(selected: RatingType, onSelect: (RatingType) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.DropdownList) { open = true }.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AutoSizeText(typeLabel(selected), TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold), color = DJMetryColors.Accent, minFontSize = 14.sp, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.Filled.ExpandMore, null, tint = DJMetryColors.Accent, modifier = Modifier.size(26.dp))
        }
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = DJMetryColors.PanelStrong) {
            RatingType.values().forEach { t ->
                val on = t == selected
                DropdownMenuItem(
                    text = { Text(typeLabel(t, long = true), color = if (on) DJMetryColors.Accent else DJMetryColors.Text, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = { Icon(typeIcon(t), null, tint = if (on) DJMetryColors.Accent else DJMetryColors.Muted) },
                    onClick = { open = false; onSelect(t) },
                )
            }
        }
    }
}

/** Соседнее деление шкалы: -1 — назад, +1 — вперёд; null — край. */
internal fun stepRange(ranges: List<RatingRange>, current: RatingRange, delta: Int): RatingRange? =
    ranges.indexOf(current).takeIf { it >= 0 }?.let { ranges.getOrNull(it + delta) }

/** «‹ 1–100 ›»: стрелки листают деления, тап по подписи — список всех (сотни, Talents или годы). */
@Composable
private fun RangeStepper(ranges: List<RatingRange>?, selected: RatingRange, onSelect: (RatingRange) -> Unit) {
    val items = ranges ?: return com.djmetry.ui.components.SkeletonBox(Modifier.size(150.dp, 40.dp), CircleShape)
    var open by remember { mutableStateOf(false) }
    val prev = stepRange(items, selected, -1)
    val next = stepRange(items, selected, 1)
    Box {
        Row(
            Modifier.height(40.dp).clip(CircleShape).background(DJMetryColors.Panel).border(1.dp, DJMetryColors.Border, CircleShape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val arrow: @Composable (ImageVector, RatingRange?) -> Unit = { icon, target ->
                Icon(
                    icon, null, tint = if (target != null) DJMetryColors.Text else DJMetryColors.Muted.copy(alpha = 0.35f),
                    modifier = Modifier.size(40.dp).clip(CircleShape).clickable(enabled = target != null, role = Role.Button) { target?.let(onSelect) }.padding(9.dp),
                )
            }
            arrow(Icons.Filled.ChevronLeft, prev)
            Text(
                rangeLabel(selected), color = DJMetryColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.DropdownList) { open = true }.padding(horizontal = 6.dp, vertical = 6.dp),
            )
            arrow(Icons.Filled.ChevronRight, next)
        }
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = DJMetryColors.PanelStrong) {
            items.forEach { r ->
                val on = r == selected
                DropdownMenuItem(
                    text = { Text(rangeLabel(r), color = if (on) DJMetryColors.Accent else DJMetryColors.Text, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { open = false; onSelect(r) },
                )
            }
        }
    }
}

/** Телефон: круглые кнопки «страна» (флаг, если выбрана) и «жанр»; выбранный фильтр — зелёная обводка и точка. */
@Composable
private fun FilterButtons(query: RatingQuery, onQuery: (RatingQuery) -> Unit) {
    val i18n = useI18n()
    var pickCountry by remember { mutableStateOf(false) }
    var pickGenre by remember { mutableStateOf(false) }
    FilterButton(i18n.t(Strings.SET_COUNTRY), active = query.country != null, onClick = { pickCountry = true }) {
        if (query.country != null) CountryFlag(query.country, 22.dp) else Icon(Icons.Outlined.Public, null, tint = DJMetryColors.Text, modifier = Modifier.size(22.dp))
    }
    FilterButton(query.genre ?: i18n.t(Strings.RATING_GENRE), active = query.genre != null, onClick = { pickGenre = true }) {
        Icon(Icons.Outlined.MusicNote, null, tint = if (query.genre != null) DJMetryColors.Accent else DJMetryColors.Text, modifier = Modifier.size(22.dp))
    }
    if (pickCountry) CountryDialog(query.country, { onQuery(query.copy(country = it)); pickCountry = false }) { pickCountry = false }
    if (pickGenre) GenreDialog(query.type, { onQuery(query.copy(genre = it)); pickGenre = false }) { pickGenre = false }
}

@Composable
private fun FilterButton(label: String, active: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(44.dp).semantics { contentDescription = label }.clip(CircleShape).background(DJMetryColors.Panel)
            .border(if (active) 1.5.dp else 1.dp, if (active) DJMetryColors.Accent else DJMetryColors.Border, CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
        if (active) Box(Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 7.dp).size(8.dp).clip(CircleShape).background(DJMetryColors.Accent))
    }
}

/** Первое деление шкалы для типа (пока шкала грузится): TOP 100 или последний год. */
internal fun defaultRange(type: RatingType): RatingRange = when (type) {
    RatingType.DJMetry, RatingType.Ambient -> RatingRange.Top(100)
    RatingType.DJMag -> RatingRange.Year(currentSeason())
    RatingType.Year -> RatingRange.Year(currentSeason())
}

/** Последний сезон DJ Mag / итогов — прошлый календарный год до ноября (рейтинг выходит осенью). */
internal fun currentSeason(): Int = kotlin.time.Clock.System.now()
    .toLocalDateTime(kotlinx.datetime.TimeZone.UTC).year.let { it - 1 }

/** Капсула типов: цветная «таблетка» переезжает на выбранный (анимация). */
@Composable
private fun TypeCapsule(selected: RatingType, onSelect: (RatingType) -> Unit) {
    val i18n = useI18n()
    val types = RatingType.values().toList()
    val labels = listOf("DJMetry", "Ambient", "DJ Mag", i18n.t(Strings.RT_YEAR_SHORT))
    BoxWithConstraints(Modifier.fillMaxWidth().widthIn(max = 640.dp).clip(CircleShape).background(DJMetryColors.Panel).padding(4.dp)) {
        val cell = maxWidth / types.size
        val offset = animateDpAsState(cell * types.indexOf(selected), spring(dampingRatio = 0.8f, stiffness = 500f), label = "capsule")
        Box(
            // Сдвиг бегунка — на этапе раскладки (лямбда), а не пересборкой переключателя каждый кадр
            Modifier.offset { androidx.compose.ui.unit.IntOffset(offset.value.roundToPx(), 0) }.width(cell).height(40.dp).clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(DJMetryColors.Accent, DJMetryColors.Accent2)))
        )
        Row(Modifier.fillMaxWidth().height(40.dp)) {
            types.forEachIndexed { i, t ->
                Box(Modifier.weight(1f).fillMaxHeight().clip(CircleShape).clickable(role = Role.Tab) { onSelect(t) }, contentAlignment = Alignment.Center) {
                    AutoSizeText(labels[i], TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Bold), color = if (t == selected) DJMetryColors.Background else DJMetryColors.Text, minFontSize = 9.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** Чипы «Страна ▾» и «Жанр ▾» с иконками — открывают выбор; выбранное — зелёным с крестиком. */
@Composable
private fun FilterChips(query: RatingQuery, onQuery: (RatingQuery) -> Unit) {
    val i18n = useI18n()
    var pickCountry by remember { mutableStateOf(false) }
    var pickGenre by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(query.country ?: i18n.t(Strings.RT_ALL_COUNTRIES), query.country != null,
            leading = { on -> if (query.country != null) CountryFlag(query.country, 20.dp) else Icon(Icons.Outlined.Public, null, tint = if (on) DJMetryColors.Background else DJMetryColors.Accent, modifier = Modifier.size(18.dp)) },
            onClear = { onQuery(query.copy(country = null)) }) { pickCountry = true }
        FilterChip(query.genre ?: i18n.t(Strings.RT_ALL_GENRES), query.genre != null,
            leading = { on -> Icon(Icons.Outlined.MusicNote, null, tint = if (on) DJMetryColors.Background else DJMetryColors.Accent, modifier = Modifier.size(18.dp)) },
            onClear = { onQuery(query.copy(genre = null)) }) { pickGenre = true }
    }
    if (pickCountry) CountryDialog(query.country, { onQuery(query.copy(country = it)); pickCountry = false }) { pickCountry = false }
    if (pickGenre) GenreDialog(query.type, { onQuery(query.copy(genre = it)); pickGenre = false }) { pickGenre = false }
}

@Composable
private fun FilterChip(text: String, active: Boolean, leading: @Composable (Boolean) -> Unit, onClear: () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(if (active) DJMetryColors.Accent else DJMetryColors.Panel).clickable(role = Role.Button, onClick = onClick)
            .padding(start = 14.dp, end = if (active) 6.dp else 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leading(active)
        Text(text, color = if (active) DJMetryColors.Background else DJMetryColors.Text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (active) Icon(Icons.Filled.Close, null, tint = DJMetryColors.Background, modifier = Modifier.minimumInteractiveComponentSize().size(28.dp).clip(CircleShape).clickable(onClick = onClear).padding(6.dp))
        else Icon(Icons.Filled.ArrowDropDown, null, tint = DJMetryColors.Muted, modifier = Modifier.size(20.dp))
    }
}

/** Выбор страны: популярные сверху флагами, дальше — весь справочник с поиском. */
@Composable
private fun CountryDialog(current: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val i18n = useI18n()
    val settings = LocalAppContainer.current.settings
    val allLoad = com.djmetry.ui.components.rememberLoadable { settings.countries() }
    val all = allLoad.value.orEmpty()
    val ordered = remember(all) { POPULAR_COUNTRIES.mapNotNull { c -> all.firstOrNull { it.code == c } } + all.filter { it.code !in POPULAR_COUNTRIES } }
    SearchPickerDialog(
        history = com.djmetry.data.search.SearchScope.Country,
        title = i18n.t(Strings.SET_COUNTRY), items = ordered, label = { it.name }, flagIso = { it.code },
        onPick = { onPick(it.code) }, onDismiss = onDismiss, load = allLoad,
        extra = if (current != null) i18n.t(Strings.RT_RESET) to { onPick(null) } else null,
    )
}

/** Выбор жанра — из списка бэкенда для этой категории (dj / ambient). */
@Composable
private fun GenreDialog(type: RatingType, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val i18n = useI18n()
    val repo = LocalAppContainer.current.rating
    val allLoad = com.djmetry.ui.components.rememberLoadable(type) { repo.genres(type) }
    val all = allLoad.value.orEmpty()
    SearchPickerDialog(
        history = com.djmetry.data.search.SearchScope.Genre,
        title = i18n.t(Strings.RATING_GENRE), items = all, label = { it }, onPick = { onPick(it) }, onDismiss = onDismiss, load = allLoad,
        extra = i18n.t(Strings.RT_ALL_GENRES) to { onPick(null) },
    )
}

/** Строка «поиск» в панели фильтров: иконка лупы Material + текст. */
@Composable
private fun SearchRow(text: String, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DJMetryColors.PanelStrong).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.Search, null, tint = if (active) DJMetryColors.Accent else DJMetryColors.Muted, modifier = Modifier.size(18.dp))
        Text(text, color = if (active) DJMetryColors.Accent else DJMetryColors.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Постоянная панель фильтров (альбом, десктоп): флаги популярных стран сеткой + поиск, жанры чипами + поиск. */
@Composable
private fun FilterPanel(query: RatingQuery, onQuery: (RatingQuery) -> Unit) {
    val i18n = useI18n()
    val repo = LocalAppContainer.current.rating
    var pickCountry by remember { mutableStateOf(false) }
    var pickGenre by remember { mutableStateOf(false) }
    val genres by produceState(emptyList<String>(), query.type) { value = emptyList(); value = repo.genres(query.type).getOrNull().orEmpty() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AutoSizeText(i18n.t(Strings.TAB_RATING), TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold), color = DJMetryColors.Text, minFontSize = 20.sp)
        if (!query.type.byScore) return@Column
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(DJMetryColors.Panel).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PanelTitle(i18n.t(Strings.SET_COUNTRY), if (query.country != null) i18n.t(Strings.RT_RESET) else null) { onQuery(query.copy(country = null)) }
            POPULAR_COUNTRIES.chunked(3).forEach { rowCodes ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowCodes.forEach { code ->
                        val on = query.country == code
                        Row(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                .background(if (on) DJMetryColors.Accent.copy(alpha = 0.15f) else DJMetryColors.PanelStrong)
                                .clickable { onQuery(query.copy(country = if (on) null else code)) }.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CountryFlag(code, 18.dp)
                            Text(code, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = if (on) DJMetryColors.Accent else DJMetryColors.Text, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                    repeat(3 - rowCodes.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            SearchRow(i18n.t(Strings.SEARCH_HINT), active = false) { pickCountry = true }
            PanelTitle(i18n.t(Strings.RATING_GENRE), if (query.genre != null) i18n.t(Strings.RT_RESET) else null) { onQuery(query.copy(genre = null)) }
            SearchRow(query.genre ?: i18n.t(Strings.RT_ALL_GENRES), active = query.genre != null) { pickGenre = true }
            genres.take(10).chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pair.forEach { g ->
                        val on = query.genre == g
                        Text(g, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (on) DJMetryColors.Background else DJMetryColors.Text, textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f).clip(CircleShape).background(if (on) DJMetryColors.Accent else DJMetryColors.PanelStrong)
                                .clickable { onQuery(query.copy(genre = if (on) null else g)) }.padding(horizontal = 8.dp, vertical = 7.dp))
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
    if (pickCountry) CountryDialog(query.country, { onQuery(query.copy(country = it)); pickCountry = false }) { pickCountry = false }
    if (pickGenre) GenreDialog(query.type, { onQuery(query.copy(genre = it)); pickGenre = false }) { pickGenre = false }
}

@Composable
private fun PanelTitle(title: String, action: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = DJMetryColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        action?.let { Text(it, color = DJMetryColors.Accent, fontSize = 12.5.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onAction).padding(horizontal = 8.dp, vertical = 12.dp)) }
    }
}

@Composable
private fun RatingList(
    listState: LazyListState,
    state: Result<RatingPage>?,
    busy: Boolean,
    header: @Composable () -> Unit,
    wide: Boolean,
    onRow: (RatingRow) -> Unit,
    onRetry: () -> Unit,
) {
    val i18n = useI18n()
    val page = state?.getOrNull()
    val split = page?.rows?.let(::podiumSplit)
    // Смена фильтра: пока грузится новое — старые строки затемнены, без скелетона
    val dim by animateFloatAsState(if (busy && page != null) 0.4f else 1f, label = "dim")
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = if (wide) 0.dp else 16.dp, end = if (wide) 0.dp else 16.dp, bottom = LocalBottomClearance.current),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "header") { header() }
        when {
            state == null -> {
                item(key = "podium-skeleton") { PodiumSkeleton() }
                items(RATING_SKELETON_ROWS, key = { "skeleton-$it" }) { RatingRowSkeleton(it, wide) }
            }
            page == null -> item(key = "error") {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(i18n.t(Strings.HOME_ERROR), color = DJMetryColors.Muted, fontSize = 15.sp)
                    TextButton(onClick = onRetry) { Text(i18n.t(Strings.HOME_RETRY), color = DJMetryColors.Accent) }
                }
            }
            page.notFinalized -> item(key = "not-final") { EmptyNote(i18n.t(Strings.RT_YEAR_NOT_FINAL)) }
            page.rows.isEmpty() -> item(key = "empty") { EmptyNote(i18n.t(Strings.RT_EMPTY_FILTER)) }
            else -> {
                page.shownYear?.let { y -> item(key = "shown-year") { Text(i18n.tWithArgs(Strings.RT_SHOWING_YEAR, arrayOf(y)), color = DJMetryColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp)) } }
                if (split!!.podium.isNotEmpty()) item(key = "podium") { Box(Modifier.graphicsLayer { alpha = dim }) { Podium(split.podium, onRow) } }
                if (wide) item(key = "table-head") { TableHeader() }
                items(split.rest, key = { "${it.position}-${it.spotifyArtistId ?: it.name}" }) { row ->
                    Box(Modifier.graphicsLayer { alpha = dim }) { if (wide) TableRow(row) { onRow(row) } else ListRow(row) { onRow(row) } }
                }
            }
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(text, color = DJMetryColors.Muted, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp))
}

/** Подиум: 2-е, 1-е, 3-е; фото в рамке золото / серебро / бронза, номер на рамке. */
@Composable
private fun Podium(podium: List<RatingRow>, onRow: (RatingRow) -> Unit) {
    val colors = listOf(Silver, Gold, Bronze)
    val heights = listOf(52.dp, 70.dp, 40.dp)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(DJMetryColors.Panel)
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(24.dp)).padding(start = 12.dp, end = 12.dp, top = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom,
    ) {
        podium.forEachIndexed { i, row ->
            val first = i == 1
            Column(
                Modifier.weight(if (first) 1.15f else 1f).clip(RoundedCornerShape(16.dp)).clickable { onRow(row) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(contentAlignment = Alignment.BottomCenter) {
                    val size: Dp = if (first) 88.dp else 70.dp
                    Box(Modifier.padding(bottom = 10.dp).clip(RoundedCornerShape(if (first) 26.dp else 22.dp)).border(3.dp, colors[i], RoundedCornerShape(if (first) 26.dp else 22.dp))) {
                        CoverImage(row.imageUrl, size, cornerRadius = if (first) 26.dp else 22.dp)
                    }
                    com.djmetry.ui.components.CountBadge(row.position, fg = DJMetryColors.Background, bg = colors[i], size = 24.dp)
                }
                AutoSizeText(
                    row.name, TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp), color = DJMetryColors.Text,
                    minFontSize = 10.sp, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp),
                )
                row.score?.let { Text(formatScore(it), color = DJMetryColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                Box(
                    Modifier.padding(top = 8.dp).fillMaxWidth().height(heights[i]).clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                        .background(Brush.verticalGradient(listOf(colors[i].copy(alpha = 0.28f), Color.Transparent)))
                )
            }
        }
    }
}

/** Колонка места: номер (кегль подбирается — «100» и «1000» не рвутся) и сдвиг под ним. */
@Composable
private fun RankCell(row: RatingRow) {
    Column(Modifier.width(RANK_COLUMN), horizontalAlignment = Alignment.End) {
        AutoSizeText("${row.position}", TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = DJMetryColors.Text, minFontSize = 10.sp, textAlign = TextAlign.End)
        ChangeText(row.change, 10.5f)
    }
}

@Composable
private fun ChangeText(change: RatingChange?, sizeSp: Float) {
    val label = changeLabel(change)
    if (label == null) Text("—", color = DJMetryColors.Muted, fontSize = sizeSp.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    else {
        val color = if (changeIsUp(change)) DJMetryColors.Accent else DJMetryColors.LowScore
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (change is RatingChange.Places) Icon(
                if (change.delta > 0) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown, null, tint = color,
                modifier = Modifier.size((sizeSp * 1.7f).dp).padding(end = 0.dp),
            )
            Text(label, color = color, fontSize = sizeSp.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Строка варианта A: место + сдвиг, фото, имя, жанр, Score справа. */
@Composable
private fun ListRow(row: RatingRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DJMetryColors.Panel).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RankCell(row)
        Spacer(Modifier.width(12.dp))
        CoverImage(row.imageUrl, 46.dp, cornerRadius = 13.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            AutoSizeText(row.name, TextStyle(fontSize = 15.5.sp, fontWeight = FontWeight.Bold), color = DJMetryColors.Text, minFontSize = 12.sp)
            Subtitle(row)
        }
        row.score?.let { AutoSizeText(formatScore(it), TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = DJMetryColors.Text, minFontSize = 12.sp) }
    }
}

/** Под именем — только жанр, во всю ширину (DJ Mag в таблице не показываем — отдельная вкладка). */
@Composable
private fun Subtitle(row: RatingRow) {
    val text = listOfNotNull(row.genre, useI18n().t(Strings.RT_TALENT_SCORE).takeIf { row.talent }).joinToString(" · ")
    if (text.isNotEmpty()) Text(text, color = DJMetryColors.Muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

// ───────── Таблица (альбом, десктоп) ─────────

@Composable
private fun TableHeader() {
    val i18n = useI18n()
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        val style = TextStyle(fontSize = 12.sp, color = DJMetryColors.Muted)
        Text("#", style = style, modifier = Modifier.width(RANK_COLUMN + 12.dp))
        Text(i18n.t(Strings.RATING_ARTIST), style = style, modifier = Modifier.weight(1f))
        Text(i18n.t(Strings.RATING_GENRE), style = style, modifier = Modifier.width(200.dp))
        Text("Score", style = style, modifier = Modifier.width(80.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun TableRow(row: RatingRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(DJMetryColors.Panel).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RankCell(row)
        Spacer(Modifier.width(12.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            CoverImage(row.imageUrl, 40.dp, cornerRadius = 11.dp)
            AutoSizeText(row.name, TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = DJMetryColors.Text, minFontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp))
        }
        Text(row.genre ?: "—", color = DJMetryColors.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(200.dp))
        Text(row.score?.let(::formatScore) ?: "—", color = DJMetryColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(80.dp))
    }
}

/** Карточка выбранного артиста справа (альбом, десктоп): фото, место, Score, подписчики, «Следить», «Открыть карточку». */
@Composable
private fun DetailPanel(row: RatingRow) {
    val i18n = useI18n()
    val container = LocalAppContainer.current
    val openArtist = LocalArtistNavigator.current
    val scope = rememberCoroutineScope()
    val flight = com.djmetry.ui.components.rememberSingleFlight() // два тапа по «Подписаться» — один запрос
    val follows by container.discover.follows.collectAsState()
    val following = follows.any { it.spotifyArtistId == row.spotifyArtistId }
    var message by remember(row) { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(DJMetryColors.Panel)
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(22.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val openId = row.spotifyArtistId
        Box(if (openId != null) Modifier.clip(RoundedCornerShape(18.dp)).clickable(role = Role.Button) { openArtist(openId) } else Modifier) {
            CoverImage(row.imageUrl, 312.dp, cornerRadius = 18.dp)
            com.djmetry.ui.components.PillBadge("#${row.position}", DJMetryColors.Background, DJMetryColors.Accent, Modifier.align(Alignment.TopStart).padding(12.dp), fontSize = 13.sp, height = 28.dp)
        }
        Box(if (openId != null) Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { openArtist(openId) } else Modifier) {
            ArtistName(row.name, verified = false, size = 24.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric(row.score?.let(::formatScore) ?: "—", "Score", DJMetryColors.Accent, Modifier.weight(1f))
            Metric(row.followers?.let(::compactCount) ?: row.djMagRank?.let { "#$it" } ?: "—", if (row.followers != null) "Spotify" else "DJ Mag", DJMetryColors.Text, Modifier.weight(1f))
        }
        val id = row.spotifyArtistId
        if (id != null) {
            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(if (following) DJMetryColors.PanelStrong else DJMetryColors.Accent)
                    .clickable(enabled = !flight.busy) {
                        flight.run(scope) {
                            val r = if (following) container.discover.unfollow(id) else container.discover.follow(id, row.name, row.imageUrl)
                            message = r.exceptionOrNull()?.let { i18n.actionError(it) }
                        }
                    },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Favorite, null, tint = if (following) DJMetryColors.Accent else DJMetryColors.Background, modifier = Modifier.size(18.dp))
                Text(i18n.t(if (following) Strings.ARTIST_FOLLOWING else Strings.ACTION_FOLLOW), color = if (following) DJMetryColors.Text else DJMetryColors.Background, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
            }
            Text(
                i18n.t(Strings.RATING_OPEN_CARD), color = DJMetryColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, DJMetryColors.Border, RoundedCornerShape(14.dp)).clickable { openArtist(id) }.padding(12.dp),
            )
        }
        message?.let { Text(it, color = DJMetryColors.LowScore, fontSize = 12.sp) }
    }
}

@Composable
private fun Metric(value: String, caption: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(DJMetryColors.PanelStrong).padding(horizontal = 12.dp, vertical = 9.dp)) {
        AutoSizeText(value, TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold), color = color, minFontSize = 11.sp)
        AutoSizeText(caption, TextStyle(fontSize = 11.5.sp), color = DJMetryColors.Muted, minFontSize = 8.sp)
    }
}

/** Рейтинг во ViewModel: запрос (тип, сотня, фильтры), загруженная страница, диапазоны и прокрутка. */
internal class RatingViewModel : androidx.lifecycle.ViewModel() {
    var query by mutableStateOf(RatingQuery())
    var attempt by mutableStateOf(0)
    var ranges by mutableStateOf<List<RatingRange>?>(null)
    var rangesFor: RatingType? = null
    var shown by mutableStateOf<Result<RatingPage>?>(null)
    var shownFor: Pair<RatingQuery, Int>? = null
    var busy by mutableStateOf(true)
    var scrolledFor by mutableStateOf(RatingQuery())
    val listState = LazyListState()
}
