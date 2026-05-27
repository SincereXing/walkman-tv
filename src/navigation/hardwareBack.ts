import { BackHandler, Platform } from 'react-native'
import { Navigation } from 'react-native-navigation'
import { IS_TV_APP } from '@/config/tv'
import { exitApp, setNavActiveId } from '@/core/common'
import commonState from '@/store/common/state'
import { confirmDialog } from '@/utils/tools'
import * as screenNames from './screenNames'
import { dismissOverlay, pop } from './utils'

interface TopNav {
  componentId: string
  componentName: string
}

let topNav: TopNav = { componentId: '', componentName: '' }

/** 记录当前栈顶屏幕，供硬件返回键 pop / 退出确认使用 */
export function registerNavigationTopTracker() {
  return Navigation.events().registerComponentDidAppearListener((event) => {
    if (event.componentType !== 'Component') return
    topNav = {
      componentId: event.componentId,
      componentName: event.componentName,
    }
  })
}

function handleHardwareBack(): boolean {
  const { componentId, componentName } = topNav

  if (!componentId) return true

  if (componentName === screenNames.VERSION_MODAL || componentName === screenNames.SYNC_MODE_MODAL) {
    void dismissOverlay(componentId)
    return true
  }

  if (componentName === screenNames.HOME_SCREEN) {
    // 非推荐页按返回键，回到推荐页
    if (
      commonState.navActiveId === 'nav_setting' ||
      commonState.navActiveId === 'nav_search' ||
      commonState.navActiveId === 'nav_love' ||
      commonState.navActiveId === 'nav_songlist' ||
      commonState.navActiveId === 'nav_top'
    ) {
      setNavActiveId('nav_recommend')
      return true
    }
    // 推荐页按返回键，弹出退出确认
    void confirmDialog({
      message: '确认退出吗？',
      cancelButtonText: '取消',
      confirmButtonText: '确定',
    }).then((ok) => {
      if (ok) exitApp('Hardware back')
    })
    return true
  }

  void pop(componentId).catch(() => {})
  return true
}

/** TV：返回键先关闭子屏，仅在首页弹窗确认退出 */
export function registerTvHardwareBack() {
  if (!IS_TV_APP || Platform.OS !== 'android') {
    return { remove: () => {} }
  }
  return BackHandler.addEventListener('hardwareBackPress', handleHardwareBack)
}
