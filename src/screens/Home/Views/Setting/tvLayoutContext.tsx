import { createContext, useContext } from 'react'

/** 设置页 TV 布局：加大区块与行高、便于遥控聚焦 */
const TvSettingLayoutContext = createContext(false)

export const TvSettingLayoutProvider = TvSettingLayoutContext.Provider

export const useTvSettingLayout = () => useContext(TvSettingLayoutContext)
