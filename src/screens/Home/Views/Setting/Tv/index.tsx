import { useRef } from 'react'
import { View, ScrollView } from 'react-native'
import { createStyle } from '@/utils/tools'
import { TV_BG_DEEP, TV_CARD_BG, TV_BG_PANEL } from '@/config/tvStyles'
import NavList from '../Horizontal/NavList'
import Main, { type MainType } from '../Main'
import { TvSettingLayoutProvider } from '../tvLayoutContext'

const styles = createStyle({
  root: {
    flex: 1,
    flexDirection: 'row',
    backgroundColor: TV_BG_DEEP,
    paddingVertical: 16,
    paddingHorizontal: 12,
  },
  navWrap: {
    width: '26%',
    maxWidth: 280,
    minWidth: 180,
    marginRight: 12,
    borderRadius: 16,
    overflow: 'hidden',
    backgroundColor: TV_BG_PANEL,
  },
  navInner: {
    flex: 1,
    paddingVertical: 12,
  },
  scroll: {
    flex: 1,
  },
  mainPanel: {
    flex: 1,
    borderRadius: 16,
    paddingHorizontal: 24,
    paddingTop: 20,
    paddingBottom: 40,
    overflow: 'hidden',
    backgroundColor: TV_CARD_BG,
  },
})

export default () => {
  const mainRef = useRef<MainType>(null)

  return (
    <TvSettingLayoutProvider value={true}>
      <View style={styles.root}>
        <View style={styles.navWrap}>
          <View style={styles.navInner}>
            <NavList
              tvLayout
              onChangeId={(id) => {
                mainRef.current?.setActiveId(id)
              }}
            />
          </View>
        </View>
        <ScrollView
          style={styles.scroll}
          keyboardShouldPersistTaps="always"
          contentContainerStyle={{ flexGrow: 1 }}
        >
          <View style={styles.mainPanel}>
            <Main ref={mainRef} />
          </View>
        </ScrollView>
      </View>
    </TvSettingLayoutProvider>
  )
}
