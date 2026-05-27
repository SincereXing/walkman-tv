import { memo } from 'react'
import { View } from 'react-native'
import TvPressable from '@/components/tv/TvPressable'

import { Icon } from '@/components/common/Icon'
import { pop } from '@/navigation'
// import { AppColors } from '@/theme'
import StatusBar from '@/components/common/StatusBar'
import { useI18n } from '@/lang'
import { createStyle } from '@/utils/tools'
import Text from '@/components/common/Text'
import { HEADER_HEIGHT as _HEADER_HEIGHT } from '@/config/constant'
import { scaleSizeH } from '@/utils/pixelRatio'
import commonState from '@/store/common/state'
import { useStatusbarHeight } from '@/store/common/hook'

const HEADER_HEIGHT = scaleSizeH(_HEADER_HEIGHT)

export default memo(({ musicInfo }: {
  musicInfo: LX.Music.MusicInfo
}) => {
  const t = useI18n()
  const statusBarHeight = useStatusbarHeight()

  const back = () => {
    void pop(commonState.componentIds.comment!)
  }

  return (
    <View style={{ height: HEADER_HEIGHT + statusBarHeight, paddingTop: statusBarHeight }}>
      <StatusBar />
      <View style={{ ...styles.container }}>
        <TvPressable onPress={back} style={{ ...styles.button, width: HEADER_HEIGHT }}>
          <Icon name="chevron-left" size={18} />
        </TvPressable>
        <Text numberOfLines={1} size={16} style={styles.title}>{t('comment_title', { name: musicInfo.name, singer: musicInfo.singer })}</Text>
      </View>
    </View>
  )
})


const styles = createStyle({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    height: '100%',
    paddingRight: 40,
    // backgroundColor: 'rgba(255, 255, 255, 0.5)',
  },
  button: {
    // paddingLeft: 10,
    // paddingRight: 10,
    width: '100%',
    justifyContent: 'center',
    alignItems: 'center',
  },
  title: {
    flex: 1,
    textAlign: 'center',
  },
  icon: {
    paddingLeft: 4,
    paddingRight: 4,
  },
})
