import { useEffect, useImperativeHandle, forwardRef, useMemo, useRef, useState, type Ref } from 'react'
import { View, Animated } from 'react-native'
import TvPressable from '@/components/tv/TvPressable'
import { tvScrollParentProps } from '@/utils/tvFocusProps'
import { useWindowSize } from '@/utils/hooks'
import { IS_TV_APP } from '@/config/tv'

import Modal, { type ModalType } from './Modal'

import { createStyle } from '@/utils/tools'
import { useTheme } from '@/store/theme/hook'
import Text from './Text'
import { scaleSizeH, scaleSizeW } from '@/utils/pixelRatio'
import { TV_ACCENT_GREEN, TV_ACCENT_GREEN_DIM, TV_PANEL_BG } from '@/config/tvStyles'

const menuItemHeight = IS_TV_APP ? scaleSizeH(52) : scaleSizeH(40)
const menuItemWidth = IS_TV_APP ? scaleSizeW(160) : scaleSizeW(100)

export interface Position { w: number, h: number, x: number, y: number, menuWidth?: number, menuHeight?: number }
export interface MenuSize { width?: number, height?: number }
export type Menus = Readonly<Array<{ action: string, label: string, disabled?: boolean }>>

const styles = createStyle({
  mask: {
    position: 'absolute',
    top: 0,
    bottom: 0,
    left: 0,
    right: 0,
    opacity: 0,
    backgroundColor: 'black',
  },
  menu: {
    position: 'absolute',
    // borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'lightgray',
    borderRadius: IS_TV_APP ? 12 : 2,
    backgroundColor: 'white',
    elevation: 3,
  },
  menuItem: {
    paddingLeft: IS_TV_APP ? 14 : 10,
    paddingRight: IS_TV_APP ? 14 : 10,
    marginHorizontal: IS_TV_APP ? 6 : 0,
    marginVertical: IS_TV_APP ? 3 : 0,
    borderRadius: IS_TV_APP ? 10 : 0,
    // height: menuItemHeight,
    // width: menuItemWidth,
    // alignItems: 'center',
    justifyContent: 'center',
    // backgroundColor: '#ccc',
  },
  // menuText: {
  //   // textAlign: 'center',
  //   fontSize: 14,
  // },
})

interface Props<M extends Menus = Menus> {
  menus: Readonly<M>
  onPress?: (menu: M[number]) => void
  buttonPosition: Position
  menuSize: MenuSize
  onHide: () => void
  width?: number
  height?: number
  fontSize?: number
  center?: boolean
  activeId?: M[number]['action'] | null
}

const Menu = ({
  buttonPosition,
  menuSize,
  menus,
  width,
  height,
  onPress = () => {},
  onHide,
  activeId,
  fontSize = IS_TV_APP ? 16 : 15,
  center = false,
}: Props) => {
  const theme = useTheme()
  const windowSize = useWindowSize()
  const [focusedAction, setFocusedAction] = useState<string | null>(null)
  // const fadeAnim = useRef(new Animated.Value(0)).current
  // console.log(buttonPosition)

  const menuItemStyle = useMemo(() => {
    return {
      width: width ?? menuSize.width ?? menuItemWidth,
      height: height ?? menuSize.height ?? menuItemHeight,
    }
  }, [menuSize, width, height])

  const menuStyle = useMemo(() => {
    let menuHeight = menus.length * menuItemStyle.height
    const topHeight = buttonPosition.y - 20
    const bottomHeight = windowSize.height - buttonPosition.y - buttonPosition.h - 20
    if (menuHeight > topHeight && menuHeight > bottomHeight) menuHeight = Math.max(topHeight, bottomHeight)

    const menuWidth = menuItemStyle.width
    const bottomSpace = windowSize.height - buttonPosition.y - buttonPosition.h - 20
    const rightSpace = windowSize.width - buttonPosition.x - menuWidth
    const showInBottom = bottomSpace >= menuHeight
    const showInRight = rightSpace >= menuWidth
    const frameStyle: {
      height: number
      width: number
      top: number
      left?: number
      right?: number
    } = {
      height: menuHeight,
      top: showInBottom ? buttonPosition.y + buttonPosition.h : buttonPosition.y - menuHeight,
      width: menuWidth,
    }
    if (showInRight) {
      frameStyle.left = buttonPosition.x
    } else {
      frameStyle.right = windowSize.width - buttonPosition.x - buttonPosition.w
    }
    return frameStyle
  }, [menus.length, menuItemStyle, buttonPosition, windowSize])

  const preferredAction = useMemo(() => {
    if (activeId != null) return activeId
    return menus.find(menu => !menu.disabled)?.action ?? null
  }, [activeId, menus])

  useEffect(() => {
    setFocusedAction(preferredAction)
  }, [preferredAction, menus])

  const menuPress = (menu: Menus[number]) => {
    // if (menu.disabled) return
    onPress(menu)
    onHide()
  }

  // console.log('render menu')
  // console.log(activeId)
  // console.log(menuStyle)
  // console.log(menuItemStyle)
  return (
    <View style={{ ...styles.menu, ...menuStyle, backgroundColor: IS_TV_APP ? TV_PANEL_BG : theme['c-content-background'] }} onStartShouldSetResponder={() => true}>
      <Animated.ScrollView keyboardShouldPersistTaps={'always'} {...tvScrollParentProps}>
        {
          menus.map((menu) => {
            const isFocused = menu.action == focusedAction
            if (menu.disabled) {
              return (
                <View
                  key={menu.action}
                  style={{ ...styles.menuItem, width: menuItemStyle.width, height: menuItemStyle.height, opacity: 0.4 }}
                >
                  <Text style={{ textAlign: center ? 'center' : 'left' }} size={fontSize} numberOfLines={1}>{menu.label}</Text>
                </View>
              )
            }
            return (
              <TvPressable
                key={menu.action}
                tvPreferredFocus={menu.action == preferredAction}
                tvFocusVariant="none"
                style={{
                  ...styles.menuItem,
                  width: menuItemStyle.width - (IS_TV_APP ? 12 : 0),
                  height: menuItemStyle.height - (IS_TV_APP ? 6 : 0),
                  flexDirection: 'row',
                  alignItems: 'center',
                  justifyContent: center ? 'center' : 'flex-start',
                  paddingLeft: IS_TV_APP ? 12 : styles.menuItem.paddingLeft,
                  backgroundColor: isFocused ? 'rgba(255,255,255,0.34)' : 'rgba(255,255,255,0.03)',
                  borderWidth: isFocused && IS_TV_APP ? 1 : 0,
                  borderColor: isFocused ? TV_ACCENT_GREEN_DIM : 'transparent',
                  transform: [{ scale: isFocused && IS_TV_APP ? 1.03 : 1 }],
                }}
                onTvFocusChange={(focused) => {
                  if (focused) setFocusedAction(menu.action)
                }}
                onPress={() => { menuPress(menu) }}
              >
                {isFocused && IS_TV_APP ? (
                  <View style={{ width: 4, height: 20, borderRadius: 3, backgroundColor: TV_ACCENT_GREEN, marginRight: 10 }} />
                ) : null}
                <Text
                  style={{ textAlign: center ? 'center' : 'left' }}
                  color={isFocused ? '#ffffff' : 'rgba(255,255,255,0.78)'}
                  size={fontSize}
                  numberOfLines={1}
                >
                  {menu.label}
                </Text>
              </TvPressable>
            )
          })
        }
      </Animated.ScrollView>
    </View>
  )
}

export interface MenuProps<M extends Menus = Menus> {
  menus: M
  onPress: (menu: M[number]) => void
  onHide?: () => void
  width?: number
  height?: number
  fontSize?: number
  center?: boolean
  activeId?: M[number]['action'] | null
}

export interface MenuType {
  show: (position: Position, menuSize?: MenuSize) => void
  hide: () => void
}

const Component = <M extends Menus>({ menus, width, height, activeId, onHide, onPress, fontSize, center }: MenuProps<M>, ref: Ref<MenuType>) => {
  // console.log(visible)
  const modalRef = useRef<ModalType>(null)
  const [position, setPosition] = useState<Position>({ w: 0, h: 0, x: 0, y: 0 })
  const [menuSize, setMenuSize] = useState<MenuSize>({ })
  const hide = () => {
    modalRef.current?.setVisible(false)
  }
  useImperativeHandle(ref, () => ({
    show(newPosition, menuSize) {
      setPosition(newPosition)
      if (menuSize) setMenuSize(menuSize)
      modalRef.current?.setVisible(true)
    },
    hide() {
      hide()
    },
  }))

  return (
    <Modal onHide={onHide} ref={modalRef}>
      <Menu menus={menus} width={width} height={height} activeId={activeId} buttonPosition={position} menuSize={menuSize} onPress={onPress} onHide={hide} fontSize={fontSize} center={center} />
    </Modal>
  )
}

// export default forwardRef(Component) as ForwardRefFn<MenuType>
export default forwardRef(Component) as <M extends Menus>(p: MenuProps<M> & { ref?: Ref<MenuType> }) => JSX.Element | null
