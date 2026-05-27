import { useHorizontalMode } from '@/utils/hooks'
import { IS_TV_APP } from '@/config/tv'
import Vertical from './Vertical'
import Horizontal from './Horizontal'
import Tv from './Tv'

export type { SettingScreenIds } from './Main'

export default () => {
  if (IS_TV_APP) return <Tv />

  const isHorizontalMode = useHorizontalMode()

  return isHorizontalMode
    ? <Horizontal />
    : <Vertical />
}
