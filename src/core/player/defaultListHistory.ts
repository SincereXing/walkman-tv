import { LIST_IDS } from '@/config/constant'
import { getListMusics, overwriteListMusics } from '@/core/list'

export const appendMusicToDefaultList = async(musicInfo: LX.Player.PlayMusicInfo['musicInfo'] | null) => {
  if (!musicInfo || 'progress' in musicInfo) return
  const defaultList = [...await getListMusics(LIST_IDS.DEFAULT)]
  defaultList.push(musicInfo)
  await overwriteListMusics(LIST_IDS.DEFAULT, defaultList)
}
