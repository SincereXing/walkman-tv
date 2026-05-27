import { memo, type ComponentProps } from 'react'
import { Text, type TextProps as _TextProps, StyleSheet, Animated, type ColorValue, type TextStyle } from 'react-native'
import { useTextShadow, useTheme } from '@/store/theme/hook'
import { setSpText } from '@/utils/pixelRatio'
import { useAnimateColor } from '@/utils/hooks/useAnimateColor'
import { DEFAULT_DURATION, useAnimateNumber } from '@/utils/hooks/useAnimateNumber'
import { IS_TV_APP } from '@/config/tv'
// import { AppColors } from '@/theme'

const TV_DEFAULT_COLOR = 'rgba(255, 255, 255, 0.92)'

export interface TextProps extends _TextProps {
  /**
   * 字体大小
   */
  size?: number
  /**
   * 字体颜色
   */
  color?: ColorValue
}

// const warpText = <P extends TextProps>(Component: ComponentType<TextProps>) => {
//   return ({ style, size = 15, color, children, ...props }: P) => {
//     const theme = useTheme()
//     return (
//       <Component
//         style={StyleSheet.compose({ fontFamily: 'System', fontSize: setSpText(size), color: color ?? theme['c-font'] }, style)}
//         {...props}
//       >{children}</Component>
//     )
//   }
// }

export default memo(({ style, size = 15, color, children, ...props }: TextProps) => {
  const theme = useTheme()
  const textShadow = useTextShadow()
  const defaultColor = IS_TV_APP ? TV_DEFAULT_COLOR : theme['c-font']
  style = StyleSheet.compose(textShadow ? {
    // fontFamily: 'System',
    textShadowColor: theme['c-primary-dark-300-alpha-800'],
    textShadowOffset: { width: 0.2, height: 0.2 },
    textShadowRadius: 2,
    fontSize: setSpText(size),
    color: color ?? defaultColor,
  } : {
    // fontFamily: 'System',
    fontSize: setSpText(size),
    color: color ?? defaultColor,
  }, style)

  return (
    <Text
      style={style}
      {...props}
    >{children}</Text>
  )
})

export interface AnimatedTextProps extends _AnimatedTextProps {
  /**
   * 字体大小
   */
  size?: number
  /**
   * 字体颜色
   */
  color?: ColorValue
}
export const AnimatedText = ({ style, size = 15, color, children, ...props }: AnimatedTextProps) => {
  const theme = useTheme()
  const textShadow = useTextShadow()
  const defaultColor = IS_TV_APP ? TV_DEFAULT_COLOR : theme['c-font']
  style = StyleSheet.compose(textShadow ? {
    // fontFamily: 'System',
    textShadowColor: theme['c-primary-dark-300-alpha-800'],
    textShadowOffset: { width: 0.2, height: 0.2 },
    textShadowRadius: 2,
    fontSize: setSpText(size),
    color: color ?? defaultColor,
  } : {
    // fontFamily: 'System',
    fontSize: setSpText(size),
    color: color ?? defaultColor,
  }, style as TextStyle)

  return <Animated.Text style={style} {...props}>{children}</Animated.Text>
}


type _AnimatedTextProps = ComponentProps<(typeof Animated)['Text']>
export interface AnimatedColorTextProps extends _AnimatedTextProps {
  /**
   * 字体大小
   */
  size?: number
  /**
   * 字体颜色
   */
  color?: string
  /**
   * 字体透明度
   */
  opacity?: number
}
export const AnimatedColorText = ({ style, size = 15, opacity: _opacity, color: _color, children, ...props }: AnimatedColorTextProps) => {
  const theme = useTheme()
  const textShadow = useTextShadow()
  const defaultColor = IS_TV_APP ? TV_DEFAULT_COLOR : theme['c-font']

  const [color] = useAnimateColor(_color ?? defaultColor)
  const [opacity] = useAnimateNumber(_opacity ?? 1, DEFAULT_DURATION, false)

  style = StyleSheet.compose(textShadow ? {
    // fontFamily: 'System',
    textShadowColor: theme['c-primary-dark-300-alpha-800'],
    textShadowOffset: { width: 0.2, height: 0.2 },
    textShadowRadius: 2,
    fontSize: setSpText(size),
    color: color as unknown as ColorValue,
    opacity,
  } : {
    // fontFamily: 'System',
    fontSize: setSpText(size),
    color: color as unknown as ColorValue,
    opacity,
  }, style as TextStyle)

  return <Animated.Text style={style} {...props}>{children}</Animated.Text>
}
