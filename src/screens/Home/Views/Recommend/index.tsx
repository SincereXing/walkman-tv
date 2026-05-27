import { memo, useCallback, useEffect, useMemo, useState } from 'react'
import { ImageBackground, ScrollView, View, type LayoutChangeEvent } from 'react-native'
import Text from '@/components/common/Text'
import TvPressable from '@/components/tv/TvPressable'
import { setNavActiveId } from '@/core/common'
import { createStyle } from '@/utils/tools'
import { TV_CARD_BG } from '@/config/tvStyles'
import { useI18n } from '@/lang'
import listState from '@/store/list/state'
import { getListMusics, setActiveList } from '@/core/list'

const COL_GAP = 12
const ROW_GAP = 12
const CARD_H = 168
const HALF_CARD_H = (CARD_H - ROW_GAP) / 2

const IMG = {
  guess: 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&q=80',
  daily: 'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&q=80',
  chart: 'https://images.unsplash.com/photo-1493225255756-d9584f8606e9?w=600&q=80',
  new: 'https://images.unsplash.com/photo-1619983081563-430f63602796?w=600&q=80',
  album: 'https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=600&q=80',
  classic: 'https://images.unsplash.com/photo-1514320291840-2e0a9bf2a9ae?w=600&q=80',
  drive: 'https://images.unsplash.com/photo-1525085475163-c65546867694?w=600&q=80',
  vinyl: 'https://images.unsplash.com/photo-1605218427368-35b844d95791?w=600&q=80',
  played: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=200&q=60',
  love: 'https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=200&q=60',
} as const

const styles = createStyle({
  scroll: { flex: 1 },
  scrollContent: { paddingBottom: 24, paddingTop: 4 },
  row3: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: ROW_GAP },
  card: {
    borderRadius: 12,
    overflow: 'hidden',
    height: CARD_H,
    backgroundColor: TV_CARD_BG,
  },
  cardImg: { flex: 1, justifyContent: 'flex-end' },
  cardOverlay: {
    paddingHorizontal: 14,
    paddingVertical: 16,
    backgroundColor: 'rgba(0,0,0,0.55)',
  },
  cardTitle: { fontWeight: '600' },
  halfCard: {
    borderRadius: 10,
    overflow: 'hidden',
    height: HALF_CARD_H,
    backgroundColor: TV_CARD_BG,
  },
  halfCardImg: { flex: 1, justifyContent: 'center', paddingHorizontal: 14 },
  listRow: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 34,
    overflow: 'hidden',
    backgroundColor: TV_CARD_BG,
    height: 72,
    paddingHorizontal: 10,
  },
  listAvatar: {
    width: 48,
    height: 48,
    borderRadius: 24,
    overflow: 'hidden',
    marginRight: 10,
  },
  listAvatarImg: { width: '100%', height: '100%' },
  listBody: { flex: 1, justifyContent: 'center', minWidth: 0, paddingRight: 8 },
  listLabel: { fontWeight: '600' },
  listCount: { fontWeight: '700' },
  statsRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: ROW_GAP },
})

const GridCard = memo(({ title, img, onPress }: { title: string, img: string, onPress: () => void }) => (
  <TvPressable style={styles.card} onPress={onPress}>
    <ImageBackground source={{ uri: img }} style={styles.cardImg} resizeMode="cover">
      <View style={styles.cardOverlay}>
        <Text style={styles.cardTitle} size={15} color="#fff">{title}</Text>
      </View>
    </ImageBackground>
  </TvPressable>
))

const HalfCard = memo(({ title, img, onPress }: { title: string, img: string, onPress: () => void }) => (
  <TvPressable style={styles.halfCard} onPress={onPress}>
    <ImageBackground source={{ uri: img }} style={styles.halfCardImg} resizeMode="cover">
      <Text style={styles.cardTitle} size={14} color="#fff">{title}</Text>
    </ImageBackground>
  </TvPressable>
))

const ListStatCard = memo(({
  title,
  count,
  img,
  onPress,
}: {
  title: string
  count: number
  img: string
  onPress: () => void
}) => (
  <TvPressable style={styles.listRow} onPress={onPress}>
    <View style={styles.listAvatar}>
      <ImageBackground source={{ uri: img }} style={styles.listAvatarImg} resizeMode="cover" />
    </View>
    <View style={styles.listBody}>
      <Text style={styles.listLabel} size={15} color="rgba(255,255,255,0.76)" numberOfLines={1}>{title}</Text>
    </View>
    <Text style={styles.listCount} size={18} color="#fff" numberOfLines={1}>{count}</Text>
  </TvPressable>
))

export default memo(() => {
  const t = useI18n()
  const [containerW, setContainerW] = useState(0)
  const [cellW, setCellW] = useState(120)
  const [played, setPlayed] = useState(0)
  const [loved, setLoved] = useState(0)

  const reloadCounts = useCallback(() => {
    void Promise.all([
      getListMusics(listState.defaultList.id),
      getListMusics(listState.loveList.id),
    ]).then(([defaultList, lovedList]) => {
      setPlayed(defaultList.length)
      setLoved(lovedList.length)
    })
  }, [])

  useEffect(() => {
    reloadCounts()
    global.app_event.on('myListMusicUpdate', reloadCounts)
    return () => {
      global.app_event.off('myListMusicUpdate', reloadCounts)
    }
  }, [reloadCounts])

  const onContainerLayout = useCallback((e: LayoutChangeEvent) => {
    const w = e.nativeEvent.layout.width
    setContainerW(w)
    setCellW((w - COL_GAP * 2) / 3)
  }, [])

  const cwStyle = useMemo(() => ({ width: cellW }), [cellW])
  const statsW = useMemo(() => {
    if (!containerW) return 0
    return (containerW - COL_GAP) / 2
  }, [containerW])
  const statsStyle = useMemo(() => ({ width: statsW }), [statsW])

  const goLove = useCallback(() => { setNavActiveId('nav_love') }, [])
  const goPlayed = useCallback(() => {
    setNavActiveId('nav_love')
    setActiveList(listState.defaultList.id)
  }, [])
  const goSonglist = useCallback(() => { setNavActiveId('nav_songlist') }, [])
  const goTop = useCallback(() => { setNavActiveId('nav_top') }, [])

  return (
    <ScrollView style={styles.scroll} contentContainerStyle={styles.scrollContent} keyboardShouldPersistTaps="handled">
      <View onLayout={onContainerLayout}>
        <View style={styles.row3}>
          <View style={cwStyle}><GridCard title={t('recommend_card_guess')} img={IMG.guess} onPress={goLove} /></View>
          <View style={cwStyle}><GridCard title={t('recommend_card_daily')} img={IMG.daily} onPress={goSonglist} /></View>
          <View style={[cwStyle, { justifyContent: 'space-between' }]}>
            <HalfCard title={t('nav_top')} img={IMG.chart} onPress={goTop} />
            <HalfCard title={t('recommend_card_new')} img={IMG.new} onPress={goSonglist} />
          </View>
        </View>

        <View style={styles.statsRow}>
          <View style={statsStyle}>
            <ListStatCard title="已播" count={played} img={IMG.played} onPress={goPlayed} />
          </View>
          <View style={statsStyle}>
            <ListStatCard title="收藏" count={loved} img={IMG.love} onPress={goLove} />
          </View>
        </View>

        <View style={styles.row3}>
          <View style={cwStyle}><GridCard title={t('recommend_card_new_album')} img={IMG.album} onPress={goSonglist} /></View>
          <View style={cwStyle}><GridCard title={t('recommend_card_classic')} img={IMG.classic} onPress={goSonglist} /></View>
          <View style={[cwStyle, { justifyContent: 'space-between' }]}>
            <HalfCard title={t('recommend_card_drive')} img={IMG.drive} onPress={goSonglist} />
            <HalfCard title={t('recommend_card_vinyl')} img={IMG.vinyl} onPress={goSonglist} />
          </View>
        </View>
      </View>
    </ScrollView>
  )
})
