import { memo, useMemo } from 'react'
import { toast } from '@/utils/tools'
import { MUSIC_TOGGLE_MODE } from '@/config/constant'
import { useSettingValue } from '@/store/setting/hook'
import { useI18n } from '@/lang'
import { updateSetting } from '@/core/common'
import TvPressable from '@/components/tv/TvPressable'
import { Icon } from '@/components/common/Icon'
import { TV_REF_ICON } from '@/config/tvStyles'

/** TV 全屏：单曲循环 ↔ 列表顺序循环 ↔ 随机 */
const TV_CYCLE = [
  MUSIC_TOGGLE_MODE.singleLoop,
  MUSIC_TOGGLE_MODE.listLoop,
  MUSIC_TOGGLE_MODE.random,
] as const

export default memo(({ iconColor = TV_REF_ICON }: { iconColor?: string }) => {
  const togglePlayMethod = useSettingValue('player.togglePlayMethod')
  const t = useI18n()

  const playModeIcon = useMemo(() => {
    switch (togglePlayMethod) {
      case MUSIC_TOGGLE_MODE.listLoop:
        return 'list-loop'
      case MUSIC_TOGGLE_MODE.random:
        return 'list-random'
      case MUSIC_TOGGLE_MODE.list:
        return 'list-order'
      case MUSIC_TOGGLE_MODE.singleLoop:
        return 'single-loop'
      default:
        return 'single'
    }
  }, [togglePlayMethod])

  const toggleNextPlayMode = () => {
    let idx = TV_CYCLE.findIndex(m => m === togglePlayMethod)
    if (idx < 0) idx = -1
    const next = TV_CYCLE[(idx + 1) % TV_CYCLE.length]
    updateSetting({ 'player.togglePlayMethod': next })

    let modeName: 'play_list_loop' | 'play_list_random' | 'play_list_order' | 'play_single_loop' | 'play_single'
    switch (next) {
      case MUSIC_TOGGLE_MODE.listLoop:
        modeName = 'play_list_loop'
        break
      case MUSIC_TOGGLE_MODE.random:
        modeName = 'play_list_random'
        break
      case MUSIC_TOGGLE_MODE.singleLoop:
        modeName = 'play_single_loop'
        break
      default:
        modeName = 'play_list_order'
        break
    }
    toast(t(modeName), 'short')
  }

  return (
    <TvPressable
      tvFocusVariant="mutedFill"
      style={{ minWidth: 44, minHeight: 44, justifyContent: 'center', alignItems: 'center' }}
      onPress={toggleNextPlayMode}
      accessibilityLabel="播放模式"
    >
      <Icon name={playModeIcon} color={iconColor} size={22} />
    </TvPressable>
  )
})
