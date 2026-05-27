import { NativeModules, Platform } from 'react-native'

interface PlaybackSpectrumNative {
  start: () => Promise<boolean>
  stop: () => Promise<void>
  addListener: (eventName: string) => void
  removeListeners: (count: number) => void
}

const PlaybackSpectrumModule =
  Platform.OS === 'android'
    ? (NativeModules.PlaybackSpectrumModule as PlaybackSpectrumNative | undefined)
    : undefined

export { PlaybackSpectrumModule }
