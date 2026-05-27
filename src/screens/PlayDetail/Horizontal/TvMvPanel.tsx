import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { View, StyleSheet, BackHandler, Pressable, DeviceEventEmitter, Platform, findNodeHandle, FlatList } from 'react-native'
import Video from 'react-native-video'
import Image from '@/components/common/Image'
import Text from '@/components/common/Text'
import { useWindowSize } from '@/utils/hooks'
import TvPressable from '@/components/tv/TvPressable'
import { openUrl, toast } from '@/utils/tools'
import { play, pause } from '@/core/player/player'
import playerState from '@/store/player/state'
import { IS_TV_APP } from '@/config/tv'
import { usePlayInfo } from '@/store/player/hook'
import { getList, setPlayListId, setPlayMusicInfo } from '@/core/player/playInfo'
import { getMusicMvUrl } from '@/core/music/mv'

type TvMvPanelProps = {
  visible: boolean
  mvInfo: LX.Music.MusicVideoInfo | null
  onClose: () => void
}

type HwPayload = {
  eventType?: string
  eventKeyAction?: number
  eventKeyCode?: number
  tag?: number
}

const AUTO_HIDE_MS = 1200
const SEEK_STEP = 10
const QUEUE_ROW_HEIGHT = 52

const formatTime = (seconds: number) => {
  if (!Number.isFinite(seconds) || seconds <= 0) return '00:00'
  const total = Math.floor(seconds)
  const mm = Math.floor(total / 60)
  const ss = total % 60
  return `${String(mm).padStart(2, '0')}:${String(ss).padStart(2, '0')}`
}

type QueueItem = LX.Music.MusicInfo | LX.Download.ListItem

const getItemName = (item: QueueItem | null | undefined) => item && 'name' in item ? item.name ?? '' : ''
const getItemSinger = (item: QueueItem | null | undefined) => item && 'singer' in item ? item.singer ?? '' : ''
const isMvCapableMusic = (item: QueueItem | null | undefined): item is LX.Music.MusicInfo => {
  return Boolean(item && 'source' in item)
}

/** MV 播放器：复用当前音频队列，并支持在 MV 内自动续播 */
function TvMvPanel({ visible, mvInfo, onClose }: TvMvPanelProps) {
  const { width: winWidth } = useWindowSize()
  const playInfo = usePlayInfo()
  const [activeMvInfo, setActiveMvInfo] = useState<LX.Music.MusicVideoInfo | null>(mvInfo)
  const [activeQueueIndex, setActiveQueueIndex] = useState(-1)
  const [videoPaused, setVideoPaused] = useState(false)
  const [videoLoading, setVideoLoading] = useState(false)
  const [videoEnded, setVideoEnded] = useState(false)
  const [videoError, setVideoError] = useState('')
  const [controlsVisible, setControlsVisible] = useState(true)
  const [currentTime, setCurrentTime] = useState(0)
  const [duration, setDuration] = useState(0)
  const [surfaceTag, setSurfaceTag] = useState<number | undefined>(undefined)
  const [queueOpen, setQueueOpen] = useState(false)
  const [switchingMv, setSwitchingMv] = useState(false)
  const pausedAudioByMvRef = useRef(false)
  const hideTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const videoRef = useRef<any>(null)
  const surfaceRef = useRef<View | null>(null)
  const surfaceFocusedRef = useRef(false)
  const prevVisibleRef = useRef(false)

  const queueList = useMemo(() => {
    if (!playInfo.playerListId) return []
    return getList(playInfo.playerListId) as QueueItem[]
  }, [playInfo.playerListId, playInfo.playerPlayIndex, visible])

  const queueRows = useMemo(() => {
    return queueList.map((item, index) => ({
      id: `${item.id}-${index}`,
      index,
      name: getItemName(item),
      singer: getItemSinger(item),
      current: index === activeQueueIndex,
    }))
  }, [activeQueueIndex, queueList])

  const canOpen = Boolean(activeMvInfo?.url)

  const clearHideTimer = useCallback(() => {
    if (!hideTimerRef.current) return
    clearTimeout(hideTimerRef.current)
    hideTimerRef.current = null
  }, [])

  const showControls = useCallback((keep = false) => {
    setControlsVisible(true)
    clearHideTimer()
    if (keep || !canOpen || videoPaused || videoLoading || videoError || videoEnded || queueOpen || switchingMv) return
    hideTimerRef.current = setTimeout(() => {
      setControlsVisible(false)
    }, AUTO_HIDE_MS)
  }, [canOpen, clearHideTimer, queueOpen, switchingMv, videoEnded, videoError, videoLoading, videoPaused])

  const showSeekOverlay = useCallback(() => {
    setControlsVisible(true)
    clearHideTimer()
    if (queueOpen || switchingMv || videoLoading || videoError) return
    hideTimerRef.current = setTimeout(() => {
      setControlsVisible(false)
    }, AUTO_HIDE_MS)
  }, [clearHideTimer, queueOpen, switchingMv, videoError, videoLoading])

  const resumeVideo = useCallback(() => {
    setVideoPaused(false)
    setVideoEnded(false)
    setTimeout(() => {
      videoRef.current?.resume?.()
    }, 40)
  }, [])

  const seekTo = useCallback((targetTime: number) => {
    const safeTime = Math.max(0, Math.min(duration || 0, targetTime))
    videoRef.current?.seek?.(safeTime)
    setCurrentTime(safeTime)
    setVideoEnded(false)
    resumeVideo()
    showControls()
  }, [duration, resumeVideo, showControls])

  const seekBy = useCallback((delta: number) => {
    if (!canOpen || videoError) return
    seekTo(currentTime + delta)
  }, [canOpen, currentTime, seekTo, videoError])

  const resolveMvForMusic = useCallback(async(item: LX.Music.MusicInfo | null) => {
    if (!item || item.source == 'local') return null
    return getMusicMvUrl(item)
  }, [])

  const syncAudioToQueueIndex = useCallback(async(index: number) => {
    if (!playInfo.playerListId) return
    const targetItem = queueList[index]
    if (!targetItem || 'progress' in targetItem) return
    setPlayListId(playInfo.playerListId)
    setPlayMusicInfo(playInfo.playerListId, targetItem)
    await pause()
  }, [playInfo.playerListId, queueList])

  const activateQueueIndex = useCallback(async(index: number, silent = false) => {
    const item = queueList[index]
    if (!isMvCapableMusic(item) || item.source == 'local') {
      if (!silent) toast('这首歌不支持 MV', 'short')
      return false
    }
    setSwitchingMv(true)
    showControls(true)
    try {
      const info = await resolveMvForMusic(item)
      if (!info?.url) {
        if (!silent) toast(info ? '这首歌的 MV 暂无可播放地址' : '这首歌暂无 MV', 'short')
        return false
      }
      await syncAudioToQueueIndex(index)
      setActiveMvInfo(info)
      setActiveQueueIndex(index)
      setCurrentTime(0)
      setDuration(0)
      setVideoError('')
      setVideoLoading(true)
      resumeVideo()
      return true
    } catch (error: any) {
      setVideoError(error?.message ?? '切换 MV 失败')
      if (!silent) toast(`切换 MV 失败: ${String(error?.message ?? error ?? 'unknown').slice(0, 40)}`, 'short')
      return false
    } finally {
      setSwitchingMv(false)
    }
  }, [queueList, resolveMvForMusic, resumeVideo, showControls, syncAudioToQueueIndex])

  const playNextMv = useCallback(async() => {
    if (activeQueueIndex < 0 || !queueList.length) {
      setVideoEnded(true)
      setVideoPaused(true)
      showControls(true)
      return
    }
    for (let index = activeQueueIndex + 1; index < queueList.length; index++) {
      if (await activateQueueIndex(index, true)) return
    }
    setVideoEnded(true)
    setVideoPaused(true)
    showControls(true)
    toast('MV 播放列表已结束', 'short')
  }, [activeQueueIndex, activateQueueIndex, queueList.length, showControls])

  useEffect(() => {
    if (!visible) return
    const handler = BackHandler.addEventListener('hardwareBackPress', () => {
      if (queueOpen) {
        setQueueOpen(false)
        showControls(false)
        return true
      }
      onClose()
      return true
    })
    return () => { handler.remove() }
  }, [visible, onClose, queueOpen, showControls])

  useEffect(() => {
    if (visible && !prevVisibleRef.current) {
      setActiveMvInfo(mvInfo)
      setActiveQueueIndex(playInfo.playerPlayIndex)
      setQueueOpen(false)
      setSwitchingMv(false)
      setVideoPaused(false)
      setVideoLoading(Boolean(mvInfo?.url))
      setVideoEnded(false)
      setVideoError('')
      setControlsVisible(false)
      setCurrentTime(0)
      setDuration(0)
      pausedAudioByMvRef.current = playerState.isPlay
      if (pausedAudioByMvRef.current) void pause()
    }
    prevVisibleRef.current = visible
    if (!visible) {
      clearHideTimer()
      if (pausedAudioByMvRef.current) {
        play()
        pausedAudioByMvRef.current = false
      }
      setQueueOpen(false)
      setSwitchingMv(false)
      setVideoPaused(false)
      setVideoLoading(false)
      setVideoEnded(false)
      setVideoError('')
      setControlsVisible(false)
      setCurrentTime(0)
      setDuration(0)
    }
  }, [clearHideTimer, mvInfo, playInfo.playerPlayIndex, visible])

  useEffect(() => {
    if (!visible || !canOpen || Platform.OS !== 'android' || !IS_TV_APP || surfaceTag == null) return
    const sub = DeviceEventEmitter.addListener('onHWKeyEvent', (payload: HwPayload) => {
      if (payload.eventType === 'focus' && payload.tag === surfaceTag) {
        surfaceFocusedRef.current = true
        return
      }
      if (payload.eventType === 'blur' && payload.tag === surfaceTag) {
        surfaceFocusedRef.current = false
        return
      }
      if (payload.eventKeyAction !== 0) return
      const eventType = payload.eventType ?? ''
      const isMenuEvent = eventType === 'menu' || eventType === 'menuPress' || eventType === 'options' || payload.eventKeyCode === 82
      if (isMenuEvent) {
        setQueueOpen(prev => !prev)
        setControlsVisible(false)
        clearHideTimer()
        return
      }
      const canHandleSeek = surfaceFocusedRef.current || !controlsVisible
      if (!canHandleSeek) return
      if (eventType === 'left') {
        showSeekOverlay()
        seekBy(-SEEK_STEP)
      } else if (eventType === 'right') {
        showSeekOverlay()
        seekBy(SEEK_STEP)
      } else if (eventType === 'select' || eventType === 'center') {
        toggleVideoPaused()
      }
    })
    return () => {
      sub.remove()
    }
  }, [canOpen, clearHideTimer, controlsVisible, seekBy, showSeekOverlay, surfaceTag, visible])

  const setSurfaceNode = useCallback((node: View | null) => {
    surfaceRef.current = node
    if (node) {
      const tag = findNodeHandle(node)
      if (tag) setSurfaceTag(tag)
    }
  }, [])

  if (!visible || !activeMvInfo) return null

  const toggleVideoPaused = () => {
    if (videoError) return
    if (videoEnded) {
      seekTo(0)
      return
    }
    setVideoPaused(prev => {
      const next = !prev
      if (!next) setTimeout(() => { videoRef.current?.resume?.() }, 40)
      else videoRef.current?.pause?.()
      return next
    })
  }

  const progressRatio = duration > 0 ? Math.max(0, Math.min(1, currentTime / duration)) : 0
  const fallbackExternalUrl = activeMvInfo.url || activeMvInfo.pageUrl || ''

  return (
    <View style={styles.overlay}>
      <Pressable style={StyleSheet.absoluteFill} onPress={() => {
        if (queueOpen) {
          setQueueOpen(false)
          return
        }
        onClose()
      }}
      />
      <View style={styles.wrap}>
        {canOpen ? (
          <TvPressable
            ref={setSurfaceNode}
            tvPreferredFocus
            tvFocusVariant="none"
            style={styles.videoTouch}
            {...(surfaceTag != null ? { nextFocusLeft: surfaceTag, nextFocusRight: surfaceTag } : {})}
            onPress={() => {
              toggleVideoPaused()
            }}
          >
            <View style={styles.videoStage}>
            <Video
              ref={videoRef}
              source={{ uri: activeMvInfo.url! }}
              style={styles.video}
              resizeMode="contain"
              controls={false}
              paused={videoPaused}
              playInBackground={false}
              repeat={false}
              onLoadStart={() => {
                setVideoLoading(true)
                setVideoError('')
              }}
              onLoad={(data: any) => {
                setVideoLoading(false)
                setVideoError('')
                setDuration(data?.duration ?? 0)
                resumeVideo()
              }}
              onBuffer={({ isBuffering }: any) => {
                setVideoLoading(Boolean(isBuffering))
              }}
              onProgress={(data: any) => {
                setCurrentTime(data?.currentTime ?? 0)
                if (data?.seekableDuration) setDuration(data.seekableDuration)
              }}
              onEnd={() => {
                void playNextMv()
              }}
              onError={(err: any) => {
                setVideoLoading(false)
                setVideoPaused(true)
                setVideoError(err?.error?.errorString || err?.errorString || 'MV 播放失败')
                setControlsVisible(true)
              }}
            />
            {switchingMv ? (
              <View style={styles.videoMask}>
                <Text size={14} color="#ffffff">正在切换下一首 MV...</Text>
              </View>
            ) : null}
            {videoLoading ? (
              <View style={styles.videoMask}>
                <Text size={14} color="#ffffff">MV 加载中...</Text>
              </View>
            ) : null}
            {videoError ? (
              <View style={styles.videoMask}>
                <Text size={14} color="#ffffff">{videoError}</Text>
              </View>
            ) : null}
            {videoPaused && !videoLoading && !videoError ? (
              <View style={styles.centerHint}>
                <Text size={20} color="#ffffff">已暂停</Text>
              </View>
            ) : null}
            </View>
          </TvPressable>
        ) : (
          <View style={[styles.frame, { width: Math.min(winWidth * 0.42, 420), height: Math.min(winWidth * 0.42, 420), borderRadius: Math.min(winWidth * 0.42, 420) / 2 }]}>
            <Image
              url={activeMvInfo.coverUrl || ''}
              style={{ width: Math.min(winWidth * 0.4, 400), height: Math.min(winWidth * 0.4, 400), borderRadius: Math.min(winWidth * 0.4, 400) / 2 }}
            />
          </View>
        )}
        {queueOpen ? (
          <View style={styles.queueDrawer}>
            <Text size={15} color="#fff" style={styles.queueTitle}>MV 播放列表</Text>
            {
              queueRows.length === 0
                ? <Text size={13} color="rgba(255,255,255,0.5)" style={styles.queueEmpty}>当前队列为空</Text>
                : (
                    <FlatList
                      style={styles.queueList}
                      data={queueRows}
                      keyExtractor={(item) => item.id}
                      getItemLayout={(_, index) => ({ length: QUEUE_ROW_HEIGHT, offset: QUEUE_ROW_HEIGHT * index, index })}
                      renderItem={({ item }) => (
                        <TvPressable
                          tvPreferredFocus={item.current}
                          tvFocusVariant="mutedFill"
                          style={[styles.queueRow, item.current ? styles.queueRowActive : null]}
                          onPress={() => {
                            setQueueOpen(false)
                            void activateQueueIndex(item.index)
                          }}
                        >
                          <Text numberOfLines={1} size={14} color={item.current ? '#39ff14' : '#fff'} style={styles.queueName}>
                            {item.name || '未知歌曲'}
                          </Text>
                          <Text numberOfLines={1} size={12} color="rgba(255,255,255,0.45)" style={styles.queueSinger}>
                            {item.singer}
                          </Text>
                        </TvPressable>
                      )}
                    />
                  )
            }
          </View>
        ) : null}
        {controlsVisible ? (
          <>
            {canOpen ? (
              <>
                <View style={styles.progressBlockFloating}>
                  <View style={styles.progressTrack}>
                    <View style={[styles.progressPlayed, { width: `${progressRatio * 100}%` }]} />
                  </View>
                  <View style={styles.timeRow}>
                    <Text size={12} color="rgba(255,255,255,0.72)">{formatTime(currentTime)}</Text>
                    <Text size={12} color="rgba(255,255,255,0.72)">{formatTime(duration)}</Text>
                  </View>
                </View>
                {videoError || videoEnded ? (
                  <Text size={13} color="rgba(255,255,255,0.78)" style={styles.caption}>
                    {videoError ? 'MV 播放失败，已提供降级入口。' : 'MV 已播放结束'}
                  </Text>
                ) : null}
              </>
            ) : (
              <Text size={13} color="rgba(255,255,255,0.78)" style={styles.caption}>
                当前歌曲已识别到 MV，但播放地址还未补齐。
              </Text>
            )}
            <View style={styles.btnRowFloating}>
              {videoError && fallbackExternalUrl ? (
                <TvPressable tvFocusVariant="mutedFill" style={styles.secondaryBtn} onPress={() => { void openUrl(fallbackExternalUrl) }}>
                  <Text size={14} color="rgba(255,255,255,0.92)">外部播放</Text>
                </TvPressable>
              ) : null}
              {!videoError && activeMvInfo.pageUrl ? (
                <TvPressable tvFocusVariant="mutedFill" style={styles.secondaryBtn} onPress={() => { void openUrl(activeMvInfo.pageUrl!) }}>
                  <Text size={14} color="rgba(255,255,255,0.92)">打开详情页</Text>
                </TvPressable>
              ) : null}
            </View>
          </>
        ) : null}
      </View>
    </View>
  )
}

export default memo(TvMvPanel)

const styles = StyleSheet.create({
  overlay: {
    ...StyleSheet.absoluteFillObject,
    zIndex: 95,
    elevation: 95,
    backgroundColor: '#000000',
  },
  wrap: {
    flex: 1,
    backgroundColor: '#000000',
  },
  frame: {
    overflow: 'hidden',
    justifyContent: 'center',
    alignItems: 'center',
    alignSelf: 'center',
    marginTop: 120,
  },
  videoTouch: {
    flex: 1,
  },
  videoStage: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: '#000000',
  },
  video: {
    width: '100%',
    height: '100%',
    backgroundColor: '#000000',
  },
  videoHud: {
    position: 'absolute',
    left: 12,
    top: 12,
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 8,
    backgroundColor: 'rgba(0,0,0,0.34)',
  },
  hudText: {
    fontWeight: '500',
  },
  videoMask: {
    ...StyleSheet.absoluteFillObject,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(0,0,0,0.24)',
    paddingHorizontal: 20,
  },
  progressBlock: {
    width: '100%',
    marginTop: 16,
    paddingHorizontal: 2,
  },
  progressBlockFloating: {
    position: 'absolute',
    left: 28,
    right: 28,
    bottom: 24,
  },
  progressTrack: {
    height: 4,
    width: '100%',
    borderRadius: 999,
    backgroundColor: 'rgba(255,255,255,0.14)',
    overflow: 'hidden',
  },
  progressPlayed: {
    height: '100%',
    backgroundColor: '#39ff14',
  },
  timeRow: {
    marginTop: 8,
    flexDirection: 'row',
    justifyContent: 'space-between',
    width: '100%',
  },
  caption: {
    position: 'absolute',
    left: 28,
    bottom: 52,
    textAlign: 'center',
    lineHeight: 20,
    backgroundColor: 'rgba(0,0,0,0.28)',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 8,
  },
  btnRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 22,
  },
  btnRowFloating: {
    position: 'absolute',
    right: 28,
    bottom: 20,
    flexDirection: 'row',
    alignItems: 'center',
  },
  primaryBtn: {
    minWidth: 156,
    minHeight: 48,
    paddingHorizontal: 20,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255,255,255,0.12)',
    marginRight: 12,
  },
  secondaryBtn: {
    minWidth: 132,
    minHeight: 48,
    paddingHorizontal: 20,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
  },
  ghostBtn: {
    minWidth: 120,
    minHeight: 48,
    paddingHorizontal: 20,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
  },
  queueDrawer: {
    position: 'absolute',
    right: 18,
    top: 18,
    bottom: 18,
    width: 340,
    borderRadius: 16,
    paddingVertical: 14,
    paddingHorizontal: 12,
    backgroundColor: 'rgba(9, 12, 16, 0.92)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.08)',
  },
  queueTitle: {
    fontWeight: '600',
    marginBottom: 10,
  },
  queueEmpty: {
    paddingTop: 28,
    textAlign: 'center',
  },
  queueList: {
    flex: 1,
  },
  queueRow: {
    height: QUEUE_ROW_HEIGHT,
    borderRadius: 10,
    paddingHorizontal: 10,
    marginBottom: 6,
    justifyContent: 'center',
  },
  queueRowActive: {
    backgroundColor: 'rgba(255,255,255,0.08)',
  },
  queueName: {
    marginBottom: 2,
  },
  queueSinger: {
    maxWidth: '92%',
  },
  centerHint: {
    ...StyleSheet.absoluteFillObject,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(0,0,0,0.12)',
  },
})
