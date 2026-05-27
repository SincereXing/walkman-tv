import { forwardRef, useCallback, useEffect, useRef, useState, type ForwardedRef } from 'react'
import { Pressable, type PressableProps, type StyleProp, type View, type ViewStyle } from 'react-native'
import { IS_TV_APP } from '@/config/tv'
import { TV_FOCUS_BORDER, TV_FOCUS_BORDER_WIDTH, TV_FOCUS_FILL } from '@/config/tvStyles'
import { useAndroidTvHardwareFocusRing } from '@/utils/androidTvHardwareFocus'

export type TvPressableProps = PressableProps & {
  /** 界面加载后首个接收遥控焦点的控件（通常放在左上角第一个可点区域） */
  tvPreferredFocus?: boolean
  /** accent：绿描边；mutedFill：整块浅灰高亮；none：保留焦点能力但不画焦点样式 */
  tvFocusVariant?: 'accent' | 'mutedFill' | 'none'
  /** 统一暴露 TV 焦点变化，兼容 Android TV 的硬件 focus/blur 事件 */
  onTvFocusChange?: (focused: boolean) => void
}

const focusRing: ViewStyle = {
  borderWidth: TV_FOCUS_BORDER_WIDTH,
  borderColor: TV_FOCUS_BORDER,
  borderRadius: 8,
}

const focusMutedFill: ViewStyle = {
  borderWidth: 0,
  backgroundColor: TV_FOCUS_FILL,
  borderRadius: 10,
}

/**
 * 电视遥控器：Android（Paper）下 RCTView 的 `onFocus`/`onBlur` 不会从原生进 JS，焦点由 ReactRootView
 * 通过 `onHWKeyEvent`（eventType: focus/blur）派发，见 `useAndroidTvHardwareFocusRing`。iOS 等仍走 onFocus。
 */
function assignForwardedRef<T>(f: ForwardedRef<T>, node: T | null): void {
  if (f == null) return
  if (typeof f === 'function') {
    f(node)
    return
  }
  f.current = node
}

const TvPressable = forwardRef<View | null, TvPressableProps>(
  function TvPressable({ tvPreferredFocus, tvFocusVariant = 'accent', style, onFocus, onBlur, onTvFocusChange, ...rest }, ref) {
    const [focused, setFocused] = useState(false)
    const innerRef = useRef<View | null>(null)
    useAndroidTvHardwareFocusRing(innerRef, setFocused)

    const setRefs = useCallback(
      (node: View | null) => {
        innerRef.current = node
        assignForwardedRef(ref, node)
      },
      [ref],
    )

    useEffect(() => {
      onTvFocusChange?.(focused)
    }, [focused, onTvFocusChange])

    return (
      <Pressable
        ref={setRefs}
        hasTVPreferredFocus={tvPreferredFocus}
        onFocus={(e) => {
          onFocus?.(e)
          setFocused(true)
        }}
        onBlur={(e) => {
          onBlur?.(e)
          setFocused(false)
        }}
        style={(state) => {
          const ring = IS_TV_APP && focused
            ? (tvFocusVariant === 'mutedFill' ? focusMutedFill : tvFocusVariant === 'none' ? {} : focusRing)
            : {}
          if (typeof style === 'function') {
            return [style(state), ring]
          }
          return [style as StyleProp<ViewStyle>, ring]
        }}
        {...rest}
      />
    )
  },
)

export default TvPressable
