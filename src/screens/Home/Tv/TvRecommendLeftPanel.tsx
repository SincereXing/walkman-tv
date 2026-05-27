/**
 * 首页推荐页左侧播放区，参考图：大封面 → 歌名 → 歌手 → 歌词 → 细进度条 → 药丸形控件
 * 封面尺寸根据容器高度动态计算，确保整体不超出屏幕
 */
import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { View, PanResponder, type LayoutChangeEvent } from 'react-native'
import TvPressable from '@/components/tv/TvPressable'
import { Icon } from '@/components/common/Icon'
import Text from '@/components/common/Text'
import Image from '@/components/common/Image'
import { createStyle } from '@/utils/tools'
import { usePlayerMusicInfo, useProgress, useIsPlay, useStatusText } from '@/store/player/hook'
import { useSettingValue } from '@/store/setting/hook'
import { useLrcPlay } from '@/plugins/lyric'
import { navigations } from '@/navigation'
import commonState from '@/store/common/state'
import { COMPONENT_IDS, NAV_SHEAR_NATIVE_IDS } from '@/config/constant'
import { setLoadErrorPicUrl, setMusicInfo } from '@/core/player/playInfo'
import { playNext, playPrev, togglePlay } from '@/core/player/player'
import { scaleSizeW } from '@/utils/pixelRatio'
import { useDrag } from '@/utils/hooks'
import { usePageVisible } from '@/store/common/hook'

const C_TEXT_SEC = '#a0a0b0'
const C_LYRIC = '#888888'
const C_TRACK = '#333333'

// 封面以外的元素预估高度
// 无歌曲：提示文字 + 按钮 ≈ 80
// 有歌曲：标题 + 歌手 + 歌词 + 进度 + 按钮 ≈ 150
const NON_COVER_H_EMPTY = 80
const NON_COVER_H_PLAYING = 162

const styles = createStyle({
  root: {
    flex: 1,
    width: '100%',
    paddingHorizontal: 16,
    paddingTop: 0,
    paddingBottom: 6,
  },
  coverTouch: {
    width: '100%',
    borderRadius: 12,
    overflow: 'hidden',
    marginBottom: 6,
    backgroundColor: 'rgba(0,0,0,0.25)',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 6 },
    shadowOpacity: 0.35,
    shadowRadius: 10,
    elevation: 8,
  },
  coverImg: {
    width: '100%',
    height: '100%',
    borderRadius: 12,
  },
  title: {
    fontWeight: '700',
    marginBottom: 2,
  },
  artist: {
    marginBottom: 6,
  },
  lyric: {
    marginBottom: 8,
    minHeight: 18,
  },
  trackWrap: {
    marginBottom: 10,
  },
  trackBg: {
    height: 2,
    width: '100%',
    backgroundColor: C_TRACK,
    borderRadius: 1,
    overflow: 'hidden',
  },
  trackFill: {
    height: '100%',
    backgroundColor: '#ffffff',
    borderRadius: 1,
    shadowColor: '#fff',
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.9,
    shadowRadius: 6,
  },
  pressBar: {
    position: 'absolute',
    left: 0,
    top: -12,
    height: 28,
    width: '100%',
  },
  controlsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 8,
  },
  btn: {
    width: 28,
    height: 28,
    borderRadius: 14,
    justifyContent: 'center',
    alignItems: 'center',
  },
  btnPlay: {
    width: 28,
    height: 28,
    borderRadius: 14,
    justifyContent: 'center',
    alignItems: 'center',
  },
  emptyHint: {
    marginTop: 8,
  },
})

const LyricLine = memo(({ autoUpdate }: { autoUpdate: boolean }) => {
  const { text } = useLrcPlay(autoUpdate)
  const statusText = useStatusText()
  const isPlay = useIsPlay()
  const line = isPlay ? (text || statusText) : statusText
  return (
    <Text
      numberOfLines={2}
      size={13}
      color={C_LYRIC}
      style={styles.lyric}
    >{line}
    </Text>
  )
})

const HtmlProgress = memo(({ autoUpdate }: { autoUpdate: boolean }) => {
  const allowSeek = useSettingValue('common.allowProgressBarSeek')
  const { progress, maxPlayTime } = useProgress(autoUpdate)
  const durationRef = useRef(maxPlayTime)
  useEffect(() => {
    durationRef.current = maxPlayTime
  }, [maxPlayTime])

  const [draging, setDraging] = useState(false)
  const [dragProgress, setDragProgress] = useState(0)

  const onSetProgress = useCallback((p: number) => {
    global.app_event.setProgress(p * durationRef.current)
  }, [])

  const {
    onLayout,
    onDragStart,
    onDragEnd,
    onDrag,
  } = useDrag(onSetProgress, setDraging, setDragProgress)

  const panResponder = useRef(
    PanResponder.create({
      onStartShouldSetPanResponderCapture: () => Boolean(allowSeek),
      onMoveShouldSetPanResponderCapture: () => Boolean(allowSeek),
      onPanResponderMove: (_e, g) => {
        onDrag(g.dx)
      },
      onPanResponderGrant: (e, g) => {
        onDragStart(g.dx, e.nativeEvent.locationX)
      },
      onPanResponderRelease: () => {
        onDragEnd()
      },
    }),
  ).current

  const displayP = draging ? dragProgress : progress
  const fillW = `${displayP * 100}%` as `${number}%`

  return (
    <View style={styles.trackWrap} onLayout={onLayout}>
      <View style={styles.trackBg} pointerEvents="none">
        <View style={[styles.trackFill, { width: fillW }]} />
      </View>
      {
        allowSeek
          ? (
              <View style={styles.pressBar} {...panResponder.panHandlers} />
            )
          : null
      }
    </View>
  )
})

export default memo(() => {
  const musicInfo = usePlayerMusicInfo()
  const [autoUpdate, setAutoUpdate] = useState(true)
  const [coverSize, setCoverSize] = useState(0)
  const layoutRef = useRef({ width: 0, height: 0 })
  usePageVisible([COMPONENT_IDS.home], useCallback((visible) => {
    setAutoUpdate(visible)
  }, []))

  const hasTrack = Boolean(musicInfo.id)

  const recalcCover = useCallback((w: number, h: number, playing: boolean) => {
    if (w === 0 || h === 0) return
    const nonCoverH = playing ? NON_COVER_H_PLAYING : NON_COVER_H_EMPTY
    const maxByWidth = w - 32
    const maxByHeight = h - nonCoverH
    const size = Math.max(80, Math.floor(Math.min(maxByWidth, maxByHeight)))
    setCoverSize(size)
  }, [])

  const onRootLayout = useCallback((e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout
    layoutRef.current = { width, height }
    recalcCover(width, height, hasTrack)
  }, [hasTrack, recalcCover])

  // 当 hasTrack 变化时重新计算封面尺寸
  useEffect(() => {
    const { width, height } = layoutRef.current
    recalcCover(width, height, hasTrack)
  }, [hasTrack, recalcCover])

  const coverInnerStyle = useMemo(() => ({
    width: coverSize,
    height: coverSize,
  }), [coverSize])

  const onOpenDetail = useCallback(() => {
    if (!musicInfo.id) return
    navigations.pushPlayDetailScreen(commonState.componentIds.home!)
  }, [musicInfo.id])

  const onImgErr = useCallback((url: string | number) => {
    setLoadErrorPicUrl(url as string)
    setMusicInfo({ pic: null })
  }, [])

  return (
    <View style={styles.root} onLayout={onRootLayout}>
      <TvPressable style={styles.coverTouch} onPress={onOpenDetail} tvFocusVariant="mutedFill">
        <View style={coverInnerStyle}>
          {musicInfo.pic
            ? (
                <Image
                  url={musicInfo.pic}
                  nativeID={NAV_SHEAR_NATIVE_IDS.playDetail_pic}
                  style={styles.coverImg}
                  onError={onImgErr}
                />
              )
            : (
                <View style={[styles.coverImg, { backgroundColor: 'rgba(255,255,255,0.06)', justifyContent: 'center', alignItems: 'center', width: coverSize, height: coverSize }]}>
                  <Icon name="album" size={48} color="rgba(255,255,255,0.2)" />
                </View>
              )}
        </View>
      </TvPressable>

      {hasTrack
        ? (
            <>
              <Text style={styles.title} size={16} color="#fff" numberOfLines={2}>
                {musicInfo.name}
              </Text>
              {musicInfo.singer
                ? (
                    <Text style={styles.artist} size={12} color={C_TEXT_SEC} numberOfLines={1}>
                      {musicInfo.singer}
                    </Text>
                  )
                : null}
              <LyricLine autoUpdate={autoUpdate} />
              <HtmlProgress autoUpdate={autoUpdate} />
            </>
          )
        : (
            <Text style={styles.emptyHint} size={15} color={C_TEXT_SEC}>
              暂无播放，去推荐里点歌开始吧
            </Text>
          )}

      <View style={styles.controlsRow}>
        <TvPressable style={styles.btn} onPress={() => { void playPrev() }} tvFocusVariant="mutedFill">
          <Icon name="prevMusic" color="#fff" size={scaleSizeW(16)} />
        </TvPressable>
        <TvPressable style={styles.btnPlay} onPress={togglePlay} tvFocusVariant="mutedFill">
          <PlayPauseIcon />
        </TvPressable>
        <TvPressable style={styles.btn} onPress={() => { void playNext() }} tvFocusVariant="mutedFill">
          <Icon name="nextMusic" color="#fff" size={scaleSizeW(16)} />
        </TvPressable>
      </View>
    </View>
  )
})

const PlayPauseIcon = memo(() => {
  const isPlay = useIsPlay()
  return (
    <Icon
      name={isPlay ? 'pause' : 'play'}
      color="#fff"
      size={scaleSizeW(18)}
    />
  )
})
