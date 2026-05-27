import { View } from 'react-native'
import TvPressable from '@/components/tv/TvPressable'
import Text from '@/components/common/Text'
import { BorderWidths } from '@/theme'
import { createStyle } from '@/utils/tools'
import { useTheme } from '@/store/theme/hook'
import { IS_TV_APP } from '@/config/tv'

export default ({ listInfo, onPress, width, tvPreferredFocus = false }: {
  listInfo: LX.List.MyListInfo
  onPress: (listInfo: LX.List.MyListInfo) => void
  width: number
  tvPreferredFocus?: boolean
}) => {
  const theme = useTheme()

  const handlePress = () => {
    onPress(listInfo)
  }

  return (
    <View style={{ ...styles.listItem, width }}>
      <TvPressable
        tvPreferredFocus={tvPreferredFocus}
        style={{ ...styles.button, backgroundColor: theme['c-button-background'], borderColor: theme['c-primary-light-200-alpha-700'] }}
        onPress={handlePress}
      >
        <Text numberOfLines={1} size={14} color={theme['c-button-font']}>{listInfo.name}</Text>
      </TvPressable>
    </View>
  )
}

export const styles = createStyle({
  listItem: {
    // width: '50%',
    paddingRight: 13,
  },
  button: {
    height: IS_TV_APP ? 48 : 36,
    paddingLeft: IS_TV_APP ? 14 : 10,
    paddingRight: IS_TV_APP ? 14 : 10,
    marginRight: 10,
    marginBottom: 10,
    borderRadius: IS_TV_APP ? 8 : 4,
    width: '100%',
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: BorderWidths.normal1,
  },
})
