package com.djmetry.ui.screens

import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import com.djmetry.ui.components.AutoSizeText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.withStyle

import com.djmetry.data.repository.RatingRow
import com.djmetry.ui.components.shimmer
import com.djmetry.ui.components.SkeletonListRow
import com.djmetry.ui.components.SkeletonLine
import com.djmetry.ui.components.SkeletonCircle
import com.djmetry.ui.components.SkeletonBox
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.djmetry.data.repository.DiscoverRepository
import com.djmetry.ui.layout.LayoutClass
import com.djmetry.ui.layout.LocalLayoutClass
import kotlinx.coroutines.CoroutineScope
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import com.djmetry.data.repository.DECK_TOP_SIZE
import com.djmetry.data.repository.FollowSort
import com.djmetry.data.repository.sortFollows
import com.djmetry.data.repository.filterFollows
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djmetry.LocalAppContainer
import com.djmetry.api.ApiException
import com.djmetry.api.models.FollowedArtist
import com.djmetry.api.models.RankedArtist
import com.djmetry.data.repository.DeckSource
import com.djmetry.data.repository.next
import com.djmetry.data.repository.VoteLimitException
import com.djmetry.i18n.Strings
import com.djmetry.ui.artist.LocalArtistNavigator
import com.djmetry.ui.components.CoverImage
import com.djmetry.ui.components.DJMetryLogo
import com.djmetry.ui.i18n.useI18n
import com.djmetry.ui.layout.LocalBottomClearance
import com.djmetry.ui.layout.readableWidth
import com.djmetry.ui.theme.DJMetryColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private val Orange = Color(0xFFFFB35B)

/** Действие со свайпа или кнопки. */
internal enum class SwipeAction { Skip, Follow, Vote }

/** Решение по жесту: вправо — подписка, влево — пропуск, вверх — голос, иначе вернуть карточку. */
internal fun swipeDecision(dx: Float, dy: Float, threshold: Float): SwipeAction? = when {
    dx > threshold -> SwipeAction.Follow
    dx < -threshold -> SwipeAction.Skip
    dy < -threshold * 1.2f && abs(dx) < threshold -> SwipeAction.Vote
    else -> null
}

/** Ключ текста ошибки действия. */
internal fun actionErrorKey(error: Throwable): String = when {
    error is VoteLimitException -> Strings.TOAST_VOTE_LIMIT
    error is ApiException && error.isUnauthorized -> Strings.TOAST_NEED_LOGIN
    error is ApiException && error.isRateLimited -> Strings.TOAST_SLOW_DOWN
    error is ApiException && error.code == "no_rating" -> Strings.TOAST_VOTE_NO_RATING
    error is ApiException && error.code == "legend_not_votable" -> Strings.TOAST_VOTE_LEGEND
    error is ApiException && error.code == "not_following" -> Strings.TOAST_VOTE_FOLLOW_FIRST
    error is ApiException && error.code == "performance_before_event_date" -> Strings.BK_ERR_BEFORE_DATE
    error is ApiException && error.code == "artist_not_in_company" -> Strings.BK_ERR_NOT_IN_COMPANY
    error is ApiException && error.code == "invalid_event_type_key" -> Strings.BK_ERR_EVENT_TYPE
    error is ApiException && error.code == "limit_exceeded" && error.limit != null -> Strings.TOAST_FOLLOW_LIMIT
    error is ApiException && error.code == "legend_not_followable" -> Strings.TOAST_FOLLOW_LEGEND
    error is ApiException && (error.status == 409 || error.code == "invalid_status_transition") -> Strings.BK_ERR_STALE
    error is ApiException && error.code == "invalid_file_type" -> Strings.ED_ERR_NOT_PDF
    error is ApiException && error.code == "file_too_large" -> Strings.ED_ERR_TOO_BIG
    error is com.djmetry.data.repository.InputException -> com.djmetry.ui.editor.editorErrorKey(error.code)
    else -> Strings.TOAST_FAILED
}

/** Текст ошибки действия — с числом, где оно есть («подписок уже 250»). */
internal fun com.djmetry.ui.i18n.I18nContext.actionError(error: Throwable): String {
    val key = actionErrorKey(error)
    val limit = (error as? ApiException)?.limit
    return if (key == Strings.TOAST_FOLLOW_LIMIT && limit != null) tWithArgs(key, arrayOf(limit)) else t(key)
}

/** Клавиши на планшете с клавиатурой: ← мимо, ↑ голос, → следить. */
internal fun keyAction(key: Key): SwipeAction? = when (key) {
    Key.DirectionLeft -> SwipeAction.Skip
    Key.DirectionRight -> SwipeAction.Follow
    Key.DirectionUp -> SwipeAction.Vote
    else -> null
}

/** Состояние колоды — общее для самой колоды и боковых панелей планшета. */
@Stable
internal class DeckState(
    private val repo: DiscoverRepository,
    /** Экранный scope: только обновление карточек и тосты (ушли с вкладки — обновлять нечего). */
    private val scope: CoroutineScope,
    /** Запросы подписки и голоса — уровня приложения: свайп и сразу другая вкладка не отменяют их. */
    private val work: CoroutineScope = scope,
) {
    var source by mutableStateOf(DeckSource.Top)
    var reload by mutableStateOf(0)
    val cards = mutableStateListOf<RankedArtist>()
    var loading by mutableStateOf(true)
    var failed by mutableStateOf(false)

    // Итог подборки для финальной карточки: просмотрено, подписки, голоса; пропущенные — для второго круга
    var seen by mutableStateOf(0)
    var followed by mutableStateOf(0)
    var voted by mutableStateOf(0)
    val skipped = mutableStateListOf<RankedArtist>()
    /** Сейчас идёт второй круг по пропущенным. */
    var skippedRound by mutableStateOf(false)
    /** Заранее загруженная следующая подборка — переход с финальной карточки без ожидания. */
    private val prefetched = mutableMapOf<DeckSource, List<RankedArtist>>()

    private var loadedFor: Pair<DeckSource, Int>? = null

    /** Загрузить подборку, если она ещё не загружена (возврат на вкладку не перезагружает колоду). */
    suspend fun ensureLoaded() {
        val key = source to reload
        if (loadedFor == key) return
        loadedFor = key
        load()
    }

    suspend fun load() {
        loading = true
        failed = false
        seen = 0; followed = 0; voted = 0; skipped.clear(); skippedRound = false
        val ready = prefetched.remove(source)
        (ready?.let { Result.success(it) } ?: repo.deck(source))
            .onSuccess { cards.clear(); cards.addAll(it) }
            .onFailure { failed = true }
        loading = false
    }

    /** Превью следующей подборки (аватары на финальной карточке) — и сразу кладём её про запас. */
    suspend fun preview(next: DeckSource): List<RankedArtist> =
        // Ошибку не кэшируем: иначе следующая подборка открылась бы сразу финальной карточкой «0 просмотрено»
        prefetched[next] ?: repo.deck(next).getOrNull()?.also { prefetched[next] = it }.orEmpty()

    /** Второй круг — только пропущенные (подписанных не возвращаем: они в «Подписках»). */
    fun replaySkipped() {
        val again = skipped.toList()
        skipped.clear(); seen = 0; followed = 0; voted = 0
        cards.clear(); cards.addAll(again)
        skippedRound = true
    }

    /**
     * Действие над карточкой. Свайп всегда листает дальше; в TOP 10 уже отмеченное не дублируем
     * (подписан — вправо просто дальше). Результат — для тоста через [onResult].
     */
    fun act(artist: RankedArtist, action: SwipeAction, alreadyFollowing: Boolean = false, alreadyVoted: Boolean = false, onResult: (success: Boolean, error: Throwable?) -> Unit) {
        // Двойной свайп / тап по уже ушедшей карточке — ничего не делаем (раньше — второй follow и двойной счётчик)
        if (!cards.remove(artist)) return
        seen++
        when (action) {
            SwipeAction.Skip -> skipped.add(artist)
            SwipeAction.Follow -> if (!alreadyFollowing) followed++
            SwipeAction.Vote -> if (!alreadyVoted) voted++
        }
        if ((action == SwipeAction.Follow && alreadyFollowing) || (action == SwipeAction.Vote && alreadyVoted)) return
        if (action == SwipeAction.Skip) return
        work.launch {
            val r = if (action == SwipeAction.Follow) repo.follow(artist) else repo.vote(artist.spotifyArtistId, artist.name, artist.imageUrl).map { }
            // Не прошло — карточка возвращается и счётчик откатывается (одинаково для подписки и голоса)
            scope.launch {
                r.fold({ onResult(true, null) }, {
                    onResult(false, it)
                    cards.add(0, artist); seen--
                    if (action == SwipeAction.Follow) followed-- else voted--
                })
            }
        }
    }

    /** Кнопки на карточке TOP 10: «Вы следите» — отписаться, «Ваш голос» — снять. Карточка остаётся. */
    fun toggleOff(artist: RankedArtist, follow: Boolean, onResult: (success: Boolean, error: Throwable?) -> Unit) {
        scope.launch {
            val r = if (follow) repo.unfollow(artist.spotifyArtistId) else repo.removeVote(artist.spotifyArtistId).map { }
            r.fold({ onResult(true, null) }, { onResult(false, it) })
        }
    }
}

/**
 * «Открытия» во ViewModel: колода (подборка, прогресс, итог, второй круг) и режим (колода / подписки / карта)
 * переживают смену вкладки, открытие поиска и поворот — раньше всё начиналось с первой карточки.
 */
internal class DiscoverViewModel(repo: DiscoverRepository, app: CoroutineScope, initialMode: Int) : androidx.lifecycle.ViewModel() {
    val deck = DeckState(repo, viewModelScope, app)
    /** 0 — колода, 1 — подписки, 2 — карта диджеев. */
    var mode by mutableStateOf(initialMode)
}

/** Вкладка «Открытия»: колода карточек + список подписок. На планшете — с боковыми панелями. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun DiscoverTab(
    onOpenSearch: () -> Unit, resetKey: Int = 0, onMapFullScreen: (Boolean) -> Unit = {}, initialMode: Int = 0,
    openFollowingKey: Int = 0, onFollowingOpened: () -> Unit = {},
) {
    val i18n = useI18n()
    val layout = LocalLayoutClass.current
    val vm = com.djmetry.ui.search.appViewModel<DiscoverViewModel> { org.koin.core.parameter.parametersOf(initialMode) }
    // 0 — колода, 1 — подписки, 2 — карта диджеев
    var mode by vm::mode
    LaunchedEffect(resetKey) { if (resetKey > 0) mode = 0 }
    // Разовое событие: оболочка обнуляет ключ, иначе каждый возврат на вкладку снова открывал «Подписки»
    LaunchedEffect(openFollowingKey) { if (openFollowingKey > 0) { mode = 1; onFollowingOpened() } }
    // На телефоне карта — во весь экран: шапка с вкладками скрыта, назад — кнопкой на карте, «#» или системным «Назад»
    val mapFullScreen = mode == 2 && layout == LayoutClass.Compact
    androidx.compose.ui.backhandler.BackHandler(enabled = mapFullScreen) { mode = 0 }
    LaunchedEffect(mapFullScreen) { onMapFullScreen(mapFullScreen) }
    DisposableEffect(Unit) { onDispose { onMapFullScreen(false) } }
    var toast by remember { mutableStateOf<String?>(null) }
    val deck = vm.deck
    LaunchedEffect(deck.source, deck.reload) { deck.ensureLoaded() }

    LaunchedEffect(toast) {
        if (toast != null) { delay(2400); toast = null }
    }
    val mineFollows by LocalAppContainer.current.discover.follows.collectAsState()
    val mineVotes by LocalAppContainer.current.discover.votes.collectAsState()
    val act = { artist: RankedArtist, action: SwipeAction ->
        val f = mineFollows.any { it.spotifyArtistId == artist.spotifyArtistId }
        val v = artist.spotifyArtistId in mineVotes
        deck.act(artist, action, alreadyFollowing = f, alreadyVoted = v) { ok, error ->
            if (action == SwipeAction.Skip) return@act
            toast = when {
                !ok -> i18n.actionError((error ?: Exception()))
                action == SwipeAction.Follow -> i18n.tWithArgs(Strings.TOAST_FOLLOWED, arrayOf(artist.name))
                else -> i18n.tWithArgs(Strings.TOAST_VOTED, arrayOf(artist.name))
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            if (!mapFullScreen) DiscoverHeader(
                mode = mode, onMode = { mode = it }, deck = deck, chipsInline = layout == LayoutClass.Expanded,
                follows = LocalAppContainer.current.discover.follows.collectAsState().value.size, onOpenSearch = onOpenSearch,
            )
            when {
                mode == 2 -> com.djmetry.ui.djmap.DjMapScreen(modifier = Modifier.weight(1f), onSwipes = if (mapFullScreen) ({ mode = 0 }) else null)
                mode == 1 -> FollowingList(onToast = { toast = it })
                layout == LayoutClass.Expanded -> Row(Modifier.fillMaxSize().padding(start = 10.dp, end = 24.dp)) {
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        DeckArea(deck, act, Modifier.weight(1f).widthIn(max = 520.dp), onFollowing = { mode = 1 }) { toast = it }
                    }
                    Spacer(Modifier.width(24.dp))
                    Column(
                        Modifier.width(340.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        VotesPanel(deck)
                        NextInDeckPanel(deck, act)
                        TopTenPanel()
                    }
                }
                layout == LayoutClass.Medium -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    DeckChips(deck)
                    DeckArea(deck, act, Modifier.weight(1f).widthIn(max = 600.dp), onFollowing = { mode = 1 }) { toast = it }
                    Row(
                        Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Box(Modifier.weight(1f)) { VotesPanel(deck) }
                        Box(Modifier.weight(1f)) { NextInDeckPanel(deck, act) }
                    }
                }
                // Телефон: подборка — стеклянная плашка поверх карточки, карточке достаётся вся высота
                else -> Box(Modifier.fillMaxSize()) {
                    DeckArea(deck, act, Modifier.fillMaxSize(), onFollowing = { mode = 1 }) { toast = it }
                    DeckSourcePicker(deck, Modifier.align(Alignment.TopStart).padding(start = 30.dp, top = 26.dp))
                }
            }
        }
        AnimatedVisibility(
            visible = toast != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).padding(top = 64.dp),
        ) {
            Text(
                toast.orEmpty(),
                color = DJMetryColors.Background, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp).shadow(12.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp)).background(DJMetryColors.Accent).padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

/**
 * Шапка «Открытий», вариант A (design/navigation/header-variants.html): крупный заголовок раздела и справа три кнопки —
 * подписки (сердце, точка — если есть подписки), карта, поиск. Повторное нажатие на активную — назад к колоде.
 * На десктопе подборки стоят в той же строке, на телефоне — плашкой на карточке ([DeckSourcePicker]).
 */
@Composable
private fun DiscoverHeader(mode: Int, onMode: (Int) -> Unit, deck: DeckState, chipsInline: Boolean, follows: Int, onOpenSearch: () -> Unit) {
    val hasFollows = follows > 0
    val i18n = useI18n()
    val layout = LocalLayoutClass.current
    Row(
        Modifier.fillMaxWidth().padding(start = if (layout.isTablet) 28.dp else 20.dp, end = if (layout.isTablet) 24.dp else 14.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (mode != 0) Icon(
            Icons.AutoMirrored.Filled.ArrowBack, i18n.t(Strings.ARTIST_BACK), tint = DJMetryColors.Text,
            modifier = Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button) { onMode(0) }.padding(8.dp),
        )
        val title = i18n.t(when (mode) { 1 -> Strings.SEG_FOLLOWING; 2 -> Strings.MAP_TAB; else -> Strings.SEG_DISCOVER })
        Row(if (chipsInline && mode == 0) Modifier else Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            AutoSizeText(title, TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold), color = DJMetryColors.Text, minFontSize = 16.sp, modifier = Modifier.weight(1f, fill = false))
            // «Подписки 24» — сколько артистов в подписках
            if (mode == 1 && follows > 0) Text(" $follows", color = DJMetryColors.Muted, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (chipsInline && mode == 0) {
            Spacer(Modifier.width(8.dp))
            DeckChipsRow(deck, Modifier.weight(1f))
        }
        HeaderButton(if (mode == 1) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, i18n.t(Strings.SEG_FOLLOWING), active = mode == 1, dot = hasFollows && mode != 1) { onMode(if (mode == 1) 0 else 1) }
        HeaderButton(Icons.Outlined.Map, i18n.t(Strings.MAP_TAB), active = mode == 2) { onMode(if (mode == 2) 0 else 2) }
        HeaderButton(Icons.Outlined.Search, i18n.t(Strings.TAB_SEARCH), active = false, onClick = onOpenSearch)
    }
}

/** Круглая кнопка шапки 44 dp; активная — зелёная, [dot] — зелёная точка-метка. */
@Composable
internal fun HeaderButton(icon: ImageVector, label: String, active: Boolean, dot: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(if (active) DJMetryColors.Accent else DJMetryColors.Panel)
            .border(1.dp, if (active) DJMetryColors.Accent else DJMetryColors.Border, CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (active) DJMetryColors.Background else DJMetryColors.Text, modifier = Modifier.size(22.dp))
        if (dot) Box(Modifier.align(Alignment.TopEnd).padding(top = 9.dp, end = 9.dp).size(8.dp).clip(CircleShape).background(DJMetryColors.Accent))
    }
}

/** Подборки колоды: ключ текста и иконка. TOP 10 — не переводится. */
internal fun deckSourceIcon(source: DeckSource): ImageVector = when (source) {
    DeckSource.Top -> Icons.Outlined.MilitaryTech
    DeckSource.Talents -> Icons.Outlined.AutoAwesome
    DeckSource.Rising -> Icons.AutoMirrored.Outlined.TrendingUp
    DeckSource.Breakthrough -> Icons.Outlined.RocketLaunch
    DeckSource.Stable -> Icons.Outlined.HorizontalRule
    DeckSource.Losing -> Icons.AutoMirrored.Outlined.TrendingDown
}

@Composable
private fun deckSourceLabel(source: DeckSource): String {
    val i18n = useI18n()
    return when (source) {
        DeckSource.Talents -> i18n.t(Strings.CHIP_TALENTS)
        DeckSource.Rising -> i18n.t(Strings.CHIP_RISING)
        DeckSource.Breakthrough -> i18n.t(Strings.CHIP_BREAKTHROUGH)
        DeckSource.Stable -> i18n.t(Strings.CHIP_STABLE)
        DeckSource.Losing -> i18n.t(Strings.CHIP_LOSING)
        DeckSource.Top -> "TOP $DECK_TOP_SIZE"
    }
}

/** Телефон: стеклянная плашка подборки поверх фото; тап — меню из четырёх подборок. */
@Composable
private fun DeckSourcePicker(deck: DeckState, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.height(38.dp).clip(CircleShape).background(DJMetryColors.Background.copy(alpha = 0.7f))
                .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape).clickable(role = Role.DropdownList) { open = true }
                .padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(deckSourceIcon(deck.source), null, tint = DJMetryColors.Accent, modifier = Modifier.size(18.dp))
            Text(deckSourceLabel(deck.source), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Outlined.ExpandMore, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = DJMetryColors.PanelStrong) {
            DeckSource.entries.forEach { src ->
                val on = src == deck.source
                DropdownMenuItem(
                    text = { Text(deckSourceLabel(src), color = if (on) DJMetryColors.Accent else DJMetryColors.Text, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = { Icon(deckSourceIcon(src), null, tint = if (on) DJMetryColors.Accent else DJMetryColors.Muted) },
                    onClick = { open = false; deck.source = src },
                )
            }
        }
    }
}

/** Десктоп: подборки чипами в строке шапки; не помещаются — листаются. */
@Composable
private fun DeckChipsRow(deck: DeckState, modifier: Modifier) {
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(DeckSource.entries) { src -> DeckChip(deck, src) }
    }
}

@Composable
private fun DeckChip(deck: DeckState, src: DeckSource) {
    val selected = src == deck.source
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(if (selected) DJMetryColors.Accent else DJMetryColors.Panel)
            .border(1.dp, if (selected) DJMetryColors.Accent else Color.White.copy(alpha = 0.07f), RoundedCornerShape(20.dp))
            .clickable(role = Role.Tab) { deck.source = src }.padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(deckSourceIcon(src), null, tint = if (selected) DJMetryColors.Background else DJMetryColors.Muted, modifier = Modifier.size(16.dp))
        Text(deckSourceLabel(src), color = if (selected) DJMetryColors.Background else DJMetryColors.Muted, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Планшет-портрет: подборки отдельной строкой под шапкой. */
@Composable
private fun DeckChips(deck: DeckState) {
    LazyRow(contentPadding = PaddingValues(horizontal = 28.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(DeckSource.entries) { src -> DeckChip(deck, src) }
    }
}

/** Стопка карточек + кнопки. Стрелки на клавиатуре дублируют свайпы. */
@Composable
private fun DeckArea(deck: DeckState, act: (RankedArtist, SwipeAction) -> Unit, modifier: Modifier, onFollowing: () -> Unit = {}, onToast: (String) -> Unit = {}) {
    val i18n = useI18n()
    val follows by LocalAppContainer.current.discover.follows.collectAsState()
    val votes by LocalAppContainer.current.discover.votes.collectAsState()
    val isFollowed = { a: RankedArtist -> follows.any { it.spotifyArtistId == a.spotifyArtistId } }
    val isVoted = { a: RankedArtist -> a.spotifyArtistId in votes }
    val openArtist = LocalArtistNavigator.current
    val focus = remember { FocusRequester() }
    // Закрыли то, что было поверх, — фокус снова у колоды (стрелки работают сразу)
    val covered = com.djmetry.ui.components.LocalCoveredByOverlay.current
    LaunchedEffect(covered) { if (!covered) runCatching { focus.requestFocus() } }
    Column(
        modifier
            .fillMaxWidth()
            .padding(bottom = LocalBottomClearance.current)
            .focusRequester(focus)
            .onPreviewKeyEvent { event ->
                val action = keyAction(event.key)
                if (!covered && event.type == KeyEventType.KeyDown && action != null && deck.cards.isNotEmpty()) {
                    act(deck.cards.first(), action); true
                } else false
            }
            .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
            when {
                deck.loading -> Box(Modifier.fillMaxSize().shimmer(RoundedCornerShape(28.dp))) {
                    Column(Modifier.align(Alignment.BottomStart).padding(20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SkeletonBox(Modifier.size(120.dp, 24.dp), RoundedCornerShape(12.dp))
                        Box(Modifier.fillMaxWidth(0.7f).height(34.dp).shimmer(RoundedCornerShape(10.dp)))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SkeletonCircle(44.dp); Spacer(Modifier.width(12.dp)); SkeletonLine(0.5f, 12.dp)
                        }
                    }
                }
                deck.failed -> EmptyDeck(i18n.t(Strings.HOME_ERROR), null, i18n.t(Strings.HOME_RETRY)) { deck.reload++ }
                // Подборка просмотрена — не тупик, а итог и следующий шаг (deck-end-variants.html, вариант A)
                deck.cards.isEmpty() -> DeckEndCard(deck, onFollowing)
                else -> {
                    // Рисуем снизу вверх: верхняя карточка — последняя
                    deck.cards.take(3).reversed().forEach { artist ->
                        val depth = deck.cards.indexOf(artist)
                        key(artist.spotifyArtistId) {
                            SwipeCard(artist, depth, onAction = { act(artist, it) }, onOpen = { openArtist(artist.spotifyArtistId) },
                                followed = isFollowed(artist), voted = isVoted(artist))
                        }
                    }
                }
            }
        }
        if (!deck.loading && !deck.failed && deck.cards.isNotEmpty()) {
            val top = deck.cards.first()
            // TOP 10: кнопки показывают состояние — голос подсвечен (тап снимает), сердце обводкой (тап — отписаться)
            val f = isFollowed(top)
            val v = isVoted(top)
            val off = { follow: Boolean ->
                deck.toggleOff(top, follow) { ok, e -> onToast(if (ok) i18n.tWithArgs(if (follow) Strings.DE_UNFOLLOWED else Strings.DE_UNVOTED, arrayOf(top.name)) else i18n.actionError((e ?: Exception()))) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundAction(Icons.Filled.Close, i18n.t(Strings.ACTION_SKIP), DJMetryColors.LowScore, DJMetryColors.Panel) { act(top, SwipeAction.Skip) }
                RoundAction(Icons.Filled.KeyboardDoubleArrowUp, i18n.t(Strings.ACTION_VOTE), Orange, if (v) DJMetryColors.PanelStrong else DJMetryColors.Panel, ring = if (v) Orange else null) {
                    if (v) off(false) else act(top, SwipeAction.Vote)
                }
                RoundAction(Icons.Filled.Favorite, i18n.t(Strings.ACTION_FOLLOW), if (f) DJMetryColors.Accent else DJMetryColors.Background, if (f) DJMetryColors.PanelStrong else DJMetryColors.Accent, big = true, ring = if (f) DJMetryColors.Accent else null) {
                    if (f) off(true) else act(top, SwipeAction.Follow)
                }
            }
            Text(i18n.t(Strings.DECK_HINT), color = DJMetryColors.Muted, fontSize = 11.5.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

// ───────── Боковые панели планшета ─────────

@Composable
private fun SidePanel(title: String, action: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(DJMetryColors.Panel)
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(24.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = DJMetryColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            action?.let { Text(it, color = DJMetryColors.Accent, fontSize = 12.sp) }
        }
        content()
    }
}

/** Голоса пользователя: 3 слота. Имена берём из подписок, колоды и TOP — /vote/status отдаёт только ID. */
@Composable
private fun VotesPanel(deck: DeckState) {
    val i18n = useI18n()
    val repo = LocalAppContainer.current.discover
    val scope = rememberCoroutineScope()
    val votes by repo.votes.collectAsState()
    val follows by repo.follows.collectAsState()
    val openArtist = LocalArtistNavigator.current
    SidePanel(i18n.t(Strings.YOUR_VOTES), "${votes.size}/${DiscoverRepository.MAX_VOTES}") {
        votes.forEach { id ->
            val followed = follows.firstOrNull { it.spotifyArtistId == id }
            val card = deck.cards.firstOrNull { it.spotifyArtistId == id }
            PanelRow(
                imageUrl = followed?.imageUrl ?: card?.imageUrl,
                title = followed?.name ?: card?.name ?: "…",
                subtitle = null,
                trailing = { Icon(Icons.Filled.KeyboardDoubleArrowUp, i18n.t(Strings.ACTION_VOTE), tint = Orange, modifier = Modifier.clip(CircleShape).clickable { scope.launch { repo.removeVote(id) } }.padding(6.dp)) },
                onClick = { openArtist(id) },
            )
        }
        repeat((DiscoverRepository.MAX_VOTES - votes.size).coerceAtLeast(0)) {
            Box(
                Modifier.fillMaxWidth().height(48.dp).border(1.5.dp, DJMetryColors.Border, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.ArrowUpward, null, tint = DJMetryColors.Muted, modifier = Modifier.size(15.dp))
                    Text(i18n.t(Strings.ACTION_VOTE), color = DJMetryColors.Muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun NextInDeckPanel(deck: DeckState, act: (RankedArtist, SwipeAction) -> Unit) {
    val i18n = useI18n()
    val next = deck.cards.drop(1).take(3)
    if (next.isEmpty()) return
    val openArtist = LocalArtistNavigator.current
    SidePanel(i18n.t(Strings.NEXT_IN_DECK), null) {
        next.forEach { artist ->
            PanelRow(
                imageUrl = artist.imageUrl,
                title = artist.name,
                subtitle = artist.genres.firstOrNull(),
                trailing = {
                    artist.trend?.score7d?.takeIf { it > 0 }?.let { Text("+${formatDelta(it)}", color = DJMetryColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                        ?: artist.djmetryScore?.let { Text(formatDelta(it), color = DJMetryColors.Text, fontSize = 13.sp) }
                },
                onClick = { openArtist(artist.spotifyArtistId) },
            )
        }
    }
}

@Composable
private fun TopTenPanel() {
    val container = LocalAppContainer.current
    // Тот же кэш, что у вкладки «Рейтинг» — без лишнего запроса; null — ещё грузится (скелетон, без рывка)
    val top by produceState<List<RatingRow>?>(null) {
        value = container.rating.top100().getOrNull()?.take(5).orEmpty()
    }
    val rows = top
    if (rows != null && rows.isEmpty()) return
    val openArtist = LocalArtistNavigator.current
    SidePanel("DJMetry TOP 10", "TOP 100") {
        if (rows == null) {
            repeat(5) { i ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SkeletonBox(Modifier.size(42.dp), RoundedCornerShape(12.dp))
                    Box(Modifier.weight(1f).padding(horizontal = 12.dp)) { SkeletonLine(listOf(0.7f, 0.55f, 0.62f, 0.5f, 0.66f)[i], 12.dp) }
                    SkeletonBox(Modifier.size(38.dp, 13.dp), RoundedCornerShape(5.dp))
                }
            }
        } else rows.forEach { row ->
            PanelRow(
                imageUrl = row.imageUrl,
                title = "${row.position}  ${row.name}",
                subtitle = null,
                trailing = { row.score?.let { Text(formatScore(it), color = DJMetryColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) } },
                onClick = { row.spotifyArtistId?.let(openArtist) },
            )
        }
    }
}

@Composable
private fun PanelRow(imageUrl: String?, title: String, subtitle: String?, trailing: @Composable () -> Unit, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(imageUrl, 42.dp, cornerRadius = 12.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, color = DJMetryColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, color = DJMetryColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        trailing()
    }
}

@Composable
private fun SwipeCard(artist: RankedArtist, depth: Int, onAction: (SwipeAction) -> Unit, onOpen: () -> Unit, followed: Boolean = false, voted: Boolean = false) {
    val i18n = useI18n()
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val isTop = depth == 0
    // Состояния (не значения): читаются только при отрисовке слоя — карточка не пересобирается каждый кадр
    val stackScale = animateFloatAsStateCompat(1f - depth * 0.05f)
    val stackShift = animateFloatAsStateCompat(depth * 14f)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val coverSize = maxOf(maxWidth, maxHeight)
        val threshold = width * 0.28f
        // pointerInput живёт, пока не сменится артист: обработчики и ширину читаем свежими, а не с первого кадра
        // (иначе свайп после подписки в карточке артиста слал повторный follow; поворот — старый порог)
        val widthNow by rememberUpdatedState(width)
        val onActionNow by rememberUpdatedState(onAction)
        val onOpenNow by rememberUpdatedState(onOpen)

        fun fly(action: SwipeAction) {
            scope.launch {
                val w = widthNow
                val target = when (action) {
                    SwipeAction.Follow -> Offset(w * 1.6f, offsetY.value - 80f)
                    SwipeAction.Skip -> Offset(-w * 1.6f, offsetY.value - 80f)
                    SwipeAction.Vote -> Offset(offsetX.value, -w * 2.2f)
                }
                launch { offsetX.animateTo(target.x, tween(260)) }
                offsetY.animateTo(target.y, tween(260))
                onActionNow(action)
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                // Сдвиг, поворот, тень и скругление — в одном слое и только на этапе отрисовки: во время свайпа и полёта
                // карточка не пересобирается (раньше — каждый кадр целиком, рывки на iPhone 11 и старше)
                .graphicsLayer {
                    translationX = offsetX.value
                    translationY = offsetY.value + stackShift.value.dp.toPx()
                    rotationZ = offsetX.value / 22f
                    scaleX = stackScale.value
                    scaleY = stackScale.value
                    shadowElevation = (if (isTop) 26.dp else 10.dp).toPx()
                    ambientShadowColor = Color.Black
                    spotShadowColor = Color.Black
                    shape = RoundedCornerShape(30.dp)
                    clip = true
                }
                .background(DJMetryColors.PanelStrong)
                .then(
                    // Тап без сдвига — карточка артиста; движение — свайп
                    if (!isTop) Modifier else Modifier.pointerInput(artist.spotifyArtistId) { detectTapGestures(onTap = { onOpenNow() }) }.pointerInput(artist.spotifyArtistId) {
                        detectDragGestures(
                            onDrag = { change, drag ->
                                change.consume()
                                scope.launch {
                                    offsetX.snapTo(offsetX.value + drag.x)
                                    offsetY.snapTo(offsetY.value + drag.y)
                                }
                            },
                            onDragEnd = {
                                val action = swipeDecision(offsetX.value, offsetY.value, widthNow * 0.28f)
                                if (action != null) fly(action) else scope.launch {
                                    launch { offsetX.animateTo(0f, spring(Spring.DampingRatioMediumBouncy)) }
                                    offsetY.animateTo(0f, spring(Spring.DampingRatioMediumBouncy))
                                }
                            },
                        )
                    }
                ),
        ) {
            CoverImage(artist.imageUrl, coverSize, cornerRadius = 0.dp, modifier = Modifier.align(Alignment.Center))
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.42f to Color.Transparent, 1f to DJMetryColors.Background.copy(alpha = 0.97f))))

            // TOP 10: отметки справа сверху — видны сразу и не закрывают лицо (слева сверху — выбор подборки)
            if (followed || voted) Column(Modifier.align(Alignment.TopEnd).padding(14.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (followed) StateBadge(Icons.Filled.Favorite, i18n.t(Strings.ARTIST_FOLLOWING), DJMetryColors.Accent)
                if (voted) StateBadge(Icons.Filled.KeyboardDoubleArrowUp, i18n.t(Strings.DE_YOUR_VOTE), Orange)
            }
            if (isTop) {
                Stamp(i18n.t(Strings.STAMP_FOLLOW), DJMetryColors.Accent, -14f, { (offsetX.value / threshold).coerceIn(0f, 1f) }, Modifier.align(Alignment.TopStart).padding(22.dp))
                Stamp(i18n.t(Strings.STAMP_SKIP), DJMetryColors.LowScore, 14f, { (-offsetX.value / threshold).coerceIn(0f, 1f) }, Modifier.align(Alignment.TopEnd).padding(22.dp))
                Stamp(i18n.t(Strings.STAMP_VOTE), Orange, 0f, { stampVoteAlpha(offsetX.value, offsetY.value, threshold) }, Modifier.align(Alignment.Center))
            }

            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(20.dp)) {
                // Тренд: на планшете и десктопе — три метрики над именем (trend-card-variants.html, A); на телефоне фото
                // маленькое — метрики строкой под Score, над именем только жанр и место (trend-compact-variants.html, A)
                val compact = LocalLayoutClass.current == LayoutClass.Compact
                val trend = artist.trend?.takeIf { hasTrendMetrics(it) }
                if (!compact) trend?.let { TrendPills(it, Modifier.padding(bottom = 8.dp)) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    artist.genres.firstOrNull()?.let { Tag(it, DJMetryColors.Text, Color.White.copy(alpha = 0.12f), null) }
                    artist.position?.let { Tag(if (compact) "#$it" else "#$it DJMetry", DJMetryColors.Accent, DJMetryColors.Accent.copy(alpha = 0.18f), null) }
                }
                Text(
                    artist.name, color = DJMetryColors.Text, fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp),
                )
                artist.djmetryScore?.let { score ->
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ScoreRing(score)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text("DJMetry Score", color = DJMetryColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            if (compact && trend != null) TrendLine(trend)
                            else Text(i18n.t(Strings.SCORE_CAPTION), color = DJMetryColors.Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

/** Штамп «Голос»: проявляется при свайпе вверх, гаснет при уводе вбок. */
internal fun stampVoteAlpha(dx: Float, dy: Float, threshold: Float): Float =
    (-dy / (threshold * 1.2f) - abs(dx) / (threshold * 2)).coerceIn(0f, 1f) + 0f

@Composable
private fun animateFloatAsStateCompat(target: Float): State<Float> =
    androidx.compose.animation.core.animateFloatAsState(target, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow), label = "stack")

@Composable
private fun Stamp(text: String, color: Color, rotation: Float, alpha: () -> Float, modifier: Modifier) {
    Text(
        text, color = color, fontSize = 24.sp, fontWeight = FontWeight.Black,
        // Прозрачность читается при отрисовке: штамп проявляется за пальцем без пересборки карточки
        modifier = modifier.graphicsLayer { this.alpha = alpha(); rotationZ = rotation }
            .border(3.dp, color, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/** Телефон: «24ч −34.8 · 7д −34.8 · рост −34.8%» мелко под Score — подписи приглушённые, значения по знаку. */
@Composable
internal fun TrendLine(t: com.djmetry.api.models.TrendInfo) {
    val i18n = useI18n()
    val parts = trendParts(t, i18n.t(Strings.TREND_24H), i18n.t(Strings.TREND_7D), i18n.t(Strings.TREND_GROWTH))
    val text = androidx.compose.ui.text.buildAnnotatedString {
        parts.forEachIndexed { i, p ->
            if (i > 0) withStyle(androidx.compose.ui.text.SpanStyle(color = DJMetryColors.Muted)) { append(" · ") }
            withStyle(androidx.compose.ui.text.SpanStyle(color = DJMetryColors.Muted)) { append(p.label + " ") }
            val color = when (p.sign) { 1 -> DJMetryColors.Accent; -1 -> DJMetryColors.LowScore; else -> DJMetryColors.Text }
            withStyle(androidx.compose.ui.text.SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(p.value) }
        }
    }
    Text(text, fontSize = 12.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

/** Три стеклянные метрики тренда. Не помещаются в ряд (узкий телефон, длинный язык) — переносятся. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TrendPills(t: com.djmetry.api.models.TrendInfo, modifier: Modifier = Modifier) {
    val i18n = useI18n()
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        t.score24h?.let { TrendPill(i18n.t(Strings.TREND_24H), it, arrow = true) }
        t.score7d?.let { TrendPill(i18n.t(Strings.TREND_7D), it, arrow = true) }
        t.growthRate?.let { TrendPill(i18n.t(Strings.TREND_GROWTH), it, arrow = false, suffix = "%") }
    }
}

@Composable
private fun TrendPill(label: String, value: Double, arrow: Boolean, suffix: String = "") {
    val color = when { value > 0.05 -> DJMetryColors.Accent; value < -0.05 -> DJMetryColors.LowScore; else -> DJMetryColors.Text }
    Row(
        Modifier.height(30.dp).clip(CircleShape).background(DJMetryColors.Background.copy(alpha = 0.6f))
            .border(1.dp, Color.White.copy(alpha = 0.13f), CircleShape).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, color = DJMetryColors.Muted, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (arrow) Icon(
            when { value > 0.05 -> Icons.Filled.ArrowUpward; value < -0.05 -> Icons.Filled.ArrowDownward; else -> Icons.Filled.Remove },
            null, tint = color, modifier = Modifier.size(14.dp),
        )
        Text(signedTrend(value) + suffix, color = color, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Tag(text: String, color: Color, background: Color, icon: ImageVector?) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(background).padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, null, tint = color, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(4.dp)) }
        Text(text, color = color, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ScoreRing(score: Double) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(score) { progress.animateTo((score / 100).toFloat().coerceIn(0f, 1f), tween(900)) }
    Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = 4.dp.toPx()
            val arc = Size(size.width - stroke, size.height - stroke)
            val tl = Offset(stroke / 2, stroke / 2)
            drawArc(DJMetryColors.Border, 0f, 360f, false, tl, arc, style = Stroke(stroke))
            drawArc(DJMetryColors.Accent, -90f, 360f * progress.value, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("${score.toInt()}", color = DJMetryColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, tint: Color, background: Color, big: Boolean = false, ring: Color? = null, onClick: () -> Unit) {
    val size = if (big) 66.dp else 58.dp
    Box(
        Modifier.size(size)
            .shadow(if (big) 16.dp else 6.dp, CircleShape, ambientColor = if (big) DJMetryColors.Accent else Color.Black, spotColor = if (big) DJMetryColors.Accent else Color.Black)
            .clip(CircleShape).background(background)
            .border(if (ring != null) 2.dp else 1.dp, ring ?: if (big) Color.Transparent else DJMetryColors.Border, CircleShape)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(if (big) 30.dp else 26.dp))
    }
}

@Composable
private fun EmptyDeck(title: String, text: String?, action: String, onAction: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
        Icon(DJMetryLogo.HashMark, null, tint = DJMetryColors.Border, modifier = Modifier.size(70.dp, 63.dp))
        Text(title, color = DJMetryColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 18.dp))
        text?.let { Text(it, color = DJMetryColors.Muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp)) }
        Button(
            onClick = onAction, modifier = Modifier.padding(top = 18.dp), shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DJMetryColors.Accent, contentColor = DJMetryColors.Background),
        ) { Text(action, fontWeight = FontWeight.SemiBold) }
    }
}

/** Ширина, от которой «Подписки» — список + панель выбранного артиста (вариант C). */
internal const val FOLLOWING_DETAIL_MIN_DP = 900f

/**
 * «Подписки», вариант C «Мастер-деталь» (design/discover/following-variants.html): сортировка, компактный список
 * (фото, имя, подписчики, значок голоса). Узко — тап открывает карточку артиста; широко — справа панель
 * выбранного: фото, жанр и страна, Score, место, подписчики, «Открыть карточку», «Голос», «Отписаться».
 */
@Composable
private fun FollowingList(onToast: (String) -> Unit) {
    val i18n = useI18n()
    val repo = LocalAppContainer.current.discover
    val scope = rememberCoroutineScope()
    val follows by repo.follows.collectAsState()
    val votes by repo.votes.collectAsState()
    val openArtist = LocalArtistNavigator.current
    var loaded by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    var sort by remember { mutableStateOf(FollowSort.Recent) }
    // Фильтр «Голоса» (design/following/votes-filter-variants.html, вариант A)
    var votesOnly by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(attempt) { failed = !repo.refreshMine(force = true); loaded = true }
    val voteFlight = com.djmetry.ui.components.rememberSingleFlight()
    val toggleVote: (FollowedArtist) -> Unit = { artist ->
        // Два тапа по «Голос» — один запрос (раньше второй уходил с устаревшим списком голосов)
        voteFlight.run(scope) {
            val result = if (artist.spotifyArtistId in votes) repo.removeVote(artist.spotifyArtistId) else repo.vote(artist.spotifyArtistId, artist.name.orEmpty(), artist.imageUrl)
            result.onFailure { onToast(i18n.actionError(it)) }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth.value >= FOLLOWING_DETAIL_MIN_DP
        val pad = if (wide) 28.dp else 16.dp
        when {
            !loaded -> Column(Modifier.readableWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { repeat(7) { SkeletonListRow(it, leading = false, trailing = false) } }
            follows.isEmpty() && failed -> Box(Modifier.fillMaxSize().padding(bottom = LocalBottomClearance.current), contentAlignment = Alignment.Center) {
                com.djmetry.ui.components.LoadFailedRow({ loaded = false; attempt++ }, Modifier.readableWidth().padding(16.dp))
            }
            follows.isEmpty() -> Box(Modifier.fillMaxSize().padding(bottom = LocalBottomClearance.current), contentAlignment = Alignment.Center) {
                Text(i18n.t(Strings.FOLLOWING_EMPTY), color = DJMetryColors.Muted, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
            }
            else -> {
                val sorted = remember(follows, sort, votes, votesOnly) { filterFollows(sortFollows(follows, sort), votes, votesOnly) }
                var selectedId by remember { mutableStateOf<String?>(null) }
                val selected = sorted.firstOrNull { it.spotifyArtistId == selectedId } ?: sorted.firstOrNull()
                Row(Modifier.fillMaxSize().padding(horizontal = pad), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    LazyColumn(
                        modifier = Modifier.weight(1f).then(if (wide) Modifier else Modifier.readableWidth()),
                        contentPadding = PaddingValues(top = 8.dp, bottom = LocalBottomClearance.current + 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item { FollowSortChips(sort, { sort = it }, votesOnly, votes.size) { votesOnly = !votesOnly } }
                        if (sorted.isEmpty()) item {
                            Text(i18n.t(Strings.FOLLOWING_NO_VOTES), color = DJMetryColors.Muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
                        }
                        items(sorted, key = { it.spotifyArtistId }) { artist ->
                            FollowRow(artist, voted = artist.spotifyArtistId in votes, selected = wide && artist.spotifyArtistId == selected?.spotifyArtistId) {
                                if (wide) selectedId = artist.spotifyArtistId else openArtist(artist.spotifyArtistId)
                            }
                        }
                    }
                    if (wide && selected != null) Box(Modifier.width(420.dp).padding(top = 8.dp, bottom = LocalBottomClearance.current + 16.dp)) {
                        FollowDetail(
                            selected, voted = selected.spotifyArtistId in votes,
                            onOpen = { openArtist(selected.spotifyArtistId) }, onVote = { toggleVote(selected) },
                            onUnfollow = { voteFlight.run(scope) { repo.unfollow(selected.spotifyArtistId).onFailure { onToast(i18n.actionError(it)) } } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowSortChips(sort: FollowSort, onSort: (FollowSort) -> Unit, votesOnly: Boolean, votesCount: Int, onVotes: () -> Unit) {
    val i18n = useI18n()
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // «Голоса N/3» — фильтр, а не сортировка: первым, чтобы на телефоне был виден без прокрутки строки
        Row(
            Modifier.clip(CircleShape).background(if (votesOnly) Orange.copy(alpha = 0.16f) else DJMetryColors.Panel)
                .border(1.dp, if (votesOnly) Color.Transparent else Orange.copy(alpha = 0.45f), CircleShape)
                .clickable(role = Role.Checkbox, onClick = onVotes).padding(horizontal = 13.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Filled.KeyboardDoubleArrowUp, null, tint = Orange, modifier = Modifier.size(16.dp))
            Text(i18n.tWithArgs(Strings.FOLLOW_FILTER_VOTES, arrayOf(votesCount, com.djmetry.data.repository.DiscoverRepository.MAX_VOTES)), color = Orange, fontSize = 13.5.sp,
                fontWeight = if (votesOnly) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        listOf(
            Triple(FollowSort.Recent, Strings.FOLLOW_SORT_RECENT, Icons.Outlined.Schedule),
            Triple(FollowSort.Name, Strings.FOLLOW_SORT_NAME, Icons.Outlined.SortByAlpha),
            Triple(FollowSort.Popular, Strings.FOLLOW_SORT_POPULAR, Icons.Outlined.Groups),
        ).forEach { (s, key, icon) ->
            val on = s == sort
            Row(
                Modifier.clip(CircleShape).background(if (on) DJMetryColors.Accent.copy(alpha = 0.14f) else DJMetryColors.Panel)
                    .border(1.dp, if (on) Color.Transparent else DJMetryColors.Border, CircleShape).clickable(role = Role.Tab) { onSort(s) }
                    .padding(horizontal = 13.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(icon, null, tint = if (on) DJMetryColors.Accent else DJMetryColors.Muted, modifier = Modifier.size(16.dp))
                Text(i18n.t(key), color = if (on) DJMetryColors.Accent else DJMetryColors.Muted, fontSize = 13.5.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Строка списка: фото, имя, подписчики Spotify, значок голоса (если отдан) и «›». Выбранная (широкий экран) — зелёная рамка. */
@Composable
private fun FollowRow(artist: FollowedArtist, voted: Boolean, selected: Boolean, onClick: () -> Unit) {
    val i18n = useI18n()
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(DJMetryColors.Panel).background(if (selected) DJMetryColors.Accent.copy(alpha = 0.08f) else Color.Transparent)
            .border(1.dp, if (selected) DJMetryColors.Accent else Color.White.copy(alpha = 0.05f), shape)
            .clickable(role = Role.Button, onClick = onClick).padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(artist.imageUrl, 48.dp, cornerRadius = 14.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(artist.name.orEmpty(), color = DJMetryColors.Text, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            artist.followers?.let { Text("${com.djmetry.ui.artist.compactCount(it)} · Spotify", color = DJMetryColors.Muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (voted) Row(
            Modifier.padding(end = 6.dp).clip(CircleShape).background(Orange.copy(alpha = 0.15f)).padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(Icons.Filled.KeyboardDoubleArrowUp, i18n.t(Strings.YOUR_VOTE), tint = Orange, modifier = Modifier.size(15.dp))
            Text(i18n.t(Strings.FOLLOW_VOTE_BADGE), color = Orange, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = DJMetryColors.Muted, modifier = Modifier.size(22.dp))
    }
}

/** Панель выбранного артиста (широкий экран): основное подгружается из карточки артиста. */
@Composable
private fun FollowDetail(artist: FollowedArtist, voted: Boolean, onOpen: () -> Unit, onVote: () -> Unit, onUnfollow: () -> Unit) {
    val i18n = useI18n()
    val container = LocalAppContainer.current
    val details by produceState<com.djmetry.api.models.ArtistDetailsResponse?>(null, artist.spotifyArtistId) {
        value = null // другой артист — не показывать Score и место прежнего
        value = container.artists.details(artist.spotifyArtistId, i18n.locale.code).getOrNull()
    }
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(DJMetryColors.Panel).border(1.dp, Color.White.copy(alpha = 0.06f), shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClick = onOpen)) {
            CoverImage(artist.imageUrl, maxOf(maxWidth, 280.dp), cornerRadius = 0.dp, modifier = Modifier.align(Alignment.Center))
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to DJMetryColors.Background.copy(alpha = 0.95f))))
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text(artist.name.orEmpty(), color = DJMetryColors.Text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val country = details?.country?.let { c -> if (c.length == 2) com.djmetry.i18n.localizedCountryName(c, i18n.locale.code) ?: c else c }
                val sub = listOfNotNull(details?.genres?.firstOrNull(), country).joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, color = DJMetryColors.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val cell: @Composable (String, String, Color) -> Unit = { label, value, color ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(DJMetryColors.Background.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(label, color = DJMetryColors.Muted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    AutoSizeText(value, TextStyle(fontSize = 19.sp, fontWeight = FontWeight.ExtraBold), color = color, minFontSize = 12.sp)
                }
            }
            cell("Score", details?.djmetryScore?.let(::formatScore) ?: "—", DJMetryColors.Accent)
            cell(i18n.t(Strings.PROFILE_PLACE).replaceFirstChar { it.uppercase() }, details?.position?.let { "#$it" } ?: "—", DJMetryColors.Text)
            cell(i18n.t(Strings.PROFILE_FOLLOWERS).replaceFirstChar { it.uppercase() }, (artist.followers ?: details?.followers)?.let { com.djmetry.ui.artist.compactCount(it) } ?: "—", DJMetryColors.Text)
        }
        Row(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(15.dp)).background(DJMetryColors.Accent).clickable(role = Role.Button, onClick = onOpen),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Person, null, tint = DJMetryColors.Background, modifier = Modifier.size(20.dp))
            Text(i18n.t(Strings.RATING_OPEN_CARD), color = DJMetryColors.Background, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(15.dp)).background(if (voted) Orange.copy(alpha = 0.15f) else DJMetryColors.PanelStrong)
                    .border(1.dp, if (voted) Orange.copy(alpha = 0.45f) else DJMetryColors.Border, RoundedCornerShape(15.dp)).clickable(role = Role.Button, onClick = onVote),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.KeyboardDoubleArrowUp, null, tint = Orange, modifier = Modifier.size(20.dp))
                Text(i18n.t(if (voted) Strings.YOUR_VOTE else Strings.ACTION_VOTE), color = if (voted) Orange else DJMetryColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
            }
            Row(
                Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(15.dp)).border(1.dp, DJMetryColors.LowScore.copy(alpha = 0.4f), RoundedCornerShape(15.dp)).clickable(role = Role.Button, onClick = onUnfollow),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.HeartBroken, null, tint = DJMetryColors.LowScore, modifier = Modifier.size(19.dp))
                Text(i18n.t(Strings.UNFOLLOW), color = DJMetryColors.LowScore, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}



/** Отметка на фото карточки: «Вы следите» / «Ваш голос». */
@Composable
private fun StateBadge(icon: ImageVector, text: String, color: Color) {
    Row(
        Modifier.clip(CircleShape).background(DJMetryColors.Background.copy(alpha = 0.72f)).border(1.dp, color.copy(alpha = 0.55f), CircleShape)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Описание подборки для превью «Дальше» на финальной карточке. */
private fun deckSourceDesc(source: DeckSource): String = when (source) {
    DeckSource.Top -> Strings.DE_DESC_TOP
    DeckSource.Talents -> Strings.DE_DESC_TALENTS
    DeckSource.Rising -> Strings.DE_DESC_RISING
    DeckSource.Breakthrough -> Strings.DE_DESC_BREAK
    DeckSource.Stable -> Strings.DE_DESC_STABLE
    DeckSource.Losing -> Strings.DE_DESC_LOSING
}

/**
 * Финальная карточка подборки (deck-end-variants.html, вариант A): итог (просмотрено · подписки · голоса), превью
 * следующей подборки с аватарами и кнопка «Смотреть …» (свайп вправо — то же), второй круг по пропущенным, «Подписки».
 */
@Composable
private fun DeckEndCard(deck: DeckState, onFollowing: () -> Unit) {
    val i18n = useI18n()
    val next = deck.source.next()
    val preview by produceState<List<RankedArtist>?>(null, next) { value = null; value = deck.preview(next) }
    val go = { deck.source = next }
    var drag by remember { mutableStateOf(0f) }
    val shape = RoundedCornerShape(30.dp)
    Column(
        Modifier.fillMaxSize().graphicsLayer { translationX = drag; rotationZ = drag / 30f }
            .clip(shape).background(Brush.linearGradient(listOf(Color(0xFF16314A), Color(0xFF0D1626))))
            .border(1.dp, DJMetryColors.Accent.copy(alpha = 0.25f), shape)
            .pointerInput(next) {
                detectHorizontalDragGestures(onDragEnd = { if (drag > size.width * 0.25f) go(); drag = 0f }) { change, dx -> change.consume(); drag = (drag + dx).coerceAtLeast(0f) }
            }
            .verticalScroll(rememberScrollState()).padding(20.dp)
            // Телефон: слева сверху на карточке — выбор подборки, заголовок итога — ниже него
            .padding(top = if (LocalLayoutClass.current == LayoutClass.Compact) 48.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.CheckCircle, null, tint = DJMetryColors.Accent, modifier = Modifier.size(18.dp))
            Text(
                (if (deck.skippedRound) i18n.t(Strings.DE_SKIPPED_DONE) else i18n.tWithArgs(Strings.DE_DONE, arrayOf(deckSourceLabel(deck.source)))).uppercase(),
                color = DJMetryColors.Accent, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp, maxLines = 2,
            )
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(DJMetryColors.Background.copy(alpha = 0.5f)).padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceAround) {
            EndStat(deck.seen, i18n.t(Strings.DE_VIEWED), DJMetryColors.Text)
            EndStat(deck.followed, i18n.t(Strings.DE_FOLLOWS), DJMetryColors.Accent)
            EndStat(deck.voted, i18n.t(Strings.DE_VOTES), Orange)
        }
        Column(
            // Без weight: внутри verticalScroll на маленьком экране с крупным шрифтом остаток отрицательный — блок схлопывался
            Modifier.fillMaxWidth().heightIn(min = 150.dp).clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(Color(0x2E7DA7FF), Color(0x1F5EE6A8)))).border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
                .clickable(role = Role.Button, onClick = go).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
        ) {
            Text(i18n.t(Strings.DE_NEXT).uppercase(), color = DJMetryColors.Muted, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(deckSourceIcon(next), null, tint = DJMetryColors.Accent, modifier = Modifier.size(26.dp))
                Text(deckSourceLabel(next), color = DJMetryColors.Text, fontSize = 26.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val pv = preview
            if (pv == null) Box(Modifier.size(150.dp, 44.dp).shimmer(RoundedCornerShape(22.dp)))
            else Row { pv.take(4).forEachIndexed { i, a ->
                CoverImage(a.imageUrl, 44.dp, cornerRadius = 22.dp, modifier = Modifier.offset(x = (-12 * i).dp).border(2.dp, Color(0xFF13233A), CircleShape))
            } }
            Text(i18n.t(deckSourceDesc(next)), color = DJMetryColors.Muted, fontSize = 13.sp)
        }
        Button(
            onClick = go, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DJMetryColors.Accent, contentColor = DJMetryColors.Background),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(i18n.tWithArgs(Strings.DE_WATCH, arrayOf(deckSourceLabel(next))), fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (deck.skipped.isNotEmpty()) EndSecondary(Icons.Outlined.Replay, i18n.tWithArgs(Strings.DE_SKIPPED, arrayOf(deck.skipped.size.toString())), Modifier.weight(1f)) { deck.replaySkipped() }
            EndSecondary(Icons.Filled.Favorite, i18n.t(Strings.DE_FOLLOWING), Modifier.weight(1f), onFollowing)
        }
    }
}

@Composable
private fun EndStat(value: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), color = color, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = DJMetryColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EndSecondary(icon: ImageVector, text: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(48.dp).clip(RoundedCornerShape(16.dp)).background(DJMetryColors.PanelStrong).border(1.dp, DJMetryColors.Border, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = DJMetryColors.Text, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = DJMetryColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
