# BigBarrel-MixinFolia

[下载 BigBarrel 0.2.0](https://github.com/Rivertao1/BigBarrel-MixinFolia/releases/tag/v0.2.0) · 插件 JAR 文件名为 `BigBarrel-0.2.0.jar`。

面向支持启动时 Mixin 注入的 Folia 分支（Minecraft 26.3、Java 25）的独立大木桶插件。使用该版本的 NMS 容器接口与 Irisclip/Leaves Mixin 加载机制，不声明兼容普通 Paper/Folia 或其他核心版本。

将 `build/BigBarrel-0.2.0.jar` 放入服务器 `plugins` 目录后重启，替换旧版本 JAR，勿同时保留两个版本。不支持热重载。无需客户端模组、命令或游戏内配置。两个木桶底对底放置，右键任意一半打开 54 格库存；桶口朝负方向（西、下、北）的一半占前 27 格。潜行持物仍可放置方块。

**必须通过启动参数启用 Mixin。只把插件放入 `plugins` 目录无法使用 0.2.0。** 停服后修改启动脚本或面板的 JVM 参数，再完整启动服务器。

在服务器目录执行以下命令（Linux、Windows 终端均可使用这一行）：

```sh
java -Dirisclip.enable.mixin=true "-Dpaperclip.extraClasspathUrls=server.jar" -jar server.jar nogui
```

两个参数都必须位于 `-jar` 前：

| 参数 | 用途 |
| --- | --- |
| `-Dirisclip.enable.mixin=true` | 开启核心内置的 Mixin 加载器 |
| `-Dpaperclip.extraClasspathUrls=核心文件名.jar` | 将启动器内的 Mixin 回调类暴露给游戏类加载器；此核心构建需要该参数 |

如果重命名核心文件，必须同时修改 `extraClasspathUrls` 和 `-jar` 后的文件名。相对路径以服务器工作目录为准；路径包含空格时保留引号。面板应把两个 `-D` 参数放到 **JVM 参数** 中，不能放在 `nogui` 后。原有内存、GC 等 JVM 参数可以保留。

启动后确认日志显示 `MixinURLClassLoader` 和 `Enabling BigBarrel v0.2.0`，且没有 `Missing comparator mixins` 或 Mixin 注入错误。插件通过 `leaves-plugin.json` 注册 Mixin，配置明确选择 Irisclip 使用的 PREINIT 阶段。注入未生效时插件会拒绝启用。安装、升级、移除插件或修改参数后都必须完整重启，不能使用 `/reload` 或插件热加载工具。核心 JAR 本身不做修改。

物品直接存储于两个原版木桶，不使用独立数据库或关闭界面时的库存回写。拆掉一半沿用原版掉落逻辑。移除插件后，两半仍是普通木桶。漏斗通过库存查找事件访问完整的 54 格库存；不修改其他插件已替换的库存。无法确认两块均在当前区域线程且已加载时，不进行合并。

## 构建

需要 JDK 25、Bash 4+ 和自行准备的目标核心及其展开依赖。Windows 可在 WSL 中构建；只安装插件的管理员无需构建环境。本仓库不提供闭源核心、API JAR 或服务器依赖文件。

```sh
BOOTSTRAP_JAR=/path/to/server/server.jar SERVER_RUNTIME_JAR=/path/to/runtime.jar SERVER_API_JAR=/path/to/api.jar bash build.sh /path/to/server
```

使用目标服的启动器、展开后的运行时 JAR、API JAR 和 `libraries` 依赖编译；不打包服务器类或额外下载运行时依赖。首次可在服务器目录执行 `java -Dpaperclip.patchonly=true -jar server.jar` 只展开依赖。Mixin 类按启动器声明支持的 Java 21 字节码编译，服务器和插件主体仍要求 Java 25。

## 验证与当前限制

在目标 Minecraft 26.3 测试服上通过了插件启用、六种朝向配对、两端槽位顺序、真实库存写入、无效朝向拒绝、拆除恢复单桶，以及真实漏斗跨半桶抽取和本半桶满时的输入测试。比较器等待正常游戏刻，验证了两端的空桶 0、半满 8、全满 15、远端库存变化、拆分/重组、旋转以及原版右键切换比较/减法模式。测试后正常停服保存；原始服务器日志不随仓库公开。

- 比较器通过启动时注入读取完整 54 格库存，库存变更通知另一半附近的比较器；配对、拆分和朝向变化也会更新关联比较器。没有定时扫描或强改输出电平。
- 桶口开合通过 Lidded API 保持：最后一名非旁观者关闭时才关闭两半，保留其他插件预先打开的桶口。目标服模拟两名查看者、原版状态复查、最后一人关闭与打开事件取消的集成测试覆盖此行为。
- 已确认真人客户端能打开 54 格界面。拖拽、真人多人操作、打开期间爆炸/卸载和重启后的物品核对尚未进行端到端验证。
- 领地插件必须正确处理交互/库存事件；不承诺所有保护插件都能自动识别两半的权限边界。

集成测试源码在 `src/test`，可用上述构建命令追加 `--integration` 构建额外的测试插件。仅在测试服临时安装 `BigBarrelIntegrationTest.jar`：它启动后会在出生点高空检查空位，临时放置测试方块；比较器测试为区块添加临时加载票据，等待正常游戏刻执行。成功或断言失败后恢复方块、移除票据。应等到日志出现 `BIGBARREL_TEST_PASS` 或失败结果后再停服；运行后移除测试 JAR，正常使用仅安装 BigBarrel。

## 公开仓库与发布

- 仓库提交：`src/`（源码、插件元数据和集成测试源码）、`build.sh`、`README.md`、`.gitignore`。
- GitHub Release 附件：`BigBarrel-0.2.0.jar` 和对应的 `SHA256SUMS.txt`。管理员只需要插件 JAR，按上面的启动说明安装。
- 不上传：闭源核心或 API JAR、服务器依赖、世界存档、玩家数据、服务器配置、日志、本机启动脚本与备份、`build/`、临时集成测试插件、工具目录及凭据文件。

目前未指定许可证；仓库公开与选择开源许可证是两件事，发布者可在确定授权方式后补充 `LICENSE`。
