/**
 * 参考 TV 全屏播放截图：极细轨道 + 霓虹绿已播段（不跟随全局主题主色）
 * 支持触摸拖动 + 遥控器左右键步进
 */
import { memo, useCallback, useEffect, useRef, useState } from 'react'
import { View, PanResponder, DeviceEventEmitter, Platform, findNodeHandle } from 'react-native'
import { useDrag } from '@/utils/hooks'
import { createStyle } from '@/utils/tools'
import { TV_HTML_WAVE_BRIGHT } from '@/config/tvStyles'
import { IS_TV_APP } from '@/config/tv'
import TvPressable from '@/components/tv/TvPressable'

const TRACK = 'rgba(255, 255, 255, 0.08)'
const BUFFERED = 'rgba(57, 255, 20, 0.32)'
const GHOST = 'rgba(57, 255, 20, 0.45)'

const styles = createStyle({
  progress: {
    flex: 1,
    zIndex: 1,
  },
  progressBar: {
    height: '100%',
    borderRadius: 1,
  },
  pressBar: {
    position: 'absolute',
    left: 0,
    top: 0,
    height: '100%',
    width: '100%',
  },
  progressTvWrap: {
    flex: 1,
    justifyContent: 'center',
  },
})

const TrackBar = memo(() => (
  <View style={{
    ...styles.progressBar,
    backgroundColor: TRACK,
    position: 'absolute',
    width: '100%',
    left: 0,
    top: 0,
  }}
  />
))

const BufferedBar = memo(({ progress }: { progress: number }) => (
  <View style={{
    ...styles.progressBar,
    backgroundColor: BUFFERED,
    position: 'absolute',
    width: `${progress * 100}%`,
    left: 0,
    top: 0,
  }}
  />
))

const PreassBar = memo(({ onDragState, setDragProgress, onSetProgress }: {
  onDragState: (drag: boolean) => void
  setDragProgress: (progress: number) => void
  onSetProgress: (progress: number) => void
}) => {
  const {
    onLayout,
    onDragStart,
    onDragEnd,
    onDrag,
  } = useDrag(onSetProgress, onDragState, setDragProgress)

  const panResponder = useRef(
    PanResponder.create({
      onStartShouldSetPanResponderCapture: () => true,
      onMoveShouldSetPanResponderCapture: () => true,
      onPanResponderMove: (_evt, gestureState) => {
        onDrag(gestureState.dx)
      },
      onPanResponderGrant: (evt, gestureState) => {
        onDragStart(gestureState.dx, evt.nativeEvent.locationX)
      },
      onPanResponderRelease: () => {
        onDragEnd()
      },
    }),
  ).current

  return <View onLayout={onLayout} style={styles.pressBar} {...panResponder.panHandlers} />
})

export const TvProgressPlain = memo(({ progress, buffered }: {
  progress: number
  duration: number
  buffered: number
}) => {
  const progressStr: `${number}%` = `${progress * 100}%`
  return (
    <View style={styles.progress}>
      <View style={{ flex: 1 }}>
        <TrackBar />
        <BufferedBar progress={buffered} />
        <View style={{
          ...styles.progressBar,
          backgroundColor: TV_HTML_WAVE_BRIGHT,
          width: progressStr,
          position: 'absolute',
          left: 0,
          top: 0,
        }}
        />
      </View>
    </View>
  )
})

/** 每次遥控器左右键步进秒数 */
const SEEK_STEP = 5

const TvPlayerProgress = memo(({ progress, duration, buffered }: {
  progress: number
  duration: number
  buffered: number
}) => {
  const [draging, setDraging] = useState(false)
  const [dragProgress, setDragProgress] = useState(0)
  const progressStr: `${number}%` = `${progress * 100}%`

  const durationRef = useRef(duration)
  const progressRef = useRef(progress)
  useEffect(() => {
    durationRef.current = duration
  }, [duration])
  useEffect(() => {
    progressRef.current = progress
  }, [progress])
  const onSetProgress = useCallback((p: number) => {
    global.app_event.setProgress(p * durationRef.current)
  }, [])

  /** 遥控器左右键步进 */
  const containerRef = useRef<View | null>(null)
  const [selfTag, setSelfTag] = useState<number | undefined>(undefined)

  const setContainerRef = useCallback((node: View | null) => {
    containerRef.current = node
    if (node) {
      const tag = findNodeHandle(node)
      if (tag) setSelfTag(tag)
    }
  }, [])

  // 单一 onHWKeyEvent 监听：焦点追踪 + 左右键步进
  const focusedRef = useRef(false)
  useEffect(() => {
    if (!IS_TV_APP || Platform.OS !== 'android' || selfTag == null) return

    const sub = DeviceEventEmitter.addListener('onHWKeyEvent', (payload: { eventType?: string, eventKeyAction?: number, tag?: number }) => {
      // 焦点追踪：eventType 为 focus/blur 时更新状态
      if (payload.eventType === 'focus' && payload.tag === selfTag) {
        focusedRef.current = true
        return
      }
      if (payload.eventType === 'blur' && payload.tag === selfTag) {
        focusedRef.current = false
        return
      }

      // 左右键步进：只在聚焦 + ACTION_DOWN 时处理
      if (!focusedRef.current) return
      if (payload.eventKeyAction !== 0) return
      const dur = durationRef.current
      if (dur <= 0) return
      const curTime = progressRef.current * dur
      if (payload.eventType === 'left') {
        const t = Math.max(0, curTime - SEEK_STEP)
        global.app_event.setProgress(t)
      } else if (payload.eventType === 'right') {
        const t = Math.min(dur, curTime + SEEK_STEP)
        global.app_event.setProgress(t)
      }
    })
    return () => { sub.remove() }
  }, [selfTag])

  return (
    <TvPressable
      tvFocusVariant="mutedFill"
      style={styles.progressTvWrap}
      ref={setContainerRef}
      {...(selfTag != null ? { nextFocusLeft: selfTag, nextFocusRight: selfTag } : {})}
    >
      <View style={styles.progress}>
        <View style={{ flex: 1 }}>
          <TrackBar />
          <BufferedBar progress={buffered} />
          {
            draging
              ? (
                  <>
                    <View style={{
                      ...styles.progressBar,
                      backgroundColor: GHOST,
                      width: progressStr,
                      position: 'absolute',
                      left: 0,
                      top: 0,
                    }}
                    />
                    <View style={{
                      ...styles.progressBar,
                      backgroundColor: GHOST,
                      width: `${dragProgress * 100}%`,
                      position: 'absolute',
                      left: 0,
                      top: 0,
                    }}
                    />
                  </>
                )
              : (
                  <View style={{
                    ...styles.progressBar,
                    backgroundColor: TV_HTML_WAVE_BRIGHT,
                    width: progressStr,
                    position: 'absolute',
                    left: 0,
                    top: 0,
                  }}
                  />
                )
          }
        </View>
        <PreassBar onDragState={setDraging} setDragProgress={setDragProgress} onSetProgress={onSetProgress} />
      </View>
    </TvPressable>
  )
})

export default TvPlayerProgress
