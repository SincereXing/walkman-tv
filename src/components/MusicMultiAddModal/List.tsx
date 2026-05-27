import { useMemo } from 'react'
import { ScrollView, View } from 'react-native'
import TvPressable from '@/components/tv/TvPressable'
import { tvScrollParentProps } from '@/utils/tvFocusProps'

import Text from '@/components/common/Text'
import { useMyList } from '@/store/list/hook'
import ListItem, { styles as listStyles } from './ListItem'
import { useWindowSize } from '@/utils/hooks'
import { useTheme } from '@/store/theme/hook'
import { useI18n } from '@/lang'
import { createStyle } from '@/utils/tools'
import { scaleSizeW } from '@/utils/pixelRatio'

const styles = createStyle({
  list: {
    paddingLeft: 15,
    paddingRight: 2,
    paddingBottom: 5,
    flexDirection: 'row',
    flexWrap: 'wrap',
    // backgroundColor: 'rgba(0,0,0,0.2)'
  },
})
const MIN_WIDTH = scaleSizeW(140)
const PADDING = styles.list.paddingLeft + styles.list.paddingRight

const EditListItem = ({ itemWidth, onPress, tvPreferredFocus = false }: {
  itemWidth: number
  onPress: () => void
  tvPreferredFocus?: boolean
}) => {
  const theme = useTheme()
  const t = useI18n()

  return (
    <View style={{ ...listStyles.listItem, width: itemWidth }}>
      <TvPressable
        tvPreferredFocus={tvPreferredFocus}
        style={{ ...listStyles.button, borderColor: theme['c-primary-light-200-alpha-700'], borderStyle: 'dashed' }}
        onPress={onPress}
      >
        <Text numberOfLines={1} size={14} color={theme['c-button-font']}>{t('list_create')}</Text>
      </TvPressable>
    </View>
  )
}

export default ({ listId, onPress, onCreate }: {
  listId: string
  onPress: (listInfo: LX.List.MyListInfo) => void
  onCreate: () => void
}) => {
  const windowSize = useWindowSize()
  const allList = useMyList().filter(l => l.id != listId)
  const itemWidth = useMemo(() => {
    let w = Math.floor(windowSize.width * 0.9 - PADDING)
    let n = Math.floor(w / MIN_WIDTH)
    if (n > 10) n = 10
    return Math.floor((w - 1) / n)
  }, [windowSize])

  return (
    <ScrollView style={{ flexGrow: 0 }} {...tvScrollParentProps}>
      <View style={{ ...styles.list }} onStartShouldSetResponder={() => true}>
        {allList.map((info, index) => (
          <ListItem
            key={info.id}
            listInfo={info}
            onPress={onPress}
            width={itemWidth}
            tvPreferredFocus={index === 0}
          />
        ))}
        <EditListItem itemWidth={itemWidth} onPress={onCreate} tvPreferredFocus={allList.length === 0} />
      </View>
    </ScrollView>
  )
}
