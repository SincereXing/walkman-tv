import { memo, useCallback, useState } from 'react'
import { View, StyleSheet } from 'react-native'
import { usePlayerMusicInfo } from '@/store/player/hook'
import Text from '@/components/common/Text'
import Lyric from './Lyric'
import VinylPic from './VinylPic'
import TvPlayBottomBar from './TvPlayBottomBar'
import { useStatusbarHeight } from '@/store/common/hook'
import StatusBar from '@/components/common/StatusBar'
import TvPlayQueueModal from './TvPlayQueueModal'
import TvMvPanel from './TvMvPanel'
import { getMusicMvUrl } from '@/core/music/mv'
import { toast } from '@/utils/tools'
import playerState from '@/store/player/state'

// #region debug-point D:mv-ui-report
const reportMvUiDebug = (msg: string, data?: Record<string, unknown>) => {
  fetch('http://127.0.0.1:7777/event', {
    method: 'POST',
    body: JSON.stringify({
      sessionId: 'mv-fetch-fail',
      runId: 'pre-fix',
      hypothesisId: 'D',
      location: 'src/screens/PlayDetail/Horizontal/TvPlayDetailLayout.tsx',
      msg: `[DEBUG] ${msg}`,
      data,
      ts: Date.now(),
    }),
  }).catch(() => {})
}
// #endregion

/**
 * 全屏播放（TV）
 * 布局参考效果图：
 * 径向绿心背景、标题区（靠左与歌词同列）、左歌词右唱片、底部波形 + 进度条 + 三段式控件
 */
export default memo(({ componentId }: { componentId: string }) => {
  const statusBarHeight = useStatusbarHeight()
  const musicInfo = usePlayerMusicInfo()
  const currentMusic = playerState.playMusicInfo.musicInfo
  const [queueOpen, setQueueOpen] = useState(false)
  const [mvOpen, setMvOpen] = useState(false)
  const [mvLoading, setMvLoading] = useState(false)
  const [mvInfo, setMvInfo] = useState<LX.Music.MusicVideoInfo | null>(null)
  const handleOpenQueue = useCallback(() => { setQueueOpen(true) }, [])
  const handleCloseQueue = useCallback(() => { setQueueOpen(false) }, [])
  const handleCloseMv = useCallback(() => {
    setMvOpen(false)
    setMvInfo(null)
  }, [])

  const handleOpenMv = useCallback(() => {
    if (!currentMusic || 'progress' in currentMusic) {
      toast('暂无播放歌曲', 'short')
      return
    }
    // #region debug-point D:mv-ui-click
    reportMvUiDebug('MV button clicked', {
      id: currentMusic.id,
      source: currentMusic.source,
      name: currentMusic.name,
      singer: currentMusic.singer,
      hash: 'hash' in currentMusic ? (currentMusic as any).hash ?? '' : '',
      mvHash: 'mvHash' in currentMusic ? (currentMusic as any).mvHash ?? '' : '',
      mvId: 'mvId' in currentMusic ? (currentMusic as any).mvId ?? '' : '',
      mvVid: 'mvVid' in currentMusic ? (currentMusic as any).mvVid ?? '' : '',
    })
    // #endregion
    setMvLoading(true)
    setQueueOpen(false)
    void getMusicMvUrl(currentMusic).then((info) => {
      // #region debug-point D:mv-ui-result
      reportMvUiDebug('MV button resolved', {
        hasInfo: Boolean(info),
        hasUrl: Boolean(info?.url),
        hasPageUrl: Boolean(info?.pageUrl),
        qualityCount: info?.qualitys?.length ?? 0,
      })
      // #endregion
      if (!info) {
        toast('当前歌曲暂无 MV', 'short')
        return
      }
      setMvInfo(info)
      setMvOpen(true)
    }).catch((error: any) => {
      // #region debug-point D:mv-ui-error
      reportMvUiDebug('MV button rejected', {
        message: error?.message ?? String(error),
        stack: error?.stack ? String(error.stack).slice(0, 400) : '',
      })
      // #endregion
      toast(`获取 MV 失败: ${String(error?.message ?? error ?? 'unknown').slice(0, 60)}`, 'short')
    }).finally(() => {
      setMvLoading(false)
    })
  }, [currentMusic])

  return (
    <View style={styles.root}>
      <View pointerEvents="none" style={styles.bgBase} />
      <View pointerEvents="none" style={styles.radialGlow} />
      <StatusBar />
      <View style={[styles.content, { paddingTop: statusBarHeight }]}>
        <View style={styles.split}>
          {/* 左侧：标题 + 歌手 + 歌词 */}
          <View style={styles.leftPane}>
            <View style={styles.header}>
              <Text
                numberOfLines={1}
                size={26}
                color="#ffffff"
                style={styles.titleText}
              >
                {musicInfo.name || '—'}
              </Text>
              <Text numberOfLines={1} size={14} color="#a8a8a8" style={styles.artist}>
                {musicInfo.singer || ' '}
              </Text>
            </View>
            <View style={styles.lyricWrap}>
              <Lyric />
            </View>
          </View>
          {/* 右侧：黑胶唱片 */}
          <View style={styles.rightPane}>
            <VinylPic componentId={componentId} />
          </View>
        </View>
        <TvPlayBottomBar onOpenQueue={handleOpenQueue} onOpenMv={handleOpenMv} mvLoading={mvLoading} />
      </View>
      <TvMvPanel
        visible={mvOpen}
        mvInfo={mvInfo}
        onClose={handleCloseMv}
      />
      <TvPlayQueueModal visible={queueOpen} onClose={handleCloseQueue} />
    </View>
  )
})

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: '#000000',
  },
  bgBase: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: '#000000',
  },
  /** 与效果图接近：中间椭圆形区域偏绿，边缘仍是 bgBase 的黑色 */
  radialGlow: {
    position: 'absolute',
    left: '-12%',
    right: '-12%',
    top: '6%',
    bottom: '18%',
    borderRadius: 9999,
    backgroundColor: 'rgba(26, 51, 26, 0.5)',
  },
  content: {
    flex: 1,
    minHeight: 0,
    zIndex: 1,
    flexDirection: 'column',
  },
  header: {
    marginBottom: 12,
    paddingLeft: 24,
    paddingRight: 22,
    zIndex: 2,
  },
  titleText: {
    fontWeight: '600',
  },
  artist: {
    marginTop: 6,
    paddingLeft: 2,
  },
  split: {
    flex: 1,
    flexDirection: 'row',
    minHeight: 0,
    width: '100%',
    paddingHorizontal: 20,
    alignItems: 'stretch',
  },
  leftPane: {
    width: '50%',
    flexGrow: 0,
    flexShrink: 0,
    paddingRight: 12,
    paddingTop: 20,
  },
  rightPane: {
    width: '50%',
    flexGrow: 0,
    flexShrink: 0,
    justifyContent: 'center',
    alignItems: 'center',
    paddingTop: 20,
    paddingLeft: 16,
    overflow: 'hidden',
  },
  lyricWrap: {
    flex: 1,
    minHeight: 0,
  },
})
