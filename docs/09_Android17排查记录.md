# Android17阅读兼容排查

日期：2026-11-01。本轮从dev.8接续，保留此前失败，不把诊断改动当成已修复。

## 现场证据

- b886bc6，[运行41259643961](https://github.com/sk5416/zhigou/actions/runs/41259643961)：构建及API29通过；Android17系统文件用例通过，阅读在C36的ADR文本可访问性断言失败。
- 失败截图`.artifacts/windows37/device-screenshots/smoke-failure.png`可见目标段落及ADR-007表格；窗口采集确认知构窗口active/focused，WebView节点却没有正文子节点。不是仅凭等待超时猜测，也没有将截图可见当作全部验收通过。
- 新增WindowEvidence：显式包含全部交互窗口/普通节点，在失败时保存窗口类型、包、焦点、层级和截图。该配置不删除业务或实际绘制断言。

## dev.9修改与待验证假设

1. API29保留已验证的软件绘制兼容，新系统使用WebView默认绘制方式，避免将旧系统兼容措施推广到所有版本。
2. WebView在AndroidView.onRelease中保存位置、停止加载并销毁；标记释放状态，延迟加载完成回调不再访问已释放实例。
3. App dev.9/code9；课程、旧题修订、Room1和备份格式1不变。真实覆盖安装基线为dev.8提交c1e14a9，不以卸载清库升级。

这些是针对渲染和释放生命周期的修正，不能提前认定就是Android17失败的全部根因。依据[WebView.destroy官方要求](https://developer.android.com/reference/android/webkit/WebView#destroy())和[Compose WebView包装说明](https://developer.android.google.cn/develop/ui/compose/migrate/interoperability-apis/wrap-webview-in-compose?hl=en)，释放应随原生视图退出进行，销毁后不再调用实例。

## 检查方法与边界

Android17上截图有正文、WebView无正文子节点的事实只能证明可访问树与实际绘制不一致，不能认定读屏完整，也不能直接认定教材缺失。原先仅依赖UiAutomator正文节点的检查改为：定位当前章节的可见WebView，等待加载及Chromium视觉状态回调，再使用原生findAllAsync查询实际渲染文档；文字必须存在，查找引起的移动不得超过一个视口。原有正文像素、搜索、题库、已读/书签、自测/错题和深色大字检查保留。JavaScript仍关闭，不通过读取源文件代替界面检查。

初版原生查询在导航尚未结束时读到上一章，C06检查失败；随后要求URL对应目标章节，忽略退出中的WebView，并等待视觉状态。失败保留在运行记录中，不能把更换检查接口写作修复了系统可访问树。TalkBack和真实手机体验仍需独立验证。

## 当前验证

- cb0531c的[运行41261336888](https://github.com/sk5416/zhigou/actions/runs/41261336888)：API29全阶段通过；Android17常规设备方法完成，但宿主升级脚本读取`/data/data`目录属主被拒绝。改为PackageManager查询UID，仍要求升级前后UID一致及真实旧包记录保留；不能将读取权限失败写成升级已通过。命令依据[AOSP包管理命令实现](https://android.googlesource.com/platform/frameworks/base/+/bcb4d3cf6385/services/core/java/com/android/server/pm/PackageManagerShellCommand.java)，核验日期2026-11-01。

- 内容结构/审核匹配/完整课程检查通过：81单元、584题；Python28项通过。
- 本地Debug与Release各21项单测、Lint和应用构建通过；最终测试APK与Lint再次构建通过。日志为`.artifacts/dev9-build.log`、`.artifacts/dev9-release.log`和`.artifacts/dev9-final-build.log`。双系统设备结果待追加。
- 开发包`.artifacts/知构-0.1.0-dev.9.apk`，SHA256为`dba0bd8f76406f6129d1cf74526a291d67aa31bd0832ea53b8623b6f77699484`；开发证书与dev.8相同。核对包名、code9、min29/target35及无INTERNET权限。aapt不支持本机中文文件名，改在英文目录联接核对同散列的原APK；首次路径错误不记检查通过。
- 正式签名、独立备份和实机门槛保持开放，不随本轮渲染修改自动关闭。
