import { View } from 'react-native'
import { useNavActiveId } from '@/store/common/hook'
import { useI18n } from '@/lang'
import { createStyle } from '@/utils/tools'
import Text from '@/components/common/Text'
import { scaleSizeH } from '@/utils/pixelRatio'
import { HEADER_HEIGHT as _HEADER_HEIGHT } from '@/config/constant'
import { type InitState as CommonState } from '@/store/common/state'
import SearchTypeSelector from '@/screens/Home/Views/Search/SearchTypeSelector'

const HEADER_HEIGHT = _HEADER_HEIGHT * 0.72

const headerComponents: Partial<Record<CommonState['navActiveId'], React.ReactNode>> = {
  nav_search: <SearchTypeSelector />,
}

export default () => {
  const id = useNavActiveId()
  const t = useI18n()

  return (
    <View style={styles.container}>
      <View style={styles.left}>
        <Text style={styles.title} size={17}>{t(id)}</Text>
      </View>
      {headerComponents[id] ?? null}
    </View>
  )
}

const styles = createStyle({
  container: {
    paddingRight: 8,
    paddingLeft: 8,
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    height: scaleSizeH(HEADER_HEIGHT),
    zIndex: 10,
  },
  left: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    height: '100%',
  },
  title: {
    paddingRight: 12,
  },
})
