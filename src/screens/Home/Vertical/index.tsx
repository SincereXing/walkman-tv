import Content from './Content'
import PlayerBar from '@/components/player/PlayerBar'
import { IS_TV_APP } from '@/config/tv'

export default () => {
  return (
    <>
      <Content />
      {IS_TV_APP ? null : <PlayerBar isHome />}
    </>
  )
}
