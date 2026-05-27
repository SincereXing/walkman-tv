import { useTheme } from '@/store/theme/hook'
import { useMemo, useRef, useImperativeHandle, forwardRef, useState } from 'react'
import { Pressable, type PressableProps, StyleSheet, type View, type ViewProps } from 'react-native'
import { IS_TV_APP } from '@/config/tv'
import { TV_FOCUS_BORDER, TV_FOCUS_BORDER_WIDTH } from '@/config/tvStyles'
import { useAndroidTvHardwareFocusRing } from '@/utils/androidTvHardwareFocus'
// import { AppColors } from '@/theme'


export interface BtnProps extends PressableProps {
  ripple?: PressableProps['android_ripple']
  style?: ViewProps['style']
  onChangeText?: (value: string) => void
  onClearText?: () => void
  children: React.ReactNode
}


export interface BtnType {
  measure: (callback: (x: number, y: number, width: number, height: number, pageX: number, pageY: number) => void) => void
  focus: () => void
}

export default forwardRef<BtnType, BtnProps>(({ ripple: propsRipple = {}, disabled, children, style, onFocus, onBlur, ...props }, ref) => {
  const theme = useTheme()
  const btnRef = useRef<View>(null)
  const [focused, setFocused] = useState(false)
  useAndroidTvHardwareFocusRing(btnRef, setFocused)
  const ripple = useMemo(() => ({
    color: theme['c-primary-light-200-alpha-700'],
    ...propsRipple,
  }), [theme, propsRipple])

  useImperativeHandle(ref, () => ({
    measure(callback) {
      btnRef.current?.measure(callback)
    },
    focus() {
      btnRef.current?.focus?.()
    },
  }))

  return (
    <Pressable
      android_ripple={ripple}
      disabled={disabled}
      onFocus={(e) => {
        onFocus?.(e)
        setFocused(true)
      }}
      onBlur={(e) => {
        onBlur?.(e)
        setFocused(false)
      }}
      style={() => {
        const base = StyleSheet.compose({ opacity: disabled ? 0.3 : 1 }, style)
        const ring = IS_TV_APP && focused
          ? { borderWidth: TV_FOCUS_BORDER_WIDTH, borderColor: TV_FOCUS_BORDER, borderRadius: 10 }
          : {}
        return StyleSheet.compose(base, ring)
      }}
      {...props}
      ref={btnRef}
    >
      {children}
    </Pressable>
  )
})
