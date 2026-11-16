# 遇见天气 · AI 开发说明

本文件面向在本仓库继续开发的 AI agent。核心要求:**全站页面间使用统一组件设计——统一顶栏、统一返回/退出组件、统一转场契约,禁止任何页面自写返回、退出、转场、进入动画。**

## 1. 统一转场契约(唯一入口)

- 全站页面进入 / 返回 / 退出 / 转场的唯一入口是 [AppTransitions](app/src/main/kotlin/com/zhisheng/weather/ui/AppTransitions.kt)(internal object):
  - `navSpec(prefs, systemMotion)` → 生成库 `MochiNavSpec`,由「设置 → 外观与设备 → 应用转场与侧推」驱动;`mochiNavSpecFor` 只是薄委托。
  - `pushFrom(style, dest, cityKey)` → push / setTopSource 时交给库的来源 key:「打开应用式」优先用真实登记 key,没有来源卡片的页面兜底成 `CORNER_KEY`("app.corner")走角落容器变换;其余样式返回 null 走库侧推。
  - `contentTransform(motionEnabled, style, swipeBack)` → 非库导航的整页 AnimatedContent(横竖屏待机、首次定位引导)专用,弹簧与库导航同一条,禁止再自拼 tween 转场。
- 标准实现范例:**「设置 → 生活指数数据」这对页面**(settings.main 的 `MochiSettingsNavRow` 行 + settings.lifeIndex 页,见 [MochiSettingsScreen.kt](app/src/main/kotlin/com/zhisheng/weather/ui/MochiSettingsScreen.kt))。新页面一律照这对页面接入。
- 应用转场默认样式为 **打开应用式(APP)**;可在「设置 → 应用转场与侧推」修改(交叉淡变 / 侧推 / 景深 / 打开应用式 / 无动画,以及预测性返回、侧推返回、下层页缩放、返回卡片高亮)。

## 2. 打开应用式与角落容器变换(Container Transform)

- 页面从按下的那张来源卡片长成整页,返回缩回卡片:来源用 `Modifier.mochiNavSource(key, cornerRadius, color)` 登记,**cornerRadius 与 color 必须和真实组件一致,key 必须与 `push(from=)` / `setTopSource` 传的完全相同**(含城市后缀 `:locationKey`)。
- **上一级页面没有可返回的卡片**(搜索页、城市管理页,或卡片被滚走 / 销毁):传 `AppTransitions.CORNER_KEY`,库(`cornerWhenNoSource` 开关,见 [NavGeometry.kt](mochi-compose-main/mochi-compose/src/main/java/ui/feice/mochi/navigation/NavGeometry.kt) 与 [MochiNavHost.kt](mochi-compose-main/mochi-compose/src/main/java/ui/feice/mochi/navigation/MochiNavHost.kt))把整页向角落小卡片插值——进入从左上角展开;返回按发起侧缩回:右边缘手势 → 右上角,返回按钮 / 左边缘手势 / 应用内右滑 → 左上角。
- 转场期间需要冻结首页重排 / 不间断动画时读 `nav.isTransitioning`(参考 MainActivity 的 `homeSettled`)。

## 3. 统一顶栏与统一返回组件

- 二级页外壳只用这三件(见 [MochiPageChrome.kt](app/src/main/kotlin/com/zhisheng/weather/ui/MochiPageChrome.kt)):
  - `MochiPageFrame`(懒加载列表正文)、`MochiColumnPageFrame`(整块内容正文,地图/图表页)、`MochiSettingsScaffold`(设置链路,带共享「保存」按钮)。
  - 顶栏返回一律是库 `ui.feice.mochi.navigation.MochiBackButton(onClick = onBack)`。
- **禁止**:Material `IconButton + Icons.AutoMirrored.Filled.ArrowBack` 之类自写返回按钮;`FeaturePageHeader`、`ProviderAccessScreen`、`WeatherSourceScreen`(组合函数)等遗留已删除,不要复活。
- 悬浮在内容上的工具条 / 底栏按库文档需要时标 `mochiNavOccluder()`。

## 4. 导航接入规则(硬性)

- 应用级导航与设置页内部导航都是库 `MochiNavHost`([MainActivity.kt](app/src/main/kotlin/com/zhisheng/weather/MainActivity.kt)、MochiSettingsScreen)。`screen` 状态是唯一事实,单向同步进 `rememberMochiNavigator`;库退回根再写回。
- 不得混用 Material 3 的 `SharedTransitionLayout` 做页面转场;`mochiSharedElement` 已废弃且无效果,不得使用。
- 系统返回键、预测性返回、应用内右滑、左上角返回按钮必须共用同一返回语义(`returnHome()` / `nav.pop()`),关闭动画由库按当前「应用转场」设置执行,不在各入口单独处理。
- 页面内部的**元素级动画**(AnimatedVisibility 详情展开、提示条淡入淡出等)不受本契约约束,可以保留。

## 5. 关键文件索引

| 职责 | 文件 |
| --- | --- |
| 统一转场入口 / 角落哨兵 key | app/src/main/kotlin/com/zhisheng/weather/ui/AppTransitions.kt |
| AppScreen / 路由 / 来源 key / 样式枚举 | app/src/main/kotlin/com/zhisheng/weather/ui/ScreenTransition.kt |
| 转场全局契约与偏好快照 | app/src/main/kotlin/com/zhisheng/weather/ui/MochiEffectPrefs.kt |
| 统一顶栏 / 页面外壳 / 返回组件 | app/src/main/kotlin/com/zhisheng/weather/ui/MochiPageChrome.kt |
| 应用级导航宿主 | app/src/main/kotlin/com/zhisheng/weather/MainActivity.kt |
| 设置页导航与标准范例 | app/src/main/kotlin/com/zhisheng/weather/ui/MochiSettingsScreen.kt |
| 库导航几何(角落变换) | mochi-compose-main/mochi-compose/src/main/java/ui/feice/mochi/navigation/NavGeometry.kt |

修改组件库源码前先读 [mochi-compose-main/AGENTS.md](mochi-compose-main/AGENTS.md);库行为变更保持默认关闭的可选参数风格,避免影响其它 MochiNavHost 用法。

## 6. 验证

- 编译:`.\gradlew :app:compileDebugKotlin :mochi-compose:compileDebugKotlin`
- 测试:`.\gradlew :app:testDebugUnitTest`(CI 会跑同一套,退出码非 0 即失败)
- 改动转场后至少人工验证:打开应用式(有卡 / 无卡角落变换)、侧推、无动画三档下的进入与返回。
