import { memo, useEffect, useMemo, useRef, useState, useCallback } from 'react'
import { View, Animated, Easing, type LayoutChangeEvent } from 'react-native'
import { usePlayerMusicInfo } from '@/store/player/hook'
import { useIsPlay } from '@/store/player/hook'
import { NAV_SHEAR_NATIVE_IDS } from '@/config/constant'
import Image from '@/components/common/Image'
import { TV_HTML_VINYL_BORDER } from '@/config/tvStyles'

/**
 * 对齐参考 HTML：外圈约 280、8px #155015 绿边、内封面 160/280、中心圆孔；播放时整碟旋转（HTML animation rotate）
 * 尺寸必须按「右侧栏实际宽度」计算，禁止用整屏宽度比例，否则会向左溢出。
 */
export default memo(({ componentId: _componentId }: { componentId: string }) => {
  const musicInfo = usePlayerMusicInfo()
  const isPlay = useIsPlay()
  const [box, setBox] = useState({ w: 0, h: 0 })

  const onBoxLayout = useCallback((e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout
    setBox({ w: width, h: height })
  }, [])

  const outer = useMemo(() => {
    const { w, h } = box
    if (w < 8 || h < 8) return 180
    // 确保唱片完全在容器内，左右各留 8px
    const maxW = w - 32
    const maxH = h - 32
    const cap = Math.min(maxW, maxH, 300)
    return Math.max(160, Math.floor(cap))
  }, [box])

  const coverSize = outer * (160 / 280)
  const hole = outer * (20 / 280)

  const rotate = useRef(new Animated.Value(0)).current
  useEffect(() => {
    if (!isPlay) return
    rotate.setValue(0)
    const loop = Animated.loop(
      Animated.timing(rotate, {
        toValue: 1,
        duration: 10000,
        easing: Easing.linear,
        useNativeDriver: true,
      }),
    )
    loop.start()
    return () => {
      loop.stop()
    }
  }, [isPlay, rotate])

  const spin = rotate.interpolate({
    inputRange: [0, 1],
    outputRange: ['0deg', '360deg'],
  })

  return (
    <View
      style={{
        flex: 1,
        width: '100%',
        minHeight: 0,
        justifyContent: 'center',
        alignItems: 'center',
        paddingVertical: 12,
      }}
      onLayout={onBoxLayout}
    >
      <Animated.View
        style={{
          width: outer,
          height: outer,
          borderRadius: outer / 2,
          backgroundColor: '#111111',
          borderWidth: 8,
          borderColor: TV_HTML_VINYL_BORDER,
          justifyContent: 'center',
          alignItems: 'center',
        shadowColor: TV_HTML_VINYL_BORDER,
        shadowOpacity: 0.8,
        shadowRadius: 48,
        shadowOffset: { width: 0, height: 0 },
        elevation: 24,
          transform: [{ rotate: spin }],
        }}
      >
        <View style={{
          width: coverSize,
          height: coverSize,
          borderRadius: coverSize / 2,
          overflow: 'hidden',
        }}
        >
          <Image
            url={musicInfo.pic}
            nativeID={NAV_SHEAR_NATIVE_IDS.playDetail_pic}
            style={{ width: coverSize, height: coverSize }}
          />
        </View>
        <View style={{
          position: 'absolute',
          width: hole,
          height: hole,
          borderRadius: hole / 2,
          backgroundColor: '#111111',
          borderWidth: 2,
          borderColor: '#333333',
        }}
        />
      </Animated.View>
    </View>
  )
})
