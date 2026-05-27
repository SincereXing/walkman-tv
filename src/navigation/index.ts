import { type EmitterSubscription } from 'react-native'
import { Navigation } from 'react-native-navigation'
import * as screenNames from './screenNames'
import * as navigations from './navigation'

import registerScreens from './registerScreens'
import { removeComponentId } from '@/core/common'
import { onAppLaunched } from './regLaunchedEvent'
import { registerNavigationTopTracker, registerTvHardwareBack } from './hardwareBack'
import { IS_TV_APP } from '@/config/tv'
import { TV_BG_DEEP } from '@/config/tvStyles'

let unRegisterEvent: ReturnType<ReturnType<typeof Navigation.events>['registerScreenPoppedListener']>
let unRegisterTopNav: EmitterSubscription | undefined
let tvHardwareBackSub: ReturnType<typeof registerTvHardwareBack> | undefined

const init = (callback: () => void | Promise<void>) => {
  // Register all screens on launch
  registerScreens()

  if (unRegisterEvent) unRegisterEvent.remove()
  if (unRegisterTopNav) unRegisterTopNav.remove()
  if (tvHardwareBackSub) tvHardwareBackSub.remove()

  unRegisterTopNav = registerNavigationTopTracker()
  tvHardwareBackSub = registerTvHardwareBack()

  Navigation.setDefaultOptions({
    animations: {
      setRoot: {
        waitForRender: true,
      },
    },
    layout: {
      componentBackgroundColor: IS_TV_APP ? TV_BG_DEEP : undefined,
      backgroundColor: IS_TV_APP ? TV_BG_DEEP : undefined,
    },
  })
  unRegisterEvent = Navigation.events().registerScreenPoppedListener(({ componentId }) => {
    removeComponentId(componentId)
  })
  onAppLaunched(() => {
    console.log('Register app launched listener')
    void callback()
  })
}

export * from './utils'
export * from './event'
export * from './hooks'

export {
  init,
  screenNames,
  navigations,
}
