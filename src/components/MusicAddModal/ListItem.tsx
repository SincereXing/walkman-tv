import { View } from 'react-native'
import TvPressable from '@/components/tv/TvPressable'
import Text from '@/components/common/Text'
import { BorderWidths } from '@/theme'
import { createStyle, toast } from '@/utils/tools'
import { useTheme } from '@/store/theme/hook'
import { useMusicExistsList } from '@/store/list/hook'
import { IS_TV_APP } from '@/config/tv'

export default ({ listInfo, onPress, musicInfo, width, tvPreferredFocus = false }: {
  listInfo: LX.List.MyListInfo
  onPress: (listInfo: LX.List.MyListInfo) => void
  musicInfo: LX.Music.MusicInfo
  width: number
  tvPreferredFocus?: boolean
}) => {
  const theme = useTheme()
  const isExists = useMusicExistsList(listInfo, musicInfo)

  const handlePress = () => {
    if (isExists) {
      toast(global.i18n.t('list_add_tip_exists'))
      return
    }
    onPress(listInfo)
  }

  return (
    <View style={{ ...styles.listItem, width }}>
      <TvPressable
        tvPreferredFocus={tvPreferredFocus}
        style={{ ...styles.button, backgroundColor: theme['c-button-background'], borderColor: theme['c-primary-light-400-alpha-300'], opacity: isExists ? 0.4 : 1 }}
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
    // backgroundColor: 'rgba(0,0,0,0.2)',
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
