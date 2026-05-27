import { memo, useMemo } from 'react'
import { View } from 'react-native'
import { useKeyboard } from '@/utils/hooks'

import Pic from './components/Pic'
import Title from './components/Title'
import PlayInfo from './components/PlayInfo'
import ControlBtn from './components/ControlBtn'
import { createStyle } from '@/utils/tools'
import { BorderWidths } from '@/theme'
// import { useSettingValue } from '@/store/setting/hook'
import { useTheme } from '@/store/theme/hook'
import { useSettingValue } from '@/store/setting/hook'


export default memo(({ isHome = false, sidebar = false }: { isHome?: boolean, sidebar?: boolean }) => {
  // const { onLayout, ...layout } = useLayout()
  const { keyboardShown } = useKeyboard()
  const theme = useTheme()
  const autoHidePlayBar = useSettingValue('common.autoHidePlayBar')

  const playerComponent = useMemo(() => {
    if (sidebar) {
      return (
        <View style={{
          ...styles.sidebarRoot,
          backgroundColor: theme['c-content-background'],
          borderRightColor: theme['c-border-background'],
        }}
        >
          <Pic isHome={isHome} large />
          <View style={styles.sidebarCenter}>
            <Title isHome={isHome} sidebar />
            <PlayInfo isHome={isHome} sidebar />
          </View>
          <View style={styles.sidebarControls}>
            <ControlBtn sidebar />
          </View>
        </View>
      )
    }
    return (
      <View style={{ ...styles.container, backgroundColor: theme['c-content-background'] }}>
        <Pic isHome={isHome} />
        <View style={styles.center}>
          <Title isHome={isHome} />
          {/* <View style={{ ...styles.row, justifyContent: 'space-between' }}>
          <PlayTime />
        </View> */}
          <PlayInfo isHome={isHome} />
        </View>
        <View style={styles.right}>
          <ControlBtn />
        </View>
      </View>
    )
  }, [theme, isHome, sidebar])

  // console.log('render pb')

  return autoHidePlayBar && keyboardShown ? null : playerComponent
})


const styles = createStyle({
  sidebarRoot: {
    flex: 1,
    width: '100%',
    flexDirection: 'column',
    alignItems: 'stretch',
    paddingVertical: 10,
    paddingHorizontal: 8,
    borderTopLeftRadius: 0,
    borderTopRightRadius: 0,
    elevation: 4,
    borderRightWidth: BorderWidths.normal,
  },
  sidebarCenter: {
    flexGrow: 1,
    flexShrink: 1,
    width: '100%',
    justifyContent: 'center',
    paddingTop: 8,
  },
  sidebarControls: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    paddingTop: 10,
    width: '100%',
  },
  container: {
    width: '100%',
    // height: 100,
    // paddingTop: progressContentPadding,
    // marginTop: -progressContentPadding,
    // backgroundColor: 'rgba(0, 0, 0, .1)',
    // borderTopWidth: BorderWidths.normal2,
    paddingVertical: 5,
    paddingLeft: 5,
    // backgroundColor: AppColors.primary,
    // backgroundColor: 'red',
    borderTopLeftRadius: 6,
    borderTopRightRadius: 6,
    flexDirection: 'row',
    alignItems: 'center',
    elevation: 10,
  },
  left: {
    // borderRadius: 3,
    flexGrow: 0,
    flexShrink: 0,
  },
  center: {
    flexDirection: 'column',
    flexGrow: 1,
    flexShrink: 1,
    paddingLeft: 5,
    height: '100%',
    // justifyContent: 'space-evenly',
    // height: 48,
    // backgroundColor: 'rgba(0, 0, 0, .1)',
  },
  right: {
    flexDirection: 'row',
    alignItems: 'center',
    flexGrow: 0,
    flexShrink: 0,
    paddingLeft: 5,
    paddingRight: 5,
  },
  // row: {
  //   flexDirection: 'row',
  //   flexGrow: 0,
  //   flexShrink: 0,
  // },
})
