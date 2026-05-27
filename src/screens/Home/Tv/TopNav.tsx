import { memo } from 'react'
import { View } from 'react-native'
import TvPressable from '@/components/tv/TvPressable'
import { useNavActiveId } from '@/store/common/hook'
import { Icon } from '@/components/common/Icon'
import { createStyle } from '@/utils/tools'
import { NAV_MENUS } from '@/config/constant'
import type { InitState } from '@/store/common/state'
import { setNavActiveId } from '@/core/common'
import { useI18n } from '@/lang'
import Text from '@/components/common/Text'
import {
  TV_BG_PANEL,
  TV_NAV_ACTIVE_BG,
  TV_NAV_ACTIVE_FG,
  TV_NAV_INACTIVE_BG,
  TV_TEXT_MUTED,
} from '@/config/tvStyles'

const styles = createStyle({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 10,
    paddingHorizontal: 14,
    minHeight: 60,
  },
  brand: {
    flexDirection: 'row',
    alignItems: 'center',
    marginRight: 10,
  },
  brandText: {
    marginLeft: 6,
    fontWeight: '600',
  },
  searchPill: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 20,
    marginRight: 10,
  },
  tabRow: {
    flex: 1,
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'center',
    alignContent: 'center',
    paddingRight: 8,
  },
  pill: {
    paddingHorizontal: 16,
    paddingVertical: 9,
    borderRadius: 20,
    marginRight: 8,
  },
  pillLabel: {
    fontWeight: '500',
  },
  tail: {
    flexDirection: 'row',
    alignItems: 'center',
  },
})

type IdType = InitState['navActiveId']

const TabPill = ({ id, label, active, onFocusSwitch, tvPreferredFocus }: {
  id: IdType
  label: string
  active: boolean
  onFocusSwitch: (id: IdType) => void
  tvPreferredFocus?: boolean
}) => {
  return (
    <TvPressable
      tvPreferredFocus={tvPreferredFocus}
      style={{
        ...styles.pill,
        backgroundColor: active ? TV_NAV_ACTIVE_BG : TV_NAV_INACTIVE_BG,
        minHeight: 44,
        justifyContent: 'center',
      }}
      onFocus={() => { onFocusSwitch(id) }}
      onPress={() => { onFocusSwitch(id) }}
    >
      <Text
        style={[styles.pillLabel, active && { fontWeight: '700' }]}
        size={14}
        color={active ? TV_NAV_ACTIVE_FG : 'rgba(255,255,255,0.65)'}
      >{label}
      </Text>
    </TvPressable>
  )
}

export default memo(() => {
  const t = useI18n()
  const activeId = useNavActiveId()

  const handlePress = (id: IdType) => {
    setNavActiveId(id)
  }

  return (
    <View style={{ ...styles.container, backgroundColor: TV_BG_PANEL }}>
      <View style={styles.brand}>
        <Icon name="music_time" color="#4ade80" size={22} />
        <Text style={styles.brandText} size={15} color="#fff">客厅音乐</Text>
      </View>
      <View style={styles.tabRow}>
        {NAV_MENUS.filter(m => m.id !== 'nav_search' && m.id !== 'nav_setting').map(menu => (
          <TabPill
            key={menu.id}
            id={menu.id}
            label={t(menu.id)}
            active={activeId === menu.id}
            onFocusSwitch={handlePress}
            tvPreferredFocus={activeId === menu.id && menu.id === 'nav_recommend'}
          />
        ))}
      </View>
      <TvPressable
        style={{ ...styles.searchPill, backgroundColor: TV_NAV_INACTIVE_BG, minHeight: 44, justifyContent: 'center' }}
        onPress={() => { handlePress('nav_search') }}
      >
        <Icon name="search-2" size={16} color={TV_TEXT_MUTED} />
        <Text style={{ marginLeft: 6 }} size={13} color={TV_TEXT_MUTED}>{t('nav_search')}</Text>
      </TvPressable>
      <View style={styles.tail}>
        <TvPressable style={{ padding: 12, minWidth: 48, minHeight: 48, justifyContent: 'center', alignItems: 'center' }} onPress={() => { handlePress('nav_setting') }}>
          <Icon name="setting" size={22} color="rgba(255,255,255,0.85)" />
        </TvPressable>
      </View>
    </View>
  )
})
