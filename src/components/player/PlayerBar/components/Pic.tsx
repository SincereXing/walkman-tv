import { StyleSheet } from 'react-native'
import TvPressable from '@/components/tv/TvPressable'
import { navigations } from '@/navigation'
import { usePlayerMusicInfo } from '@/store/player/hook'
import { scaleSizeH } from '@/utils/pixelRatio'
import commonState from '@/store/common/state'
import playerState from '@/store/player/state'
import { LIST_IDS, NAV_SHEAR_NATIVE_IDS } from '@/config/constant'
import Image from '@/components/common/Image'
import { useCallback } from 'react'
import { setLoadErrorPicUrl, setMusicInfo } from '@/core/player/playInfo'

const PIC_HEIGHT = scaleSizeH(46)
const PIC_HEIGHT_LARGE = scaleSizeH(132)

const styles = StyleSheet.create({
  image: {
    borderRadius: 4,
  },
  imageLarge: {
    borderRadius: 12,
  },
})

export default ({ isHome, large }: { isHome: boolean, large?: boolean }) => {
  const musicInfo = usePlayerMusicInfo()
  const handlePress = () => {
    // console.log('')
    // console.log(playMusicInfo)
    if (!musicInfo.id) return
    navigations.pushPlayDetailScreen(commonState.componentIds.home!)

    // toast(global.i18n.t('play_detail_todo_tip'), 'long')
  }

  const handleLongPress = () => {
    if (!isHome) return
    const listId = playerState.playMusicInfo.listId
    if (!listId || listId == LIST_IDS.DOWNLOAD) return
    global.app_event.jumpListPosition()
  }

  const handleError = useCallback((url: string | number) => {
    setLoadErrorPicUrl(url as string)
    setMusicInfo({
      pic: null,
    })
  }, [])

  const side = large ? PIC_HEIGHT_LARGE : PIC_HEIGHT

  return (
    <TvPressable
      onLongPress={handleLongPress}
      onPress={handlePress}
      style={large ? { alignSelf: 'center' } : undefined}
    >
      <Image
        url={musicInfo.pic}
        nativeID={NAV_SHEAR_NATIVE_IDS.playDetail_pic}
        style={[styles.image, large ? styles.imageLarge : null, { width: side, height: side }]}
        onError={handleError}
      />
    </TvPressable>
  )
}


// const styles = StyleSheet.create({
//   playInfoImg: {

//   },
// })
