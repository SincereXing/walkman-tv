import { memo, useRef, useState } from 'react'
import { View, FlatList, type FlatListProps } from 'react-native'
import TvPressable from '@/components/tv/TvPressable'
import { IS_TV_APP } from '@/config/tv'

import { Icon } from '@/components/common/Icon'

import { useTheme } from '@/store/theme/hook'
import { createStyle } from '@/utils/tools'
import Text from '@/components/common/Text'
import { scaleSizeH } from '@/utils/pixelRatio'
import { SETTING_SCREENS, type SettingScreenIds } from '../Main'
import { useI18n } from '@/lang'
import { TV_NAV_ACTIVE_BG, TV_NAV_ACTIVE_FG } from '@/config/tvStyles'

type FlatListType = FlatListProps<SettingScreenIds>

const ITEM_HEIGHT = scaleSizeH(40)
const ITEM_HEIGHT_TV = scaleSizeH(54)

const ListItem = memo(({ id, activeId, onPress, tvLayout }: {
  onPress: (item: SettingScreenIds) => void
  activeId: string
  id: SettingScreenIds
  tvLayout?: boolean
}) => {
  const theme = useTheme()
  const t = useI18n()

  const active = activeId == id

  const handlePress = () => {
    onPress(id)
  }

  const handleFocus = () => {
    if (tvLayout) onPress(id)
  }

  if (tvLayout) {
    return (
      <View style={[styles.listItemTv, { marginHorizontal: 8, marginVertical: 4 }]}>
        <TvPressable
          style={[
            styles.listNameTv,
            active ? { backgroundColor: TV_NAV_ACTIVE_BG } : null,
          ]}
          onPress={handlePress}
          onFocus={handleFocus}
        >
          <Text
            numberOfLines={1}
            size={15}
            color={active ? TV_NAV_ACTIVE_FG : 'rgba(255,255,255,0.92)'}
            style={styles.listLabelTv}
          >{t(`setting_${id}`)}
          </Text>
        </TvPressable>
      </View>
    )
  }

  return (
    <View style={{ ...styles.listItem, height: ITEM_HEIGHT }}>
      {
        active
          ? <Icon style={styles.listActiveIcon} name="chevron-right" size={12} color={theme['c-primary-font']} />
          : null
      }
      <TvPressable style={styles.listName} onPress={handlePress}>
        <Text numberOfLines={1} size={16} color={active ? theme['c-primary-font'] : theme['c-font']}>{t(`setting_${id}`)}</Text>
      </TvPressable>
    </View>
  )
}, (prev, next) => {
  return prev.id === next.id && prev.activeId === next.activeId && prev.tvLayout === next.tvLayout
})


export default ({ onChangeId, tvLayout }: {
  onChangeId: (id: SettingScreenIds) => void
  tvLayout?: boolean
}) => {
  const flatListRef = useRef<FlatList>(null)
  const [activeId, setActiveId] = useState(global.lx.settingActiveId)
  const rowHeight = tvLayout ? ITEM_HEIGHT_TV : ITEM_HEIGHT

  const handleChangeId = (id: SettingScreenIds) => {
    onChangeId(id)
    setActiveId(id)
    global.lx.settingActiveId = id
  }

  const renderItem: FlatListType['renderItem'] = ({ item }) => (
    <ListItem
      id={item}
      activeId={activeId}
      tvLayout={tvLayout}
      onPress={handleChangeId}
    />
  )
  const getkey: FlatListType['keyExtractor'] = item => item
  const getItemLayout: FlatListType['getItemLayout'] | undefined = tvLayout
    ? undefined
    : (data, index) => {
        return { length: rowHeight, offset: rowHeight * index, index }
      }

  return (
    <FlatList
      ref={flatListRef}
      style={styles.container}
      data={SETTING_SCREENS}
      maxToRenderPerBatch={9}
      windowSize={9}
      removeClippedSubviews={!IS_TV_APP && !tvLayout}
      initialNumToRender={18}
      renderItem={renderItem}
      keyExtractor={getkey}
      extraData={activeId}
      getItemLayout={getItemLayout}
    />
  )
}


const styles = createStyle({
  container: {
    flexShrink: 1,
    flexGrow: 0,
  },
  // listContainer: {
  //   // borderBottomWidth: BorderWidths.normal2,
  // },

  listItem: {
    height: 'auto',
    flexDirection: 'row',
    alignItems: 'center',
    paddingRight: 10,
    paddingLeft: 10,
    // borderBottomWidth: BorderWidths.normal,
  },
  listActiveIcon: {
    // width: 18,
    marginLeft: 3,
    // paddingRight: 5,
    textAlign: 'center',
  },
  listName: {
    height: '100%',
    // height: 46,
    // paddingTop: 12,
    // paddingBottom: 12,
    justifyContent: 'center',
    flexGrow: 1,
    flexShrink: 1,
    paddingLeft: 5,
    // backgroundColor: 'rgba(0,0,0,0.1)',
  },
  listItemTv: {
    minHeight: ITEM_HEIGHT_TV,
    justifyContent: 'center',
  },
  listNameTv: {
    minHeight: ITEM_HEIGHT_TV,
    justifyContent: 'center',
    paddingHorizontal: 14,
    borderRadius: 12,
  },
  listLabelTv: {
    fontWeight: '500',
  },
})

