import { memo } from 'react'

import { View } from 'react-native'

import CheckBox, { type CheckBoxProps } from '@/components/common/CheckBox'
import { createStyle } from '@/utils/tools'
import { useTvSettingLayout } from '../tvLayoutContext'
import { scaleSizeH } from '@/utils/pixelRatio'


export default memo((props: CheckBoxProps) => {
  const tv = useTvSettingLayout()
  return (
    <View style={tv ? styles.containerTv : styles.container}>
      <CheckBox {...props} />
    </View>
  )
})

const styles = createStyle({
  container: {
    paddingLeft: 25,
    // marginTop: -10,
    // marginBottom: 0,
  },
  containerTv: {
    paddingLeft: 12,
    paddingVertical: scaleSizeH(8),
    minHeight: scaleSizeH(48),
  },
})

