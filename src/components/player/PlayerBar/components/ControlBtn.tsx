import { View } from 'react-native'
import TvPressable from '@/components/tv/TvPressable'
import { Icon } from '@/components/common/Icon'
import { useIsPlay } from '@/store/player/hook'
import { useTheme } from '@/store/theme/hook'
import { playNext, playPrev, togglePlay } from '@/core/player/player'
import { createStyle } from '@/utils/tools'
import { useHorizontalMode } from '@/utils/hooks'
import { IS_TV_APP } from '@/config/tv'
import { TV_NAV_INACTIVE_BG } from '@/config/tvStyles'

const BTN_SIZE = 24
const BTN_SIZE_SIDEBAR = 26
const handlePlayPrev = () => {
  void playPrev()
}
const handlePlayNext = () => {
  void playNext()
}

const PlayPrevBtn = ({ sidebar }: { sidebar?: boolean }) => {
  const theme = useTheme()
  const sz = sidebar ? BTN_SIZE_SIDEBAR : BTN_SIZE

  return (
    <TvPressable style={sidebar ? styles.sideCtrl : styles.cotrolBtn} onPress={handlePlayPrev}>
      <Icon name='prevMusic' color={sidebar ? 'rgba(255,255,255,0.92)' : theme['c-button-font']} size={sz} />
    </TvPressable>
  )
}

const PlayNextBtn = ({ sidebar }: { sidebar?: boolean }) => {
  const theme = useTheme()
  const sz = sidebar ? BTN_SIZE_SIDEBAR : BTN_SIZE

  return (
    <TvPressable style={sidebar ? styles.sideCtrl : styles.cotrolBtn} onPress={handlePlayNext}>
      <Icon name='nextMusic' color={sidebar ? 'rgba(255,255,255,0.92)' : theme['c-button-font']} size={sz} />
    </TvPressable>
  )
}

const TogglePlayBtn = ({ sidebar }: { sidebar?: boolean }) => {
  const isPlay = useIsPlay()
  const theme = useTheme()
  const sz = sidebar ? 30 : BTN_SIZE

  return (
    <TvPressable
      style={sidebar ? styles.sidePlay : styles.cotrolBtn}
      onPress={togglePlay}
    >
      <Icon
        name={isPlay ? 'pause' : 'play'}
        color={sidebar ? '#121418' : theme['c-button-font']}
        size={sz}
      />
    </TvPressable>
  )
}

export default ({ sidebar }: { sidebar?: boolean }) => {
  const isHorizontalMode = useHorizontalMode()
  const useSidebarUi = Boolean(sidebar && IS_TV_APP)

  const inner = (
    <>
      { isHorizontalMode ? <PlayPrevBtn sidebar={useSidebarUi} /> : null }
      <TogglePlayBtn sidebar={useSidebarUi} />
      <PlayNextBtn sidebar={useSidebarUi} />
    </>
  )

  if (useSidebarUi) {
    return (
      <View style={styles.sidePill}>
        {inner}
      </View>
    )
  }

  return <>{inner}</>
}


const styles = createStyle({
  cotrolBtn: {
    width: 46,
    height: 46,
    justifyContent: 'center',
    alignItems: 'center',

    // backgroundColor: '#ccc',
    shadowOpacity: 1,
    textShadowRadius: 1,
  },
  sidePill: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: 999,
    backgroundColor: TV_NAV_INACTIVE_BG,
    gap: 6,
  },
  sideCtrl: {
    width: 44,
    height: 44,
    justifyContent: 'center',
    alignItems: 'center',
    borderRadius: 22,
  },
  sidePlay: {
    width: 52,
    height: 52,
    justifyContent: 'center',
    alignItems: 'center',
    borderRadius: 26,
    backgroundColor: '#fff',
  },
})
