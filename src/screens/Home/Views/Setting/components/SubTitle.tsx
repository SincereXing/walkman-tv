import { memo } from 'react'

import { View } from 'react-native'
import { createStyle } from '@/utils/tools'
import Text from '@/components/common/Text'
import { useTvSettingLayout } from '../tvLayoutContext'

export default memo(({ title, children }: {
  title: string
  children: React.ReactNode | React.ReactNode[]
}) => {
  const tv = useTvSettingLayout()
  return (
    <View style={tv ? styles.containerTv : styles.container}>
      <Text style={styles.title} size={tv ? 15 : undefined}>{title}</Text>
      {children}
    </View>
  )
})


const styles = createStyle({
  container: {
    paddingLeft: 25,
    marginBottom: 18,
  },
  containerTv: {
    paddingLeft: 12,
    marginBottom: 22,
  },
  title: {
    marginLeft: -10,
    marginBottom: 10,
    // lineHeight: 16,
  },
})
