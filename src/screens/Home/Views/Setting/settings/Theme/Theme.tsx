import { memo, useCallback, useEffect, useMemo, useState } from 'react'
import { View, type ImageSourcePropType } from 'react-native'
import TvPressable from '@/components/tv/TvPressable'
import { IS_TV_APP } from '@/config/tv'
import { setTheme } from '@/core/theme'
import { useI18n } from '@/lang'
import { useSettingValue } from '@/store/setting/hook'
import { useTheme } from '@/store/theme/hook'

import SubTitle from '../../components/SubTitle'
import { BG_IMAGES, getAllThemes, type LocalTheme } from '@/theme/themes'
import Text from '@/components/common/Text'
import { createStyle } from '@/utils/tools'
import { scaleSizeH } from '@/utils/pixelRatio'
import { Icon } from '@/components/common/Icon'
import ImageBackground from '@/components/common/ImageBackground'

const ITEM_HEIGHT = IS_TV_APP ? 78 : 62
const COLOR_ITEM_HEIGHT = IS_TV_APP ? 44 : 36
const IMAGE_HEIGHT = IS_TV_APP ? 34 : 29

const useActive = (id: string) => {
  const activeThemeId = useSettingValue('theme.id')
  const isActive = useMemo(() => activeThemeId == id, [activeThemeId, id])
  return isActive
}

const ThemeItem = ({ id, name, color, image, setTheme, showAll }: {
  id: string
  name: string
  color: string
  showAll: boolean
  image?: ImageSourcePropType
  setTheme: (id: string) => void
}) => {
  const theme = useTheme()
  const isActive = useActive(id)

  const colorBlock = (
    <View style={{ ...styles.colorContent, width: scaleSizeH(COLOR_ITEM_HEIGHT), borderColor: isActive ? color : 'transparent' }}>
      {
        image
          ? <ImageBackground style={{ ...styles.imageContent, width: scaleSizeH(IMAGE_HEIGHT), backgroundColor: color }}
              imageStyle={{ borderRadius: 4 }}
              source={image} />
          : <View style={{ ...styles.imageContent, width: scaleSizeH(IMAGE_HEIGHT), backgroundColor: color }}></View>
        }
    </View>
  )
  const label = (
    <Text style={styles.name} size={IS_TV_APP ? 13 : 12} color={isActive ? color : theme['c-font']} numberOfLines={1}>{name}</Text>
  )

  if (!(showAll || isActive)) return null

  return (
    <TvPressable style={{ ...styles.item, width: scaleSizeH(ITEM_HEIGHT) }} onPress={() => { setTheme(id) }}>
      {colorBlock}
      {label}
    </TvPressable>
  )
}

const MoreBtn = ({ showAll, setShowAll }: {
  showAll: boolean
  setShowAll: (showAll: boolean) => void
}) => {
  const theme = useTheme()
  const t = useI18n()

  if (showAll) return null

  return (
    <TvPressable style={IS_TV_APP ? styles.moreBtnTv : styles.moreBtn} onPress={() => { setShowAll(!showAll) }}>
      <Text size={IS_TV_APP ? 15 : 14} color={theme['c-primary-font']} numberOfLines={1}>{t('setting_basic_theme_more_btn_show')}</Text>
      <Icon name="chevron-right" size={IS_TV_APP ? 16 : 12} color={theme['c-primary-font']} />
    </TvPressable>
  )
}

interface ThemeInfo {
  themes: Readonly<LocalTheme[]>
  userThemes: LX.Theme[]
  dataPath: string
}
const initInfo: ThemeInfo = { themes: [], userThemes: [], dataPath: '' }
export default memo(() => {
  const [showAll, setShowAll] = useState(false)
  const t = useI18n()
  const [themeInfo, setThemeInfo] = useState(initInfo)
  const setThemeId = useCallback((id: string) => {
    requestAnimationFrame(() => {
      setTheme(id)
    })
  }, [])

  useEffect(() => {
    void getAllThemes().then(setThemeInfo)
  }, [])

  return (
    <SubTitle title={t('setting_basic_theme')}>
      <View style={styles.list}>
        {
          themeInfo.themes.map(({ id, config }) => {
            return <ThemeItem
              key={id}
              color={config.themeColors['c-theme']}
              image={config.extInfo['bg-image'] ? BG_IMAGES[config.extInfo['bg-image']] : undefined}
              showAll={showAll}
              id={id}
              name={t(`theme_${id}`)}
              setTheme={setThemeId} />
          })
        }
        {
          themeInfo.userThemes.map(({ id, name, config }) => {
            return <ThemeItem
              key={id}
              color={config.themeColors['c-theme']}
              // image={undefined}
              showAll={showAll}
              id={id}
              name={name}
              setTheme={setThemeId} />
          })
        }
        <MoreBtn showAll={showAll} setShowAll={setShowAll} />
      </View>
    </SubTitle>
  )
})

const styles = createStyle({
  list: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
    marginTop: 5,
  },
  item: {
    // marginRight: 15,
    alignItems: 'center',
    // marginTop: 5,
    // backgroundColor: 'rgba(0,0,0,0.2)',
  },
  colorContent: {
    height: COLOR_ITEM_HEIGHT,
    borderRadius: 4,
    borderWidth: 1.6,
    alignItems: 'center',
    justifyContent: 'center',
    // backgroundColor: 'rgba(0,0,0,0.2)',
  },
  imageContent: {
    height: IMAGE_HEIGHT,
    borderRadius: 4,
    // elevation: 1,
  },
  name: {
    marginTop: 2,
  },
  moreBtn: {
    marginLeft: 10,
    flexDirection: 'row',
    alignItems: 'center',
    // justifyContent: 'center',
    gap: 8,
  },
  moreBtnTv: {
    marginLeft: 10,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingVertical: 10,
    paddingHorizontal: 12,
    minHeight: 44,
    borderRadius: 10,
  },
})
