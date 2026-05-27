import { IS_TV_APP } from '@/config/tv'

/** FlatList 在电视上裁剪子视图会导致焦点链断裂，需关闭裁剪 */
export const tvFlatListFocusProps = IS_TV_APP
  ? { removeClippedSubviews: false as const }
  : {}

/** 外层 ScrollView 参与焦点竞争时，应自身不可聚焦，把焦点留给子项 */
export const tvScrollParentProps = IS_TV_APP
  ? { focusable: false as const }
  : {}
