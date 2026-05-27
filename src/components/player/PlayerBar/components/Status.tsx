import { useLrcPlay } from '@/plugins/lyric'
import { useIsPlay, useStatusText } from '@/store/player/hook'
// import { createStyle } from '@/utils/tools'
import Text from '@/components/common/Text'
import { TV_ACCENT_GREEN, TV_TEXT_MUTED } from '@/config/tvStyles'


export default ({ autoUpdate, sidebar }: { autoUpdate: boolean, sidebar?: boolean }) => {
  const { text } = useLrcPlay(autoUpdate)
  const statusText = useStatusText()
  const isPlay = useIsPlay()
  // console.log('render status')

  const status = isPlay ? text : statusText
  const sidebarLine = isPlay ? (text || statusText) : statusText

  if (sidebar) {
    return (
      <Text
        numberOfLines={2}
        size={14}
        color={isPlay && text ? 'rgba(255,255,255,0.95)' : TV_TEXT_MUTED}
        style={isPlay && text ? { textShadowColor: TV_ACCENT_GREEN, textShadowRadius: 6, textShadowOffset: { width: 0, height: 0 } } : undefined}
      >{sidebarLine}
      </Text>
    )
  }

  return <Text numberOfLines={1} size={12}>{status}</Text>
}

// const styles = createStyle({
//   text: {
//     // fontSize: 10,
//     // lineHeight: 18,
//     // height: 18,
//     // height: '100%',
//     // backgroundColor: 'rgba(0,0,0,0.2)',
//   },
// })
