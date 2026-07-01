package com.walkman.tv.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.walkman.tv.data.model.Track
import com.walkman.tv.playback.LyricParser
import com.walkman.tv.playback.RepeatMode
import com.walkman.tv.ui.appContainer
import com.walkman.tv.ui.components.Artwork
import com.walkman.tv.ui.components.TvFocusable
import com.walkman.tv.ui.components.TvPill
import com.walkman.tv.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
  val controller = appContainer.playbackController
  val state by controller.state.collectAsState()
  val lyrics by controller.lyrics.collectAsState()
  val love by appContainer.libraryStore.love.collectAsState()
  val settings by appContainer.settingsStore.settings.collectAsState()
  val lyricSize = settings.lyricSize
  val scope = rememberCoroutineScope()
  val context = androidx.compose.ui.platform.LocalContext.current
  var showMvQueue by remember { mutableStateOf(false) }
  val playFocus = remember { FocusRequester() }

  val track = state.currentTrack

  var showEqDialog by remember { mutableStateOf(false) }
  var showPicker by remember { mutableStateOf(false) }

  // 核心自适应算法：获取屏幕物理总高度（DP），彻底摆脱内部组件相互挤压的局限
  val configuration = androidx.compose.ui.platform.LocalConfiguration.current
  val screenHeight = configuration.screenHeightDp.dp
  // 强制将黑胶总盘面尺寸锚定为全屏高度的 67%（大屏自动等比放大，且视觉效果极为饱满）
  val vinylAbsoluteSize = screenHeight * 0.67f

  val wantsFocus = !state.isMv && !showMvQueue && !showEqDialog && !showPicker
  LaunchedEffect(wantsFocus) {
    if (wantsFocus) tryRequestFocus(playFocus)
  }

  BackHandler(enabled = true) {
    when {
      showMvQueue -> showMvQueue = false
      state.isMv -> controller.exitMv()
      else -> onClose()
    }
  }

  LaunchedEffect(Unit) {
    appContainer.events.menuKey.collect { showMvQueue = !showMvQueue }
  }

  if (state.isMv) {
    Box(modifier = modifier.fillMaxSize().background(AppColors.BgDeep)) {
      AndroidView(
        factory = { ctx -> PlayerView(ctx).apply { player = controller.player; useController = true } },
        modifier = Modifier.fillMaxSize(),
      )
      MvQueueDrawer(
        visible = showMvQueue,
        queue = state.queue,
        currentIndex = state.index,
        onSelect = { idx ->
          showMvQueue = false
          controller.playAt(idx)
        },
        modifier = Modifier.align(Alignment.CenterEnd),
      )
    }
    return
  }

  Box(modifier = modifier.fillMaxSize().background(AppColors.BgDeep)) {
    CoverBackdrop(picURL = track?.picURL)

    if (track == null) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("暂无播放", color = AppColors.TextSecondary)
      }
      return@Box
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp)) {
      // 主展示内容区 Row
      Row(modifier = Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
        // 左侧歌词自适应收缩区：吃掉除去右侧唱片尺寸后剩下的全部横向空间
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
          Text(
            track.name,
            color = AppColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            track.singer,
            color = AppColors.TextSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Spacer(Modifier.height(18.dp))
          LyricPane(
            lyrics,
            state.positionMs,
            lyricSize = lyricSize,
            modifier = Modifier.fillMaxWidth().weight(1f),
          )
        }

        Spacer(Modifier.width(36.dp))

        // 右侧黑胶盘面容器：直接赋予正方形物理尺寸命令，从根本上锁死比例，粉碎椭圆化风险
        Box(
          modifier = Modifier.size(vinylAbsoluteSize),
          contentAlignment = Alignment.Center,
        ) {
          VinylDisc(
            picURL = track.picURL,
            isPlaying = state.isPlaying,
            modifier = Modifier.fillMaxSize()
          )
        }
      }

      Spacer(Modifier.height(10.dp))
      val audioLevel by controller.audioLevel.collectAsState()
      com.walkman.tv.ui.components.Waveform(
        isPlaying = state.isPlaying,
        level = audioLevel,
        modifier = Modifier.fillMaxWidth().height(40.dp),
      )
      Spacer(Modifier.height(6.dp))
      ProgressBar(state.positionMs, state.durationMs, onSeek = { controller.seekTo(it) })
      Spacer(Modifier.height(14.dp))
      val audioSpec by controller.audioSpec.collectAsState()
      TransportBar(
        state = state,
        love = love,
        audioSpec = audioSpec,
        onTogglePlay = { controller.togglePlay() },
        onPrev = { controller.prev() },
        onNext = { controller.next() },
        onCycleRepeat = {
          val mode = state.repeatMode
          val shuffle = state.shuffle
          when {
            mode == RepeatMode.ALL && !shuffle -> {
              controller.setRepeatMode(RepeatMode.ONE)
            }
            mode == RepeatMode.ONE && !shuffle -> {
              controller.setRepeatMode(RepeatMode.ALL)
              if (!state.shuffle) controller.toggleShuffle()
            }
            else -> {
              controller.setRepeatMode(RepeatMode.ALL)
              if (state.shuffle) controller.toggleShuffle()
            }
          }
        },
        onToggleFav = { showPicker = true },
        onMv = {
          android.widget.Toast.makeText(context, "正在获取 MV…", android.widget.Toast.LENGTH_SHORT).show()
          scope.launch {
            val info = runCatching { appContainer.mvResolver.getMvUrl(track) }.getOrNull()
            val url = info?.bestUrl()
            if (url != null) {
              controller.playMvUrl(url)
            } else {
              android.widget.Toast.makeText(context, "暂无可用 MV", android.widget.Toast.LENGTH_SHORT).show()
            }
          }
        },
        onShowQueue = { showMvQueue = true },
        onTuneClick = { showEqDialog = true },
        playFocusRequester = playFocus,
      )
    }

    MvQueueDrawer(
      visible = showMvQueue,
      queue = state.queue,
      currentIndex = state.index,
      onSelect = { idx -> controller.playAt(idx) },
      modifier = Modifier.align(Alignment.CenterEnd),
    )

    if (showEqDialog) {
      EqualizerDialog(onDismiss = { showEqDialog = false })
    }
    if (showPicker) {
      com.walkman.tv.ui.components.PlaylistPickerDialog(
        track = track,
        onDismiss = { showPicker = false },
      )
    }
  }
}

// ============== Vinyl disc (自适应纯圆形画幅百分比微调) =====================================================================

@Composable
private fun VinylDisc(picURL: String?, isPlaying: Boolean, modifier: Modifier = Modifier) {
  val rotation = remember { Animatable(0f) }
  LaunchedEffect(isPlaying) {
    if (isPlaying) {
      while (true) {
        rotation.animateTo(
          rotation.value + 360f,
          animationSpec = tween(durationMillis = 30_000, easing = LinearEasing),
        )
      }
    }
  }
  val circle = androidx.compose.foundation.shape.CircleShape

  Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
    // 1) 外圈绿光晕 (填满全尺寸)
    Box(
      modifier = Modifier
        .fillMaxSize()
        .clip(circle)
        .background(
          Brush.radialGradient(
            colors = listOf(
              AppColors.AccentGreen.copy(alpha = 0.55f),
              AppColors.AccentGreen.copy(alpha = 0.20f),
              AppColors.AccentGreen.copy(alpha = 0.00f),
            ),
          ),
        ),
    )
    // 2) 黑胶主体盘面 (比例计算：300 / 360 ≈ 83.33%)
    Box(
      modifier = Modifier
        .fillMaxSize(0.8333f)
        .aspectRatio(1f)
        .graphicsLayer { rotationZ = rotation.value },
      contentAlignment = Alignment.Center,
    ) {
      VinylGrooves(modifier = Modifier.fillMaxSize())

      // 3) 内圈专辑封面 (比例计算：220 / 300 ≈ 73.33%)
      Box(
        modifier = Modifier
          .fillMaxSize(0.7333f)
          .aspectRatio(1f)
          .clip(circle)
          .background(AppColors.BgDeep),
      ) {
        Artwork(picURL, modifier = Modifier.fillMaxSize(), shape = circle)
      }
    }
    // 4) 中心金属轴孔 (比例计算：14 / 360 ≈ 3.88%)
    Box(
      modifier = Modifier
        .fillMaxSize(0.0388f)
        .aspectRatio(1f)
        .clip(circle)
        .background(Color.Black)
    )
  }
}

@Composable
private fun VinylGrooves(modifier: Modifier) {
  androidx.compose.foundation.Canvas(modifier = modifier.aspectRatio(1f)) {
    val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
    val outerRadius = size.minDimension / 2f
    val innerRadius = outerRadius * 0.7333f

    // 动态根据实时大小计算线条缩放比率，保证线条密度不变形
    val baseScale = outerRadius / 150.dp.toPx()

    drawCircle(color = Color(0xFF0E2818), radius = outerRadius, center = center)

    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(
          Color(0xFF0A1F12),
          Color(0xFF112F1C),
          Color(0xFF1B4528),
        ),
        center = center,
        radius = outerRadius,
      ),
      radius = outerRadius,
      center = center,
    )

    val grooveCount = 32
    for (i in 0 until grooveCount) {
      val frac = i / (grooveCount - 1).toFloat()
      val r = innerRadius + (outerRadius - innerRadius) * frac
      val alpha = 0.20f + 0.10f * ((i % 5) / 5f)
      val w = (if (i % 7 == 0) 1.2.dp.toPx() else 0.6.dp.toPx()) * baseScale
      drawCircle(
        color = Color.Black.copy(alpha = alpha),
        radius = r,
        center = center,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = w),
      )
    }

    drawCircle(
      color = AppColors.AccentGreen.copy(alpha = 0.45f),
      radius = innerRadius + 2.dp.toPx() * baseScale,
      center = center,
      style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx() * baseScale),
    )

    drawCircle(
      color = AppColors.AccentGreen.copy(alpha = 0.55f),
      radius = outerRadius - 1.dp.toPx() * baseScale,
      center = center,
      style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx() * baseScale),
    )
  }
}

// ============== 下方其余交互逻辑组件保持完全不变 =================================================================

@Composable
private fun EqualizerDialog(onDismiss: () -> Unit) {
  val eq = appContainer.equalizerManager
  val presets = remember { eq.presets }
  var selected by remember { mutableStateOf(eq.currentPresetIndex) }
  val activeRowFocus = remember { FocusRequester() }
  LaunchedEffect(Unit) {
    tryRequestFocus(activeRowFocus)
  }

  androidx.compose.ui.window.Dialog(
    onDismissRequest = onDismiss,
    properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Column(
      modifier = Modifier
        .width(360.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(AppColors.BgPanel)
        .padding(horizontal = 22.dp, vertical = 20.dp),
    ) {
      Text("均衡器", color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
      Spacer(Modifier.height(4.dp))
      Text(
        if (presets.isEmpty()) "本机不支持系统均衡器" else "选择一个预设，立即生效",
        color = AppColors.TextSecondary,
        fontSize = 12.sp,
      )
      Spacer(Modifier.height(14.dp))
      if (presets.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          presets.forEachIndexed { idx, label ->
            val active = idx == selected
            val rowModifier = Modifier
              .fillMaxWidth()
              .let { if (active) it.focusRequester(activeRowFocus) else it }
            TvFocusable(
              onClick = {
                selected = idx
                eq.applyPreset(idx)
              },
              modifier = rowModifier,
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  label,
                  color = if (active) AppColors.AccentGreen else AppColors.TextPrimary,
                  fontSize = 14.sp,
                  fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                  modifier = Modifier.weight(1f),
                )
                if (active) {
                  Text("✓", color = AppColors.AccentGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun TransportBar(
  state: com.walkman.tv.playback.PlaybackState,
  love: com.walkman.tv.data.model.Playlist,
  audioSpec: com.walkman.tv.playback.AudioSpec?,
  onTogglePlay: () -> Unit,
  onPrev: () -> Unit,
  onNext: () -> Unit,
  onCycleRepeat: () -> Unit,
  onToggleFav: () -> Unit,
  onMv: () -> Unit,
  onShowQueue: () -> Unit,
  onTuneClick: () -> Unit,
  playFocusRequester: FocusRequester? = null,
) {
  val track = state.currentTrack
  val faved = track?.let { t -> love.tracks.any { it.id == t.id } } ?: false
  val qualityFocus = remember { FocusRequester() }
  val repeatFocus = remember { FocusRequester() }
  val mvFocus = remember { FocusRequester() }
  val faveFocus = remember { FocusRequester() }
  val hasQuality = state.quality != null

  Box(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.align(Alignment.CenterStart),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      IconPill(
        Icons.Filled.Tune,
        onClick = onTuneClick,
        modifier = Modifier.focusProperties { left = FocusRequester.Cancel },
      )
      val displayed = com.walkman.tv.playback.displayQuality(state.quality, audioSpec)
      displayed?.let { q ->
        TvPill(
          onClick = { },
          selected = true,
          focusRequester = qualityFocus,
          modifier = Modifier.focusProperties { right = repeatFocus },
          contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        ) {
          Text(q.badgeLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        audioSpec?.let { spec ->
          Text(
            spec.displayText,
            color = AppColors.TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
          )
        }
      }
    }
    Row(
      modifier = Modifier.align(Alignment.Center),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      val repeatIcon = when {
        state.shuffle -> Icons.Filled.Shuffle
        state.repeatMode == RepeatMode.ONE -> Icons.Filled.RepeatOne
        else -> Icons.Filled.Repeat
      }
      IconPill(
        repeatIcon,
        active = true,
        focusRequester = repeatFocus,
        onClick = onCycleRepeat,
        modifier = if (hasQuality) Modifier.focusProperties { left = qualityFocus } else Modifier,
      )
      IconPill(Icons.Filled.SkipPrevious, onClick = onPrev)
      IconPill(
        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
        large = true,
        focusRequester = playFocusRequester,
        onClick = onTogglePlay,
      )
      IconPill(Icons.Filled.SkipNext, onClick = onNext)
      IconPill(
        if (faved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
        active = faved,
        focusRequester = faveFocus,
        onClick = onToggleFav,
        modifier = Modifier.focusProperties { right = mvFocus },
      )
    }
    Row(
      modifier = Modifier.align(Alignment.CenterEnd),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      TvPill(
        onClick = onMv,
        focusRequester = mvFocus,
        modifier = Modifier.focusProperties { left = faveFocus },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
      ) {
        Text("MV", fontSize = 12.sp, fontWeight = FontWeight.Bold)
      }
      IconPill(
        Icons.AutoMirrored.Filled.QueueMusic,
        onClick = onShowQueue,
        modifier = Modifier.focusProperties { right = FocusRequester.Cancel },
      )
    }
  }
}

private const val SCRUB_TAP_MS = 220L
private const val SCRUB_TAP_STEP_MS = 5_000L
private const val SCRUB_BASE_PER_SEC = 6_000f
private const val SCRUB_MAX_PER_SEC = 60_000f
private const val SCRUB_ACCEL_PER_SEC2 = 40_000f
private const val SCRUB_RELEASE_TIMEOUT_MS = 1_000L

@Composable
private fun ProgressBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
  var focused by remember { mutableStateOf(false) }
  var scrubDir by remember { mutableStateOf(0) }
  var previewMs by remember { mutableStateOf(0L) }
  var scrubStartPos by remember { mutableStateOf(0L) }
  var scrubStartAt by remember { mutableStateOf(0L) }
  var lastKeyAt by remember { mutableStateOf(0L) }

  fun commit() {
    if (scrubDir == 0) return
    val held = android.os.SystemClock.uptimeMillis() - scrubStartAt
    val target = if (held <= SCRUB_TAP_MS) {
      (scrubStartPos + scrubDir * SCRUB_TAP_STEP_MS).coerceIn(0L, durationMs)
    } else {
      previewMs.coerceIn(0L, durationMs)
    }
    onSeek(target)
    scrubDir = 0
  }

  LaunchedEffect(scrubDir) {
    if (scrubDir == 0) return@LaunchedEffect
    var lastT = android.os.SystemClock.uptimeMillis()
    while (scrubDir != 0) {
      withFrameNanos { }
      val now = android.os.SystemClock.uptimeMillis()
      val dt = (now - lastT).coerceAtLeast(0L)
      lastT = now
      val heldSec = (now - scrubStartAt) / 1000f
      val speed = (SCRUB_BASE_PER_SEC + SCRUB_ACCEL_PER_SEC2 * heldSec).coerceAtMost(SCRUB_MAX_PER_SEC)
      previewMs = (previewMs + scrubDir * speed * (dt / 1000f)).toLong().coerceIn(0L, durationMs)
      if (now - lastKeyAt > SCRUB_RELEASE_TIMEOUT_MS) commit()
    }
  }

  val shownMs = if (scrubDir != 0) previewMs else positionMs
  val fraction = if (durationMs > 0) (shownMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .onFocusChanged { fs ->
        focused = fs.isFocused
        if (!fs.isFocused && scrubDir != 0) commit()
      }
      .onPreviewKeyEvent { evt ->
        if (durationMs <= 0) return@onPreviewKeyEvent false
        val dir = when (evt.key) {
          Key.DirectionLeft -> -1
          Key.DirectionRight -> 1
          else -> return@onPreviewKeyEvent false
        }
        when (evt.type) {
          KeyEventType.KeyDown -> {
            lastKeyAt = android.os.SystemClock.uptimeMillis()
            if (scrubDir == 0) {
              scrubStartPos = positionMs
              previewMs = positionMs
              scrubStartAt = lastKeyAt
            }
            scrubDir = dir
            true
          }
          KeyEventType.KeyUp -> {
            commit(); true
          }
          else -> false
        }
      }
      .focusable(),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(if (focused) 6.dp else 3.dp)
        .clip(RoundedCornerShape(3.dp))
        .background(if (focused) AppColors.Card.copy(alpha = 0.9f) else AppColors.Card),
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth(fraction)
          .fillMaxHeight()
          .background(AppColors.AccentGreen),
      )
    }
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(
        fmt(shownMs),
        color = if (focused) AppColors.AccentGreen else AppColors.TextMuted,
        fontSize = 11.sp,
        fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
      )
      Text(fmt(durationMs), color = AppColors.TextMuted, fontSize = 11.sp)
    }
  }
}

@Composable
private fun LyricPane(
  lyrics: List<com.walkman.tv.playback.LyricLine>,
  positionMs: Long,
  lyricSize: com.walkman.tv.data.store.LyricSize,
  modifier: Modifier = Modifier,
) {
  if (lyrics.isEmpty()) {
    Box(modifier, contentAlignment = Alignment.Center) { Text("暂无歌词", color = AppColors.TextMuted) }
    return
  }
  val active = LyricParser.activeIndex(positionMs / 1000.0, lyrics)
  val listState = rememberLazyListState()
  LaunchedEffect(active) {
    if (active >= 0) runCatching { listState.animateScrollToItem(active, scrollOffset = -160) }
  }
  LazyColumn(
    state = listState,
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(14.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 80.dp),
  ) {
    itemsIndexed(lyrics) { i, line ->
      val isActive = i == active
      val distance = if (active >= 0) abs(i - active) else 0
      val a = if (isActive) 1f else (1f - distance * 0.22f).coerceAtLeast(0.18f)
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          line.text,
          color = if (isActive) AppColors.LyricActive else AppColors.TextSecondary,
          fontSize = if (isActive) lyricSize.activeSp.sp else lyricSize.inactiveSp.sp,
          fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
          textAlign = TextAlign.Center,
          modifier = Modifier.alpha(a),
        )
        line.translation?.let {
          Text(
            it,
            color = if (isActive) AppColors.LyricActive.copy(alpha = 0.7f) else AppColors.TextMuted,
            fontSize = lyricSize.translationSp.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(a),
          )
        }
      }
    }
  }
}

@Composable
private fun IconPill(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  active: Boolean = false,
  large: Boolean = false,
  focusRequester: FocusRequester? = null,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  TvPill(
    onClick = onClick,
    modifier = modifier,
    shape = androidx.compose.foundation.shape.CircleShape,
    contentPadding = androidx.compose.foundation.layout.PaddingValues(if (large) 14.dp else 10.dp),
    selected = active,
    focusRequester = focusRequester,
  ) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(if (large) 30.dp else 22.dp))
  }
}

private fun fmt(ms: Long): String {
  val s = (ms / 1000).toInt()
  return "%02d:%02d".format(s / 60, s % 60)
}

private suspend fun tryRequestFocus(target: FocusRequester) {
  repeat(5) {
    kotlinx.coroutines.delay(50)
    if (runCatching { target.requestFocus() }.isSuccess) return
  }
}

@Composable
private fun MvQueueDrawer(
  visible: Boolean,
  queue: List<Track>,
  currentIndex: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val listState = rememberLazyListState()
  val firstFocus = remember { FocusRequester() }

  LaunchedEffect(visible) {
    if (visible && queue.isNotEmpty()) {
      val target = currentIndex.coerceIn(0, queue.size - 1)
      runCatching { listState.scrollToItem(target) }
      delay(80)
      runCatching { firstFocus.requestFocus() }
    }
  }

  AnimatedVisibility(
    visible = visible,
    enter = slideInHorizontally(initialOffsetX = { it }),
    exit = slideOutHorizontally(targetOffsetX = { it }),
    modifier = modifier,
  ) {
    Box(modifier = Modifier.fillMaxHeight().width(360.dp).background(AppColors.BgPanel.copy(alpha = 0.95f))) {
      Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
          "播放队列 (${queue.size})",
          color = AppColors.TextPrimary,
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        LazyColumn(
          state = listState,
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxSize(),
        ) {
          itemsIndexed(queue, key = { _, t -> t.id }) { idx, track ->
            val isCurrent = idx == currentIndex
            QueueRow(
              track = track,
              index = idx,
              isCurrent = isCurrent,
              modifier = if (isCurrent) Modifier.focusRequester(firstFocus) else Modifier,
              onClick = { onSelect(idx) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun QueueRow(
  track: Track,
  index: Int,
  isCurrent: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  TvFocusable(onClick = onClick, modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier.width(22.dp),
        contentAlignment = Alignment.Center,
      ) {
        if (isCurrent) {
          com.walkman.tv.ui.components.MiniWaveform(
            modifier = Modifier.size(width = 18.dp, height = 14.dp),
          )
        } else {
          Text(
            "${index + 1}",
            color = AppColors.TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
          )
        }
      }
      Spacer(Modifier.width(8.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          track.name,
          color = if (isCurrent) AppColors.AccentGreen else AppColors.TextPrimary,
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          track.singer,
          color = AppColors.TextMuted,
          fontSize = 11.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

// ============== Cover backdrop =================================================================

@Composable
private fun CoverBackdrop(picURL: String?) {
  Box(modifier = Modifier.fillMaxSize()) {
    if (!picURL.isNullOrBlank()) {
      val context = androidx.compose.ui.platform.LocalContext.current
      coil.compose.AsyncImage(
        model = coil.request.ImageRequest.Builder(context)
          .data(picURL)
          .transformations(com.walkman.tv.ui.components.BlurTransformation(context, radius = 25f, sampling = 2f))
          .crossfade(400)
          .build(),
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
      )
      Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
      Box(
        modifier = Modifier.fillMaxSize().background(
          Brush.verticalGradient(
            0f to AppColors.BgDeep.copy(alpha = 0.55f),
            0.28f to Color.Transparent,
            0.72f to Color.Transparent,
            1f to AppColors.BgDeep.copy(alpha = 0.72f),
          ),
        ),
      )
    } else {
      Box(
        modifier = Modifier.fillMaxSize().background(
          Brush.verticalGradient(listOf(AppColors.BgPanel, AppColors.BgDeep)),
        ),
      )
    }
  }
}
