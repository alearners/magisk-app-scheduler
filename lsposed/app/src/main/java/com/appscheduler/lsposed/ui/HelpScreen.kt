package com.appscheduler.lsposed.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.appscheduler.lsposed.Config

/** 「更多」页的各个页面 */
enum class HelpPage(val title: String) {
    HOME("更多"),
    TERMS("使用说明"),
    LAUNCH_DOC("启动方式"),
    CONFIG_DOC("配置说明"),
    THEME("主题设置"),
    NOTIFICATION("通知位置"),
    ABOUT("关于本软件")
}

/** 关于页要展示的软件信息，由调用方填，方便截图测试注入固定值 */
data class AboutInfo(
    val appName: String = "AppTimer",
    val versionName: String = "0.0.004",
    val versionCode: Long = 4,
    val packageName: String = "com.appscheduler.lsposed",
    val minSdk: Int = 26,
    val lsposedVersion: String = "未检测到",
    val repoUrl: String = "https://github.com/alearners/magisk-app-scheduler",
    val license: String = "GPL-3.0"
)

@Composable
fun HelpScreen(
    page: HelpPage,
    about: AboutInfo,
    onOpen: (HelpPage) -> Unit,
    onBack: () -> Unit,
    onExportConfig: () -> Unit = {},
    onImportConfig: () -> Unit = {},
    themeSettings: ThemeSettings = ThemeSettings(),
    onSaveTheme: (ThemeSettings) -> Unit = {},
    snackbarMode: String = Config.DEFAULT_SNACKBAR_MODE,
    onSnackbarModeChange: (String) -> Unit = {},
    onSnackbarReset: () -> Unit = {},
    onPreviewSnackbar: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    when (page) {
        HelpPage.HOME -> HelpHome(
            onOpen = onOpen,
            onExportConfig = onExportConfig,
            onImportConfig = onImportConfig,
            modifier = modifier
        )

        HelpPage.CONFIG_DOC -> ConfigDocPage(onBack = onBack, modifier = modifier)
        HelpPage.TERMS -> TermsPage(onBack = onBack, modifier = modifier)
        HelpPage.LAUNCH_DOC -> LaunchDocPage(onBack = onBack, modifier = modifier)
        HelpPage.THEME -> ThemeSettingsScreen(
            settings = themeSettings,
            onSave = onSaveTheme,
            onBack = onBack
        )
        HelpPage.NOTIFICATION -> NotificationSettingsScreen(
            mode = snackbarMode,
            onModeChange = onSnackbarModeChange,
            onReset = onSnackbarReset,
            onPreview = onPreviewSnackbar,
            onBack = onBack,
            modifier = modifier
        )
        HelpPage.ABOUT -> AboutPage(about = about, onBack = onBack, modifier = modifier)
    }
}

// ============================================================
//  帮助首页
// ============================================================

@Composable
private fun HelpHome(
    onOpen: (HelpPage) -> Unit,
    onExportConfig: () -> Unit,
    onImportConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader("更多", "功能介绍、主题设置、启动方式说明、配置项释义与开源信息")

        SectionCard("上手四步") {
            HelpParagraph(
                "1. 请先在「配置」页启用定时总开关，并填写本机锁屏密码。\n" +
                    "2. 点击「新建启动计划」，选择需在指定时间自动启动的应用，可一次选择多个。\n" +
                    "3. 设定触发时间（每日时间点、固定间隔或指定星期），按需限定充电、WiFi 等条件，并选择启动方式。\n" +
                    "4. 保存后前往「概览」核对各项状态；如提示异常，请按指引处理，随后于计划卡片点击「立即执行」完成一次验证。"
            )
        }

        HelpEntry(
            title = "使用说明",
            subtitle = "说明功能范围、潜在风险，以及禁止使用的情形",
            onClick = { onOpen(HelpPage.TERMS) }
        )

        HelpEntry(
            title = "启动方式",
            subtitle = "四种启动方法的原理、适用场景与可能产生的影响",
            onClick = { onOpen(HelpPage.LAUNCH_DOC) }
        )

        HelpEntry(
            title = "配置说明",
            subtitle = "各配置项的作用与建议填法",
            onClick = { onOpen(HelpPage.CONFIG_DOC) }
        )

        HelpEntry(
            title = "主题设置",
            subtitle = "按钮配色、背景图片、透明度与按时段自动切换",
            onClick = { onOpen(HelpPage.THEME) }
        )

        HelpEntry(
            title = "通知位置",
            subtitle = "提示条固定在顶部、底部，或按住自由拖动",
            onClick = { onOpen(HelpPage.NOTIFICATION) }
        )

        HelpEntry(
            title = "关于本软件",
            subtitle = "版本信息、运行环境、开源协议与免责声明",
            onClick = { onOpen(HelpPage.ABOUT) }
        )

        SectionCard("配置备份") {
            HelpParagraph(
                "可将全部计划与全局设置导出为文件，供换机或重装后恢复；导入为整体替换，执行前将再次确认。\n" +
                    "导出文件内含明文锁屏密码，请妥善保管。"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onExportConfig, modifier = Modifier.weight(1f)) {
                    Text("导出配置")
                }
                OutlinedButton(onClick = onImportConfig, modifier = Modifier.weight(1f)) {
                    Text("导入配置")
                }
            }
        }

        SectionCard("常见问题") {
            HelpParagraph(
                "· 触发时间延后数分钟：多为系统未授予「精确闹钟」，请前往「概览」补授；或在「配置」页改用应用内置计时器。\n" +
                    "· 锁屏状态下无法启动：请先核对「概览」中 LSPosed 与「自启动 / 后台弹出界面」两项，再到 LSPosed 管理器确认作用域为「系统界面」。\n" +
                    "· 自动解锁失败：常见原因为密码填写有误，或机型不允许模拟输入。此时将转为等待手动解锁；请授予通知权限，否则无法收到提醒。\n" +
                    "· 下拉栏「自动旋转屏幕」自行开启：系「模拟点击」所致。将计划改用「直接打开」即不再触发；已开启者可在下拉栏中手动关闭。"
            )
        }
    }
}

@Composable
private fun HelpEntry(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ============================================================
//  配置说明
// ============================================================

@Composable
private fun ConfigDocPage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader("配置说明", "说明计划各项含义与建议填法", onBack = onBack)

        SectionCard("计划：触发方式") {
            HelpParagraph(
                "· 按时间点：每日在指定时间各触发一次，如 08:00、20:00，可添加多个。\n" +
                    "· 按间隔循环：自保存时刻起，每隔固定时长触发一次，可限定总次数。\n" +
                    "· 按星期：选择星期与时间，适用于仅在特定日期执行的计划。"
            )
        }

        SectionCard("计划：执行条件") {
            HelpParagraph(
                "各条件之间为「均须满足」关系；均不勾选时，到点即执行，不作限制。\n" +
                    "· 仅在连接 WiFi 时：断开 WiFi 或使用移动数据时跳过，常用于签到类应用。\n" +
                    "· 仅在充电时：未充电时跳过本次，适用于耗电较高或需长时间运行的应用。\n" +
                    "· 仅在屏幕熄灭时：屏幕点亮时不予执行，以免界面被意外切换。"
            )
        }

        SectionCard("计划：启动方式") {
            HelpParagraph(
                "每份计划需设定两项，均在新建 / 编辑计划页完成，二者相互独立。\n\n" +
                    "第一项：启动方式。默认的「直接打开」最为简洁；「等待界面」「模拟点击」「返回桌面」为备选方案，差异详见「更多 → 启动方式」。\n\n" +
                    "第二项：启动前是否先停止该应用。\n" +
                    "· 开启（先停止）：等同重新进入，界面回到初始状态。\n" +
                    "· 关闭（直接启动）：应用仍在后台时，将回到上次退出的位置，速度较快。\n\n" +
                    "两项可自由组合，例如「直接打开」配合「先停止」，或「模拟点击」配合「直接启动」。"
            )
        }

        SectionCard("全局设置") {
            HelpParagraph(
                "锁屏密码：即本机数字锁屏密码，自动解锁时使用。仅支持数字；字母与符号在模拟输入中易出错。\n\n" +
                    "自动解锁失败时等待手动解锁：自动输入密码未成功时不予放弃，待你解锁后立即启动应用。该功能依赖通知权限，否则无法收到等待提醒。\n\n" +
                    "最长等待时间（10–1800 秒）：等待上限。超时即放弃本次执行，写入日志并发出通知。设置过短（如 10 秒）时，往往尚未察觉提醒即已超时。\n\n" +
                    "应用无人操作时自动锁屏：应用启动后长时间无操作，将自动熄屏并锁定，恢复原锁屏状态。若触发时你正在使用其他界面，将自动跳过，不会误锁。\n\n" +
                    "无操作后锁定（2–600 秒）：即上述「长时间」的具体时长。过短则难以看清内容，建议设为 5–30 秒。\n\n" +
                    "使用应用内置计时器：\n" +
                    "  · 关闭（系统闹钟）：应用被系统清理后仍可按时触发，但省电策略可能导致延后数分钟。\n" +
                    "  · 开启（内置计时器）：由应用自身计时，精度较高；代价是通知栏常驻一条通知，应用被清理后停止计时。"
            )
        }

        SectionCard("概览中的七项核验") {
            HelpParagraph(
                "通知：用于送达等待手动解锁、本次执行未成功等提醒。\n" +
                    "精确闹钟：确保按时触发；未授予时可能延后数分钟。\n" +
                    "省电白名单：避免闹钟被电池优化延后或取消。\n" +
                    "root：解锁、强制停止、启动应用与加入白名单均依赖此项。\n" +
                    "读取应用列表：用于在「应用选择」中列出已安装应用。\n" +
                    "LSPosed 作用域：须设为「系统界面」，方可收起锁屏。\n" +
                    "自启动 / 后台弹出界面：澎湃、MIUI 等系统需手动放行，否则锁屏时界面无法显示。"
            )
        }
    }
}

// ============================================================
//  启动方式说明
// ============================================================

@Composable
private fun LaunchDocPage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader("启动方式", "四种启动方法的原理、代价与适用场景", onBack = onBack)

        SectionCard("结论") {
            HelpParagraph(
                "多数情形使用「直接打开」即可：仅发送一条启动指令，不触碰界面，亦不改动系统设置。\n" +
                    "仅在无法启动时，方需依次尝试「模拟点击」；「等待界面」与「返回桌面」为个别应用的备选方案。\n" +
                    "启动方式与「启动前先停止该应用」为两项独立设置，可自由组合。"
            )
        }

        SectionCard("直接打开（默认）") {
            HelpParagraph(
                "原理：先向系统查询该应用的第一个界面，再直接启动。\n" +
                    "可能情况：个别应用无法解析该界面时将启动失败，日志记录「未找到入口」，此时应改用「模拟点击」。\n" +
                    "日志示例：「已用指定组件打开 com.xxx/.MainActivity」。"
            )
        }

        SectionCard("等待界面") {
            HelpParagraph(
                "原理：同样直接启动，但待界面实际显示后再继续执行。\n" +
                    "代价：每个应用多耗时一至三秒；长期停留在开屏页的应用将等待至超时，本次计为失败。\n" +
                    "适用：启动较慢、需要确认界面确已显示的应用。连续启动多个应用时，耗时将明显增加。\n" +
                    "日志示例：「已等界面出现后打开 com.xxx/.MainActivity」。"
            )
        }

        SectionCard("模拟点击") {
            HelpParagraph(
                "原理：调用系统 monkey，模拟在桌面点击图标。\n" +
                    "代价：该方式会注入触摸与导航事件。经实测，在澎湃、MIUI 上会开启系统「自动旋转屏幕」，亦可能误触界面中的其他控件。\n" +
                    "保留原因：部分应用仅能通过该方式启动，属最后的兼容手段。\n" +
                    "副作用处理：下拉栏中「自动旋转屏幕」开启后，手动点击关闭即可，不影响本应用的其他功能。\n" +
                    "日志示例：「已通过模拟点击打开 com.xxx」。"
            )
        }

        SectionCard("返回桌面后启动") {
            HelpParagraph(
                "原理：先返回桌面，再启动应用，等同从桌面图标点开。\n" +
                    "代价：会打断当前操作，屏幕出现一次闪烁。\n" +
                    "适用：仅适用于必须在桌面环境下才能启动的应用，日常不建议选用。\n" +
                    "日志示例：「已回桌面再打开 com.xxx/.MainActivity」。"
            )
        }

        SectionCard("四种方式对比") {
            HelpParagraph(
                "直接打开：速度最快，不产生额外干扰；无法解析入口时将失败。\n" +
                    "等待界面：多耗时一至三秒，可确认界面确已显示；停留于开屏页将超时。\n" +
                    "模拟点击：兼容性最好；可能误触，在澎湃上会开启自动旋转。\n" +
                    "返回桌面：会打断当前界面并出现桌面闪烁，仅用于特殊应用。"
            )
        }

        SectionCard("其他情形") {
            HelpParagraph(
                "· 单份计划选择多个应用：按顺序依次启动，间隔 0.6 秒，最后一个保留在前台。\n" +
                    "· 中途某个应用启动失败：日志记录原因，其余应用继续尝试；本次整体计为失败并发出通知。\n" +
                    "· 应用已在运行：仍会将其切换至前台，通常不会回到首页；有开屏动画者会出现闪烁。\n" +
                    "· 解锁失败：任何启动方式均不执行，转为等待手动解锁或直接放弃。\n" +
                    "· 系统层面拦截：澎湃、MIUI 未授予「后台弹出界面」时，即便命令执行成功，界面也可能仅在后台出现。"
            )
        }
    }
}

// ============================================================
//  关于本软件
// ============================================================

@Composable
private fun TermsPage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PageTitle(Terms.TITLE, Terms.SUBTITLE, onBack = onBack)
        Spacer(Modifier.height(4.dp))
        Terms.sections.forEach { section ->
            SectionLabel(section.title)
            SesameCard {
                HelpParagraph(section.body, MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun AboutPage(about: AboutInfo, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader("关于本软件", "版本信息、运行环境、开源协议与免责声明", onBack = onBack)

        SectionCard("软件信息") {
            InfoLine("名称", about.appName + "（LSPosed 版）")
            InfoLine("版本", about.versionName)
            InfoLine("包名", about.packageName)
            InfoLine("最低系统", "Android ${androidName(about.minSdk)} 及以上")
            InfoLine("运行环境", "已取得 root（Magisk / KernelSU），LSPosed ${about.lsposedVersion}")
            InfoLine("模块作用域", "系统界面")
        }

        SectionCard("功能说明") {
            HelpParagraph(
                "本应用按既定计划，在指定时间将设备唤醒至可用状态，并启动指定应用：\n" +
                    "· 先点亮屏幕并上滑，再按所填密码尝试解锁；密码方式不可行时，可收起锁屏，或等待手动解锁。\n" +
                    "· 每份计划可自行选择启动方式——直接打开、等待界面、模拟点击、返回桌面，并决定启动前是否先停止该应用。\n" +
                    "· 可同时管理多个应用与多份计划，各自的时间、条件与启动方式相互独立。\n" +
                    "· 可选：应用启动后长时间无操作，将自动熄屏并锁定，恢复原状态。\n\n" +
                    "收起锁屏由 LSPosed 在系统界面进程内完成，其余操作通过 root 执行。"
            )
        }

        SectionCard("开源与协议") {
            InfoLine("协议", about.license)
            Text(
                about.repoUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            HelpParagraph("源代码公开，欢迎查阅与自行编译；对来源不明的第三方修改版本，请审慎使用。")
        }

        SectionCard("隐私") {
            HelpParagraph(
                "计划、设置与日志仅保存在本机应用私有目录，不会上传至任何服务器。\n" +
                    "导出的配置文件内含明文锁屏密码，保存在你所选的位置，请自行妥善保管。"
            )
        }

        SectionCard("免责声明（重要）") {
            SelectionContainer {
                Text(
                    "本应用为个人自用自动化工具，不提供任何形式的担保。刷入模块、授予 root 权限、将锁屏密码交由自动化流程处理，均存在风险。\n\n" +
                        "使用本应用即表示你已理解并接受：由此产生的一切后果由你自行承担，包括设备异常、数据丢失、账号被登出、目标应用被判定为异常、违反第三方服务条款等。\n\n" +
                        "请仅在完全由你掌控的设备上使用；作者不提供任何售后承诺。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** API 等级 → 系统版本号，避免在界面里写「API 26」这种看不懂的编号 */
internal fun androidName(api: Int): String = when {
    api >= 36 -> "16"
    api >= 35 -> "15"
    api >= 34 -> "14"
    api >= 33 -> "13"
    api >= 32 -> "12L"
    api >= 31 -> "12"
    api >= 30 -> "11"
    api >= 29 -> "10"
    api >= 28 -> "9"
    api >= 27 -> "8.1"
    api >= 26 -> "8.0"
    else -> "7.x"
}

@Composable
internal fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            modifier = Modifier.padding(end = 12.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SelectionContainer {
            Text(value, style = MaterialTheme.typography.bodySmall)
        }
    }
}
