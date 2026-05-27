import { memo } from 'react'
import { View } from 'react-native'

import Slider, { type SliderProps } from '@react-native-community/slider'
import { useTheme } from '@/store/theme/hook'
import { createStyle } from '@/utils/tools'
import { useTvSettingLayout } from '../tvLayoutContext'
import { scaleSizeH } from '@/utils/pixelRatio'

export type {
  SliderProps,
}

export default memo(({ value, minimumValue, maximumValue, onSlidingStart, onSlidingComplete, onValueChange, step }: SliderProps) => {
  const theme = useTheme()
  const tv = useTvSettingLayout()

  const track = (
    <Slider
      value={value}
      style={tv ? styles.sliderTv : styles.slider}
      minimumValue={minimumValue}
      maximumValue={maximumValue}
      minimumTrackTintColor={theme['c-button-background-active']}
      maximumTrackTintColor={theme['c-button-background']}
      thumbTintColor={theme['c-primary-light-100']}
      onSlidingStart={onSlidingStart}
      onSlidingComplete={onSlidingComplete}
      onValueChange={onValueChange}
      step={step}
    />
  )

  if (!tv) return track

  return <View style={styles.wrapTv}>{track}</View>
})


const styles = createStyle({
  slider: {
    flexShrink: 0,
    flexGrow: 1,
    maxWidth: 300,
    height: 40,
    marginTop: -6,
  },
  sliderTv: {
    flexShrink: 0,
    flexGrow: 1,
    width: '100%',
    maxWidth: '100%',
    height: scaleSizeH(44),
    marginTop: 0,
  },
  wrapTv: {
    width: '100%',
    paddingVertical: scaleSizeH(10),
    marginBottom: scaleSizeH(4),
    minHeight: scaleSizeH(52),
    justifyContent: 'center',
  },
})
