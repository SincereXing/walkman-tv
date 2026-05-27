import { memo, useCallback, useEffect, useMemo, useState } from 'react'
import { View, StyleSheet } from 'react-native'
import TvPressable from '@/components/tv/TvPressable'
import TvPlayerProgress from './TvPlayerProgress'
import { useIsPlay, usePlayerMusicInfo, useProgress } from '@/store/player/hook'
import Text from '@/components/common/Text'
import { useBufferProgress } from '@/plugins/player'
import { Icon } from '@/components/common/Icon'
import {
  playNext,
  playPrev,
  togglePlay,
  collectMusic,
  uncollectMusic,
} from '@/core/player/player'
import { TV_ACCENT_GREEN, TV_HTML_WAVE_BRIGHT } from '@/config/tvStyles'
import { toast } from '@/utils/tools'
import { useTvPlaybackSpectrum } from './useTvPlaybackSpectrum'
import TvPlayModeTriple from './TvPlayModeTriple'

import { getListMusics } from '@/core/list'
import { LIST_IDS } from '@/config/constant'
import playerState from '@/store/player/state'

/** 波形竖条（配色随 HTML 分层） */
const WaveBars = memo(({ heights, barColor }: { heights: number[], barColor: string }) => {
  return (
    <View style={waveStyles.wrap}>
      {heights.map((h, i) => {
        const px = 4 + h * 26
        const op = 0.15 + h * 0.55
        return (
          <View
            // eslint-disable-next-line react/no-array-index-key
            key={i}
            style={[waveStyles.bar, {
              height: px,
              opacity: op,
              backgroundColor: barColor,
            }]}
          />
        )
      })}
    </View>
  )
})

/** 波形仅反映音频频谱，不跟进度 */
const WaveSpectrumBlock = memo(({ heights }: {
  heights: number[]
}) => {
  return (
    <View style={waveStyles.block}>
      <WaveBars heights={heights} barColor={TV_HTML_WAVE_BRIGHT} />
    </View>
  )
})

const TimeLabel = ({ children }: { children: string }) => (
  <Text size={12} color="#888888">{children}</Text>
)

function shortQualityLabel(): string {
  const fullInfo = playerState.playMusicInfo.musicInfo
  if (!fullInfo) return '--'
  if ('progress' in fullInfo) return '--' // download item
  if (fullInfo.source === 'local') return '本地'
  const q = playerState.currentQuality
  if (!q) return '--'
  switch (q) {
    case '128k':
      return '128k'
    case '320k':
      return '320k'
    case 'flac':
      return 'FLAC'
    case 'flac24bit':
      return '24bit'
    case '192k':
      return '192k'
    case 'ape':
      return 'APE'
    case 'wav':
      return 'WAV'
    default:
      return '--'
  }
}

/**
 * 布局对齐参考 HTML：波形进度区 + 时间胶囊 + 单行居中控件（音质｜队列｜上一首｜白圆播放｜下一首｜收藏｜模式）
 */
export default memo(({ onOpenQueue, onOpenMv, mvLoading = false }: {
  onOpenQueue: () => void
  onOpenMv: () => void
  mvLoading?: boolean
}) => {
  const { maxPlayTimeStr, nowPlayTimeStr, progress, maxPlayTime } = useProgress()
  const buffered = useBufferProgress()
  const spectrumHeights = useTvPlaybackSpectrum(true)
  const musicInfoFull = usePlayerMusicInfo()
  const qualityChip = useMemo(
    () => shortQualityLabel(),
    [musicInfoFull],
  )

  const [inLove, setInLove] = useState(false)

  const syncLove = useCallback(() => {
    const m = musicInfoFull
    if (!m?.id) {
      setInLove(false)
      return
    }
    void getListMusics(LIST_IDS.LOVE).then((list) => {
      setInLove(list.some(x => x.id === m.id))
    })
  }, [musicInfoFull])

  useEffect(() => {
    syncLove()
    const h = (ids: string[]) => {
      if (!musicInfoFull?.id) return
      if (ids.includes(LIST_IDS.LOVE)) syncLove()
    }
    global.app_event.on('myListMusicUpdate', h)
    return () => global.app_event.off('myListMusicUpdate', h)
  }, [musicInfoFull?.id, syncLove])

  const tip = (msg: string) => {
    toast(msg, 'short')
  }

  const toggleLove = () => {
    if (!musicInfoFull?.id) {
      tip('暂无播放歌曲')
      return
    }
    if (inLove) {
      uncollectMusic()
      tip('已从「我的收藏」移除')
    } else {
      collectMusic()
      tip('已收藏到「我的收藏」列表')
    }
    syncLove()
  }

  return (
    <View style={styles.root}>
      {/* 波形区 */}
      <View style={styles.waveMount}>
        <WaveSpectrumBlock heights={spectrumHeights} />
      </View>

      {/* 进度条 */}
      <View style={styles.progressTouch}>
        <View style={styles.progressTrack}>
          <TvPlayerProgress progress={progress} duration={maxPlayTime} buffered={buffered} />
        </View>
      </View>

      {/* 时间文字 */}
      <View style={styles.timeRow}>
        <TimeLabel>{nowPlayTimeStr}</TimeLabel>
        <TimeLabel>{maxPlayTimeStr}</TimeLabel>
      </View>

      {/* 三段式控件 */}
      <View style={styles.controlsRow}>
        {/* 左侧：音质显示 */}
        <View style={styles.controlsLeft}>
          <View style={chip.hitHtml}>
            <Text size={12} color="#aaaaaa" style={{ fontWeight: '600' }}>{qualityChip}</Text>
          </View>
        </View>

        {/* 中间：循环 上一首 播放 下一首 收藏 */}
        <View style={styles.controlsCenter}>
          <TvPlayModeTriple iconColor="#888888" />
          <PrevBtn />
          <TogglePlayBtn />
          <NextBtn />
          <TvPressable
            tvFocusVariant="mutedFill"
            style={ctrl.hit}
            onPress={toggleLove}
            accessibilityLabel="收藏"
          >
            <Icon name="love" color={inLove ? TV_ACCENT_GREEN : '#888888'} rawSize={22} />
          </TvPressable>
        </View>

        {/* 右侧：MV + 队列 */}
        <View style={styles.controlsRight}>
          <TvPressable
            tvFocusVariant="mutedFill"
            style={chip.mvChip}
            onPress={onOpenMv}
            accessibilityLabel="MV"
          >
            <Text size={12} color="#aaaaaa" style={{ fontWeight: '600' }}>{mvLoading ? '加载中' : 'MV'}</Text>
          </TvPressable>
          <TvPressable
            tvFocusVariant="mutedFill"
            style={ctrl.hit}
            onPress={onOpenQueue}
            accessibilityLabel="播放队列"
          >
            <Icon name="menu" color="#888888" rawSize={22} />
          </TvPressable>
        </View>
      </View>
    </View>
  )
})

const PrevBtn = () => (
  <TvPressable tvFocusVariant="mutedFill" style={ctrl.hit} onPress={() => {
    void playPrev()
  }}
  >
    <Icon name="prevMusic" color="#888888" rawSize={24} />
  </TvPressable>
)

const NextBtn = () => (
  <TvPressable tvFocusVariant="mutedFill" style={ctrl.hit} onPress={() => {
    void playNext()
  }}
  >
    <Icon name="nextMusic" color="#888888" rawSize={24} />
  </TvPressable>
)

/** 参考图：半透明灰圆底 + 白色图标 */
const TogglePlayBtn = () => {
  const isPlay = useIsPlay()
  return (
    <TvPressable
      tvPreferredFocus
      tvFocusVariant="mutedFill"
      style={[ctrl.hit, ctrl.playCircle]}
      onPress={togglePlay}
    >
      <Icon name={isPlay ? 'pause' : 'play'} color="#ffffff" rawSize={26} />
    </TvPressable>
  )
}

const waveStyles = StyleSheet.create({
  block: {
    height: 40,
    width: '100%',
    overflow: 'hidden',
  },
  wrap: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'center',
    height: 40,
    width: '100%',
  },
  bar: {
    width: 1.5,
    marginHorizontal: 0.5,
    borderRadius: 1,
  },
})

const ctrl = StyleSheet.create({
  hit: {
    minWidth: 44,
    minHeight: 44,
    justifyContent: 'center',
    alignItems: 'center',
  },
  playCircle: {
    width: 54,
    height: 54,
    borderRadius: 27,
    backgroundColor: 'rgba(255, 255, 255, 0.15)',
    marginHorizontal: 8,
  },
})

const chip = StyleSheet.create({
  hitHtml: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 4,
    borderWidth: 1,
    borderColor: '#444444',
    backgroundColor: 'transparent',
  },
  mvChip: {
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 4,
    borderWidth: 1,
    borderColor: '#444444',
    backgroundColor: 'transparent',
    justifyContent: 'center',
    alignItems: 'center',
  },
})

const styles = StyleSheet.create({
  root: {
    paddingHorizontal: 20,
    paddingBottom: 12,
    backgroundColor: 'transparent',
    flexShrink: 0,
  },
  waveMount: {
    paddingHorizontal: 4,
    marginBottom: 4,
  },
  progressTouch: {
    height: 22,
    justifyContent: 'center',
    paddingHorizontal: 4,
  },
  progressTrack: {
    height: 2,
    overflow: 'hidden',
    borderRadius: 1,
  },
  timeRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingHorizontal: 6,
    marginBottom: 10,
  },
  controlsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 4,
  },
  controlsLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    minWidth: 120,
  },
  controlsCenter: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 18,
  },
  controlsRight: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'flex-end',
    gap: 12,
    minWidth: 120,
  },
})
