import { View } from 'react-native'
import StatusBar from '@/components/common/StatusBar'
import Main from '../Horizontal/Main'
import TopNav from './TopNav'
import { createStyle } from '@/utils/tools'
import { TV_BG_DEEP } from '@/config/tvStyles'
import { useNavActiveId } from '@/store/common/hook'
import TvRecommendLeftPanel from './TvRecommendLeftPanel'

const styles = createStyle({
  root: {
    flex: 1,
    flexDirection: 'column',
    backgroundColor: TV_BG_DEEP,
  },
  body: {
    flex: 1,
    flexDirection: 'row',
    minHeight: 0,
  },
  /** 参考 HTML 左栏约 480px、与右侧 gap 30 */
  sidebarRecommend: {
    width: '32%',
    maxWidth: 480,
    minWidth: 220,
    flexShrink: 0,
    marginLeft: 12,
    marginVertical: 8,
    marginRight: 16,
    overflow: 'hidden',
    minHeight: 0,
  },
  content: {
    flex: 1,
    minWidth: 0,
    overflow: 'hidden',
  },
  contentSearchFull: {
    marginHorizontal: 0,
  },
  contentInner: {
    flex: 1,
    paddingHorizontal: 12,
    minHeight: 0,
  },
  contentInnerSearchFull: {
    paddingHorizontal: 0,
  },
})

export default () => {
  const navId = useNavActiveId()
  const isRecommend = navId === 'nav_recommend'
  const isSearch = navId === 'nav_search'

  return (
    <>
      <StatusBar />
      <View style={styles.root}>
        {isSearch ? null : <TopNav />}
        <View style={styles.body}>
          {
            isRecommend
              ? (
                  <View style={styles.sidebarRecommend}>
                    <TvRecommendLeftPanel />
                  </View>
                )
              : null
          }
          <View style={[styles.content, isSearch ? styles.contentSearchFull : null]}>
            <View style={[styles.contentInner, isSearch ? styles.contentInnerSearchFull : null]}>
              <Main />
            </View>
          </View>
        </View>
      </View>
    </>
  )
}
