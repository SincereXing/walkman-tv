import { memo } from 'react'
import { View } from 'react-native'
import { Icon } from '@/components/common/Icon'
import { useTheme } from '@/store/theme/hook'
import Text from '@/components/common/Text'
import { type RowInfo, createStyle } from '@/utils/tools'
import { IS_TV_APP } from '@/config/tv'
import TvPressable from '@/components/tv/TvPressable'
import { scaleSizeH } from '@/utils/pixelRatio'

export interface PathItem {
  name: string
  path: string
  isDir: boolean
  mtime?: Date
  desc?: string
  size?: number
  sizeText?: string
  disabled?: boolean
}

export default memo(({ item, onPress, rowInfo }: {
  item: PathItem
  onPress: (item: PathItem) => void
  rowInfo: RowInfo
}) => {
  const theme = useTheme()

  // const moreButtonRef = useRef()
  // const handleShowMenu = useCallback(() => {
  //   if (moreButtonRef.current && moreButtonRef.current.measure) {
  //     moreButtonRef.current.measure((fx, fy, width, height, px, py) => {
  //       // console.log(fx, fy, width, height, px, py)
  //       showMenu(item, index, { x: Math.ceil(px), y: Math.ceil(py), w: Math.ceil(width), h: Math.ceil(height) })
  //     })
  //   }
  // }, [item, index, showMenu])

  return (
    <View style={{ ...styles.listItem, width: rowInfo.rowWidth }} onStartShouldSetResponder={() => true}>
      {
        item.disabled ? (
          <View style={{ ...styles.listItem, opacity: 0.3 }}>
            <View style={styles.itemInfo}>
              <Text style={styles.listItemTitleText}>{item.name}</Text>
              <Text style={styles.listItemDesc} size={12} color={theme['c-font-label']} numberOfLines={1}>{item.mtime ? new Date(item.mtime).toLocaleString() : item.desc}</Text>
            </View>
            {
              item.isDir ? null
                : <Text style={styles.size} size={12} color={theme['c-font-label']}>{item.sizeText}</Text>
            }
          </View>
        ) : (
          <TvPressable style={styles.listItem} onPress={() => { onPress(item) }}>
            <View style={styles.itemInfo}>
              <Text style={styles.listItemTitleText} size={IS_TV_APP ? 15 : undefined}>{item.name}</Text>
              <Text style={styles.listItemDesc} size={IS_TV_APP ? 13 : 12} color={theme['c-font-label']} numberOfLines={1}>{item.mtime ? new Date(item.mtime).toLocaleString() : item.desc}</Text>
            </View>
            {
              item.isDir
                ? <Icon name="chevron-right" color={theme['c-primary-light-100-alpha-600']} size={IS_TV_APP ? 22 : 18} />
                : <Text style={styles.size} size={IS_TV_APP ? 13 : 12} color={theme['c-font-label']}>{item.sizeText}</Text>
            }
          </TvPressable>
        )
      }
    </View>
  )
})

const styles = createStyle({
  listItem: {
    width: '100%',
    flexDirection: 'row',
    flexWrap: 'nowrap',
    paddingLeft: IS_TV_APP ? 14 : 10,
    paddingRight: IS_TV_APP ? 14 : 10,
    alignItems: 'center',
    minHeight: IS_TV_APP ? scaleSizeH(52) : undefined,
    // backgroundColor: 'rgba(0,0,0,0.1)',
  },
  itemInfo: {
    flexGrow: 1,
    flexShrink: 1,
    paddingTop: IS_TV_APP ? 12 : 10,
    paddingBottom: IS_TV_APP ? 12 : 10,
  },
  listItemTitleText: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    // backgroundColor: 'rgba(0,0,0,0.2)',
    flexGrow: 0,
    flexShrink: 1,
  },
  listItemDesc: {
    paddingTop: 2,
  },
  size: {
    alignSelf: 'flex-end',
    marginBottom: 10,
  },
})

