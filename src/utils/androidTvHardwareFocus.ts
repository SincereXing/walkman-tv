import { useEffect } from 'react'
import type { RefObject } from 'react'
import { DeviceEventEmitter, findNodeHandle, Platform, type View } from 'react-native'
import { IS_TV_APP } from '@/config/tv'

interface HwFocusPayload {
  eventType?: string
  tag?: number
}

/**
 * Android（Paper）：RCTView 的焦点变化不会触发 JS 的 `onFocus`/`onBlur`，而是由 ReactRootView
 * 通过全局事件 `onHWKeyEvent` 派发（eventType 为 focus/blur，tag 为原生 reactTag）。
 * TV 遥控描边必须订阅该事件并与 findNodeHandle(ref) 比对。
 */
export function useAndroidTvHardwareFocusRing(
  ref: RefObject<View | null>,
  setFocused: (v: boolean) => void,
): void {
  useEffect(() => {
    if (Platform.OS !== 'android' || !IS_TV_APP) return

    const sub = DeviceEventEmitter.addListener('onHWKeyEvent', (payload: HwFocusPayload) => {
      const node = ref.current
      if (node == null || payload.tag == null) return
      const myTag = findNodeHandle(node)
      if (myTag == null || payload.tag !== myTag) return
      if (payload.eventType === 'focus') setFocused(true)
      else if (payload.eventType === 'blur') setFocused(false)
    })
    return () => {
      sub.remove()
    }
  }, [ref, setFocused])
}
