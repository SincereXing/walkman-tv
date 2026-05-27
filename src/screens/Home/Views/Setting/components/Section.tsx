import { View } from 'react-native'

import { createStyle } from '@/utils/tools'
import { useTheme } from '@/store/theme/hook'
import Text from '@/components/common/Text'
import { useTvSettingLayout } from '../tvLayoutContext'


interface Props {
  title: string
  children: React.ReactNode | React.ReactNode[]
}

export default ({ title, children }: Props) => {
  const theme = useTheme()
  const tv = useTvSettingLayout()

  return (
    <View style={tv ? styles.containerTv : styles.container}>
      <Text
        style={{ ...styles.title, ...(tv ? styles.titleTv : {}), borderLeftColor: theme['c-primary'] }}
        size={tv ? 17 : 16}
      >{title}
      </Text>
      <View>
        {children}
      </View>
    </View>
  )
}


const styles = createStyle({
  container: {
    // paddingLeft: 10,
    // backgroundColor: 'rgba(0,0,0,0.2)',
  },
  containerTv: {
    marginBottom: 20,
  },
  title: {
    borderLeftWidth: 5,
    paddingLeft: 12,
    marginBottom: 10,
    // lineHeight: 16,
  },
  titleTv: {
    marginBottom: 14,
  },
})
