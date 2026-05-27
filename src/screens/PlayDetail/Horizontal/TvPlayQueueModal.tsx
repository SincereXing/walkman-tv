import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  Animated,
  BackHandler,
  Dimensions,
  FlatList,
  Pressable,
  StyleSheet,
  View,
} from 'react-native'
import Text from '@/components/common/Text'
import TvPressable from '@/components/tv/TvPressable'
import { TV_PANEL_BG } from '@/config/tvStyles'
import { usePlayInfo } from '@/store/player/hook'
import { playList } from '@/core/player/player'
import { getList } from '@/core/player/playInfo'
import playerState from '@/store/player/state'

const DRAWER_WIDTH_RATIO = 0.4 // 2/5 屏幕宽度
const ANIM_DURATION = 260

export default memo(({ visible, onClose }: {
  visible: boolean
  onClose: () => void
}) => {
  const playInfo = usePlayInfo()
  const [tempCount, setTempCount] = useState(playerState.tempPlayList.length)
  const [rendered, setRendered] = useState(visible)
  const slideAnim = useRef(new Animated.Value(1)).current // 1 = 完全在屏幕右侧外
  const listRef = useRef<FlatList>(null)

  useEffect(() => {
    const h = () => {
      setTempCount(playerState.tempPlayList.length)
    }
    global.state_event.on('playTempPlayListChanged', h)
    return () => {
      global.state_event.off('playTempPlayListChanged', h)
    }
  }, [])

  // 控制动画进出
  useEffect(() => {
    if (visible) {
      setRendered(true)
      Animated.timing(slideAnim, {
        toValue: 0,
        duration: ANIM_DURATION,
        useNativeDriver: true,
      }).start()
    } else {
      Animated.timing(slideAnim, {
        toValue: 1,
        duration: ANIM_DURATION,
        useNativeDriver: true,
      }).start(({ finished }) => {
        if (finished) setRendered(false)
      })
    }
  }, [visible, slideAnim])

  // 拦截返回键：播放列表可见时只关闭列表，不退出全屏播放器
  useEffect(() => {
    if (!visible) return
    const handler = BackHandler.addEventListener('hardwareBackPress', () => {
      onClose()
      return true
    })
    return () => { handler.remove() }
  }, [visible, onClose])

  const rows = useMemo(() => {
    const listId = playInfo.playerListId
    if (!listId) return []
    const musicList = getList(listId)
    return musicList.map((m, index) => ({
      id: `${('id' in m ? m.id : '')}-${index}`,
      index,
      name: 'name' in m ? (m.name ?? '') : '',
      singer: 'singer' in m ? (m.singer ?? '') : '',
      current: index === playInfo.playerPlayIndex,
    }))
  }, [playInfo.playerListId, playInfo.playerPlayIndex])

  const currentIndex = playInfo.playerPlayIndex ?? 0

  // 列表渲染完成后滚动到当前歌曲
  const onListLayout = useCallback(() => {
    if (rows.length > 0 && currentIndex >= 0 && currentIndex < rows.length) {
      setTimeout(() => {
        listRef.current?.scrollToIndex({ index: currentIndex, animated: false, viewPosition: 0.35 })
      }, 100)
    }
  }, [rows.length, currentIndex])

  const handlePick = async(index: number) => {
    const listId = playInfo.playerListId
    if (!listId) return
    await playList(listId, index)
    onClose()
  }

  if (!rendered) return null

  const screenW = Dimensions.get('window').width
  const drawerW = screenW * DRAWER_WIDTH_RATIO

  const translateX = slideAnim.interpolate({
    inputRange: [0, 1],
    outputRange: [0, drawerW],
  })

  const backdropOpacity = slideAnim.interpolate({
    inputRange: [0, 1],
    outputRange: [1, 0],
  })

  return (
    <View style={[StyleSheet.absoluteFill, { zIndex: 99, elevation: 99 }]} pointerEvents={visible ? 'auto' : 'none'}>
      <Animated.View style={[styles.backdrop, { opacity: backdropOpacity }]}>
        <Pressable style={StyleSheet.absoluteFill} onPress={onClose} />
      </Animated.View>
      <Animated.View style={[styles.drawer, { width: drawerW, transform: [{ translateX }] }]}>
        <Text style={styles.title} size={17} color="#fff">播放队列</Text>
        {tempCount > 0 ? (
          <Text style={styles.hint} size={12} color="rgba(255,255,255,0.45)">
            稍后播放中还有 {tempCount} 首将优先播放
          </Text>
        ) : null}
        {
          rows.length === 0
            ? (
                <Text style={styles.empty} size={13} color="rgba(255,255,255,0.5)">
                  暂无列表队列
                </Text>
              )
            : (
                <FlatList
                  ref={listRef}
                  style={styles.list}
                  data={rows}
                  keyExtractor={it => it.id}
                  onLayout={onListLayout}
                  onScrollToIndexFailed={(info) => {
                    setTimeout(() => {
                      listRef.current?.scrollToIndex({ index: info.index, animated: false, viewPosition: 0.35 })
                    }, 200)
                  }}
                  getItemLayout={(_, index) => ({
                    length: ROW_HEIGHT,
                    offset: ROW_HEIGHT * index,
                    index,
                  })}
                  renderItem={({ item }) => (
                    <TvPressable
                      tvFocusVariant="mutedFill"
                      tvPreferredFocus={item.current}
                      style={[styles.row, item.current ? styles.rowActive : null]}
                      onPress={() => { void handlePick(item.index) }}
                    >
                      <Text numberOfLines={1} size={14} color={item.current ? '#4ade80' : '#fff'} style={{ flex: 1 }}>
                        {item.name}
                      </Text>
                      <Text numberOfLines={1} size={12} color="rgba(255,255,255,0.45)" style={{ maxWidth: '38%' }}>
                        {item.singer}
                      </Text>
                    </TvPressable>
                  )}
                />
              )
        }
        <TvPressable tvFocusVariant="mutedFill" style={styles.closeBtn} onPress={onClose}>
          <Text size={14} color="#fff">关闭</Text>
        </TvPressable>
      </Animated.View>
    </View>
  )
})

const ROW_HEIGHT = 52

const styles = StyleSheet.create({
  backdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.5)',
  },
  drawer: {
    position: 'absolute',
    right: 0,
    top: 0,
    bottom: 0,
    backgroundColor: TV_PANEL_BG,
    paddingVertical: 20,
    paddingHorizontal: 16,
    borderLeftWidth: 1,
    borderLeftColor: 'rgba(255,255,255,0.08)',
    flexDirection: 'column',
  },
  title: {
    marginBottom: 8,
    fontWeight: '600',
  },
  hint: {
    marginBottom: 10,
  },
  empty: {
    paddingVertical: 28,
    textAlign: 'center',
  },
  list: {
    flex: 1,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    height: ROW_HEIGHT,
    paddingHorizontal: 10,
    marginVertical: 1,
    borderRadius: 8,
  },
  rowActive: {
    backgroundColor: 'rgba(57,255,20,0.08)',
  },
  closeBtn: {
    marginTop: 12,
    alignSelf: 'center',
    paddingHorizontal: 24,
    paddingVertical: 10,
    borderRadius: 8,
  },
})
