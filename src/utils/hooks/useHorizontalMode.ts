import { useWindowSize } from '@/utils/hooks'
import { IS_TV_APP } from '@/config/tv'
import { isHorizontalMode } from '../tools'


export default () => {
  const windowSize = useWindowSize()
  if (IS_TV_APP) return true

  return isHorizontalMode(windowSize.width, windowSize.height)
}
