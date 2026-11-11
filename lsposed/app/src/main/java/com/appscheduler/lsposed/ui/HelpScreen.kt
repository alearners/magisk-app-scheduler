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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 帮助页的三个页面 */
enum class HelpPage(val title: String) {
    HOME("帮助"),
    CONFIG_DOC("配置说明"),
    ABOUT("关于本软件")
}

/** 关于页要展示的软件信息，由调用方填，方便截图测试注入固定值 */
data class AboutInfo(
    val appName: String = "AppTimer",
    val versionName: String = "2.2.0",
    val versionCode: Long = 22,
    val packageName: String = "com.appscheduler.lsposed",
    val minSdk: Int = 26,
    val repoUrl: String = "https://github.com/alearners/magisk-app-scheduler",
    val license: String = "GPL-3.0"
)

@Composable
fun HelpScreen(
    page: HelpPage,
    about: AboutInfo,
    onOpen: (HelpPage) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (page) {
        HelpPage.HOME -> HelpHome(onOpen = onOpen, modifier = modifier)
        HelpPage.CONFIG_DOC -> ConfigDocPage(onBack = onBack, modifier = modifier)
        HelpPage.ABOUT -> AboutPage(about = about, onBack = onBack, modifier = modifier)
    }
}

// ============================================================
//  帮助首页
// ============================================================

@Composable
private fun HelpHome(onOpen: (HelpPage) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader("帮助", "使用说明、配置项解释与软件信息")

        SectionCard("怎么用（四步）") {
            HelpParagraph(
                "1. 在「配置」页打开调度总开关，确认锁屏密码填写正确。\n" +
                    "2. 点「新建启动计划」，选择要启动的应用（可多选）。\n" +
                    "3. 设置触发方式（时间点 / 固定间隔 / 每周）与条件（WiFi / 充电 / 熄屏）。\n" +
                    "4. 保存后回到「权限」页确认没有红色项，再点计划上的「立即执行」跑一次试试。"
            )
        }

        HelpEntry(
            title = "配置说明",
            subtitle = "每个开关和数字到底控制什么，建议怎么填",
            onClick = { onOpen(HelpPage.CONFIG_DOC) }
        )

        HelpEntry(
            title = "关于本软件",
            subtitle = "版本、运行环境、开源协议与免责声明",
            onClick = { onOpen(HelpPage.ABOUT) }
        )

        SectionCard("常见问题") {
            HelpParagraph(
                "· 到点了但晚了几分钟：系统把闹钟判成非精确调度，去「权限」页授予「精确闹钟」，或在「配置」页把计时方式改成内部计时器。\n" +
                    "· 锁屏时起不来：先看「权限」页的 LSPosed 模块与「自启动 / 后台弹出界面」两项，再去 LSPosed 管理器确认作用域是「系统界面」。\n" +
                    "· 自动解锁失败：多半是密码填错或机型拦了 input 注入，会转成等你手动解锁（需通知权限）。"
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
        ScreenHeader("配置说明", "「配置」页每一项的含义与建议", onBack = onBack)

        SectionCard("计划：触发方式") {
            HelpParagraph(
                "· 时间点：每天在列出的时间各触发一次，例如 08:00、20:00。\n" +
                    "· 固定间隔：从保存时刻起，每隔 N 分钟触发一次，可设置最多执行多少次。\n" +
                    "· 每周：按选中的星期 + 时间点触发，适合只在工作日跑的计划。"
            )
        }

        SectionCard("计划：条件") {
            HelpParagraph(
                "条件是「同时满足」的关系，一个都不勾就是不限条件、到点就执行。\n" +
                    "· 仅已连 WiFi：断网或走流量时跳过本次，适合签到类应用。\n" +
                    "· 仅充电中：插电才执行，适合耗电或需要长时间前台的应用。\n" +
                    "· 仅屏幕熄灭：屏幕亮着（你在用手机）时跳过，避免突兀地切到目标应用。"
            )
        }

        SectionCard("全局设置") {
            HelpParagraph(
                "锁屏密码：设备的数字锁屏密码，用于自动解锁时注入。只支持纯数字；字母和符号在 input 注入里容易被转义。\n\n" +
                    "等用户手动解锁后再启动：自动解锁失败时不放弃，改为等你解锁后立刻启动应用（需通知权限才能提醒你）。\n\n" +
                    "最长等待秒数（10–1800）：等待手动解锁的上限，超时就放弃本次任务并写日志 / 发通知。填太小（比如 10 秒）容易还没看到提醒就超时。\n\n" +
                    "启动后无操作自动锁屏：应用起来后若你一直没碰屏幕，过一段时间自动熄屏上锁。到点时前台已经不是目标应用（说明你切走了）会自动跳过，不会误锁。\n\n" +
                    "无操作多少秒后锁屏（2–600）：上面的延时。太短来不及看清界面，建议 5–30 秒。\n\n" +
                    "用应用内部计时器计时：\n" +
                    "  · 关闭（系统闹钟）：进程被杀也能到点触发，但可能被省电策略推迟几分钟。\n" +
                    "  · 打开（内部计时器）：常驻前台服务自己数时间，触发更准，但通知栏会一直有一条常驻通知，且服务被杀就停摆。"
            )
        }

        SectionCard("配置备份") {
            HelpParagraph(
                "导出会把全部计划与全局设置写成一个带时间戳的 JSON 文件，换机或重装系统后导入即可恢复。\n" +
                    "导入是覆盖式的：会先弹确认框，确认后当前全部计划与全局设置（含锁屏密码）都会被替换。\n" +
                    "注意：文件里含明文锁屏密码，不要随手发给别人。"
            )
        }

        SectionCard("权限清单") {
            HelpParagraph(
                "通知：手动解锁提醒、失败通知。\n" +
                    "精确闹钟：保证到点准时唤醒，否则可能延迟几分钟。\n" +
                    "省电白名单：避免闹钟被电池优化推迟或丢弃。\n" +
                    "root：解锁、强制停止与启动应用、加入白名单都依赖它。\n" +
                    "读取应用列表：用于「选择应用」列表。\n" +
                    "LSPosed 作用域：必须是「系统界面」，用于真正收起锁屏。\n" +
                    "自启动 / 后台弹出界面：HyperOS、MIUI 需要手动允许，否则锁屏时拉不起界面。"
            )
        }
    }
}

// ============================================================
//  关于本软件
// ============================================================

@Composable
private fun AboutPage(about: AboutInfo, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader("关于本软件", "版本、运行环境、协议与免责声明", onBack = onBack)

        SectionCard("软件信息") {
            InfoLine("名称", about.appName + "（LSPosed 版）")
            InfoLine("版本", "${about.versionName}（versionCode ${about.versionCode}）")
            InfoLine("包名", about.packageName)
            InfoLine("最低系统", "Android ${androidName(about.minSdk)} 及以上（API ${about.minSdk}）")
            InfoLine("运行环境", "已 root（Magisk / KernelSU）+ LSPosed")
            InfoLine("模块作用域", "系统界面（SystemUI）")
        }

        SectionCard("它是做什么的") {
            HelpParagraph(
                "按你设定的计划，在指定时间自动把设备唤到可用状态，然后启动指定的应用：\n" +
                    "· 唤醒屏幕、上滑、注入锁屏密码；密码方式失败时，可收起锁屏或等你手动解锁。\n" +
                    "· 强制停止目标应用后重新启动，保证是冷启动而不是残留在后台的旧进程。\n" +
                    "· 支持多应用、多计划并发，每个计划可单独设置触发方式与条件。\n" +
                    "· 可选：启动完成后若一直无操作，自动熄屏上锁，回到你原本的锁屏状态。\n\n" +
                    "收起锁屏由 LSPosed 钩子在 SystemUI 内完成，其余动作通过 root 执行。"
            )
        }

        SectionCard("开源与协议") {
            InfoLine("协议", about.license)
            Text(
                about.repoUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            HelpParagraph("源码公开，欢迎自查与自行编译；未经审核的第三方修改版请谨慎使用。")
        }

        SectionCard("隐私") {
            HelpParagraph(
                "计划、全局设置与运行日志只保存在本机应用私有目录，不上传任何服务器。\n" +
                    "导出的配置文件包含明文锁屏密码，保存在你选择的目录里，请自行保管。"
            )
        }

        SectionCard("免责声明（重要）") {
            SelectionContainer {
                Text(
                    "本软件为个人自用的自动化工具，没有任何形式的担保。刷入模块、授予 root、把锁屏密码交给自动化流程本身都存在风险。\n\n" +
                        "使用即表示你理解并同意：由你自行承担使用本软件的全部责任与后果，包括但不限于设备异常、数据丢失、账号被登出、目标应用被判定异常、违反第三方服务条款等。\n\n" +
                        "请只在你拥有完全控制权的设备上使用；作者不提供任何形式的售后承诺。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** API 等级 → 系统版本号，避免在界面里写「API 26」这种看不懂的编号 */
private fun androidName(api: Int): String = when {
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
private fun InfoLine(label: String, value: String) {    Row(modifier = Modifier.fillMaxWidth()) {
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

@Composable
private fun HelpParagraph(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
