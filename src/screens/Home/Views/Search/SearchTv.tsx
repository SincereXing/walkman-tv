import { memo, useCallback, useEffect, useState, type RefObject, type MutableRefObject } from 'react'
import { ScrollView, View } from 'react-native'
import TvPressable from '@/components/tv/TvPressable'
import { tvScrollParentProps } from '@/utils/tvFocusProps'
import Text from '@/components/common/Text'
import { createStyle } from '@/utils/tools'
import HeaderBar, { type HeaderBarProps, type HeaderBarType } from './HeaderBar'
import TipList, { type TipListType } from './TipList'
import List, { type ListType } from './List'
import SearchTypeSelector from './SearchTypeSelector'
import searchState from '@/store/search/state'
import {
  TV_BG_DEEP,
  TV_CARD_BG,
  TV_TEXT_MUTED,
} from '@/config/tvStyles'

const LETTERS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('')
const DIGITS = '0123456789'.split('')

const DiscoveryTags = memo(({ onPick }: { onPick: (w: string) => void }) => {
  const [tags, setTags] = useState<string[]>([])

  useEffect(() => {
    const id = setInterval(() => {
      setTags([...searchState.tipListInfo.list])
    }, 320)
    return () => {
      clearInterval(id)
    }
  }, [])

  if (!tags.length) return null

  return (
    <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.discScroll} {...tvScrollParentProps}>
      <View style={styles.discRow}>
        {tags.slice(0, 24).map((word, i) => (
          <TvPressable
            key={`${word}-${i}`}
            style={styles.discChip}
            onPress={() => { onPick(word) }}
          >
            <Text numberOfLines={1} size={13} color="rgba(255,255,255,0.88)">{word}</Text>
          </TvPressable>
        ))}
      </View>
    </ScrollView>
  )
})

const TvKeyboard = memo(({
  onKey,
  onDelete,
  onClear,
  onSymbol,
  preferFirstKeyFocus,
}: {
  onKey: (c: string) => void
  onDelete: () => void
  onClear: () => void
  onSymbol: () => void
  preferFirstKeyFocus?: boolean
}) => {
  const firstRow = LETTERS.slice(0, 13)
  return (
    <View style={styles.kbWrap}>
      <View style={styles.kbRow}>
        {firstRow.map((c, i) => (
          <TvPressable
            key={c}
            tvPreferredFocus={Boolean(preferFirstKeyFocus && i === 0)}
            style={styles.keyCell}
            onPress={() => { onKey(c) }}
          >
            <Text size={15} color="#fff">{c}</Text>
          </TvPressable>
        ))}
      </View>
      <View style={styles.kbRow}>
        {LETTERS.slice(13).map(c => (
          <TvPressable key={c} style={styles.keyCell} onPress={() => { onKey(c) }}>
            <Text size={15} color="#fff">{c}</Text>
          </TvPressable>
        ))}
      </View>
      <View style={styles.kbRow}>
        {DIGITS.map(c => (
          <TvPressable key={c} style={styles.keyCell} onPress={() => { onKey(c) }}>
            <Text size={15} color="#fff">{c}</Text>
          </TvPressable>
        ))}
      </View>
      <View style={[styles.kbRow, { marginTop: 8, justifyContent: 'space-between' }]}>
        <TvPressable style={styles.auxBtn} onPress={onClear}>
          <Text size={13} color="#fff">清空</Text>
        </TvPressable>
        <TvPressable style={styles.auxBtn} onPress={onSymbol}>
          <Text size={13} color="#fff">+=@</Text>
        </TvPressable>
        <TvPressable style={styles.auxBtn} onPress={onDelete}>
          <Text size={13} color="#fff">删除</Text>
        </TvPressable>
      </View>
    </View>
  )
})

interface Props {
  headerBarRef: RefObject<HeaderBarType | null>
  searchTipListRef: RefObject<TipListType | null>
  listRef: RefObject<ListType | null>
  layoutHeightRef: MutableRefObject<number>
  onSourceChange: HeaderBarProps['onSourceChange']
  onTipSearch: HeaderBarProps['onTipSearch']
  onSearch: HeaderBarProps['onSearch']
  onHideTipList: HeaderBarProps['onHideTipList']
  onShowTipList: HeaderBarProps['onShowTipList']
  appendChar: (c: string) => void
  deleteChar: () => void
  clearText: () => void
}

export default memo(({
  headerBarRef,
  searchTipListRef,
  listRef,
  layoutHeightRef,
  onSourceChange,
  onTipSearch,
  onSearch,
  onHideTipList,
  onShowTipList,
  appendChar,
  deleteChar,
  clearText,
}: Props) => {
  const handleLayout = useCallback((e: { nativeEvent: { layout: { height: number } } }) => {
    layoutHeightRef.current = e.nativeEvent.layout.height
  }, [layoutHeightRef])

  const handleDiscoveryPick = useCallback((word: string) => {
    onSearch(word)
  }, [onSearch])

  const onSymbol = useCallback(() => {
    appendChar('@')
  }, [appendChar])

  return (
    <View style={styles.root}>
      <View style={styles.leftCol}>
        <View style={styles.typeRow}>
          <SearchTypeSelector />
        </View>
        <HeaderBar
          ref={headerBarRef}
          onSourceChange={onSourceChange}
          onTipSearch={onTipSearch}
          onSearch={onSearch}
          onHideTipList={onHideTipList}
          onShowTipList={onShowTipList}
        />
        <Text style={styles.hint} size={12} color={TV_TEXT_MUTED}>
          输入首字母或者全拼 · 搜索周杰伦可输入 ZJL 或 zhoujielun
        </Text>
        <TvKeyboard
          preferFirstKeyFocus
          onKey={appendChar}
          onDelete={deleteChar}
          onClear={clearText}
          onSymbol={onSymbol}
        />
      </View>
      <View style={styles.rightCol} onLayout={handleLayout}>
        <Text style={styles.sectionTitle} size={16} color="#fff">搜索发现</Text>
        <DiscoveryTags onPick={handleDiscoveryPick} />
        <View style={styles.listArea}>
          <View style={styles.tipHidden}>
            <TipList ref={searchTipListRef} onSearch={onSearch} />
          </View>
          <List ref={listRef} onSearch={onSearch} />
        </View>
      </View>
    </View>
  )
})

const styles = createStyle({
  root: {
    flex: 1,
    flexDirection: 'row',
    backgroundColor: TV_BG_DEEP,
    paddingHorizontal: 0,
    paddingBottom: 8,
    paddingTop: 2,
    minHeight: 0,
    width: '100%',
  },
  leftCol: {
    width: '40%',
    maxWidth: 520,
    minWidth: 280,
    flexShrink: 0,
    paddingHorizontal: 12,
    paddingRight: 14,
    borderRightWidth: 1,
    borderRightColor: 'rgba(255,255,255,0.06)',
    minHeight: 0,
    justifyContent: 'flex-start',
  },
  typeRow: {
    marginTop: 6,
    marginBottom: 2,
    maxHeight: 44,
  },
  rightCol: {
    flex: 1,
    minWidth: 0,
    minHeight: 0,
    paddingHorizontal: 12,
    paddingLeft: 14,
  },
  hint: {
    marginTop: 4,
    marginBottom: 4,
    lineHeight: 16,
  },
  kbWrap: {
    marginTop: 2,
  },
  kbRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'flex-start',
  },
  keyCell: {
    minWidth: 28,
    flexGrow: 1,
    maxWidth: 40,
    aspectRatio: 1,
    margin: 2,
    borderRadius: 8,
    backgroundColor: TV_CARD_BG,
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.06)',
  },
  auxBtn: {
    flex: 1,
    marginHorizontal: 3,
    paddingVertical: 8,
    borderRadius: 10,
    backgroundColor: TV_CARD_BG,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.08)',
  },
  listArea: {
    flex: 1,
    minHeight: 0,
    marginTop: 4,
  },
  sectionTitle: {
    marginBottom: 8,
    fontWeight: '600',
  },
  discScroll: {
    marginBottom: 12,
    maxHeight: 52,
  },
  discRow: {
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'nowrap',
    paddingRight: 8,
  },
  discChip: {
    marginRight: 8,
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: 16,
    backgroundColor: TV_CARD_BG,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.08)',
  },
  tipHidden: {
    position: 'absolute',
    width: 1,
    height: 1,
    opacity: 0,
    overflow: 'hidden',
  },
})
