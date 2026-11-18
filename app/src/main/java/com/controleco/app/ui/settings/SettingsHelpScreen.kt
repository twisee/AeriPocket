package com.controleco.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.controleco.app.ui.common.EcoIcons
import com.controleco.app.ui.common.HelpOnboarding
import com.controleco.app.ui.common.SettingsNote
import com.controleco.app.ui.common.SettingsPage
import com.controleco.app.ui.common.SettingsSection
import com.controleco.app.ui.theme.EcoType
import com.controleco.app.ui.theme.eco

/** 原生帮助正文与 docs/说明/软件说明.md 同步，保留行内高亮和主题色。 */

/** 帮助正文：比常规行副标大一档，行高放宽，便于长段落阅读 */
private val HelpBody: TextStyle = EcoType.RowSub.copy(fontSize = 10.5.sp, lineHeight = 18.sp)

/** 条目标题：略大于行标题，和正文拉开层级 */
private val HelpTitle: TextStyle = EcoType.RowTitle.copy(fontSize = 14.sp, lineHeight = 19.sp)

/** 正文里的行内标记：`term` 与 **强调** */
private val INLINE_MARK = Regex("""\*\*(.+?)\*\*|`([^`]+)`""")

@Composable
fun SettingsHelpScreen(onBack: () -> Unit) {
    // 只要进过本页就记「看过」：首次使用引导弹窗（HelpIntroDialog）因此不再出现
    val context = LocalContext.current
    LaunchedEffect(Unit) { HelpOnboarding.markViewed(context) }

    SettingsPage(micro = "HELP", title = "帮助", onBack = onBack) {
        SettingsNote("适用于 AeriPocket 1.6.2 · 设置中的功能名称与当前版本一致。")
        SettingsSection(title = "页面操作", aside = "NAVIGATION")
        HelpEntryBlock(entry = HelpEntry(title = "左右滑动切换主页面", icon = EcoIcons.Home, paragraphs = listOf(
            "主页面按首页、流水、概览、我的排列。手指向左拖进入下一页，向右拖返回上一页；首页向右拖只回弹。底部导航也可直接切页，页面原有滚动与筛选状态会保留。",
            "首页不再提供上滑跳转。编辑和设置等子页面保持返回操作，图表内部的横向操作优先响应。",
            "流水顶部显示本月或本年全部花费，扣除退款并包含体验和不计预算的支出；概览中的结余仍按收入减净支出计算。",
        )), tint = MaterialTheme.eco.blue)
        SettingsSection(title = "体验小金库", aside = "EXPERIENCE")
        HelpEntryBlock(entry = HelpEntry(title = "独立额度与跨月累积", icon = EcoIcons.Receipt, paragraphs = listOf(
            "在设置开启`体验小金库`，从月总预算中划入固定额度，余下作为日常预算。沿用`每月首日`，本期只划入一次；未打开 App 的月份也会在下次打开时补齐。修改额度从下期生效，关闭期间暂停划入。",
            "支出默认计入日常预算；在预算选项开启`计入体验预算`后仅扣体验额度，与`不计入预算`互斥。体验支出仍保留在全部消费和结余中，退款返还体验额度；AA 按原有分摊规则计算。关闭功能会保留历史体验标记。",
            "在`我的`轻点预算卡片翻到体验小金库，主金额为累计可用额度，底部左右显示本月新增和已用。翻回预算卡正面后，点铅笔可编辑当前预算。清除累计额度只将当前可用归零，不删除流水、不重发当月额度。",
        )), tint = MaterialTheme.eco.blue)
        SettingsSection(title = "记账与编辑", aside = "ENTRY")
        HelpEntryBlock(entry = HelpEntry(
            title = "新增与补记", icon = EcoIcons.Edit, paragraphs = listOf(
                "在`流水`右下角点`记一笔`，选择支出或收入，输入金额并选择分类。金额键盘支持加减乘除；填写商户或备注后，点金额可返回计算。",
                "`日期`和`时间`直接显示在金额下方，可补记过去的账单。新账默认打开记账页时的当前时间，保存采用页面所显示的日期和时分。",
                "支出分类前三项固定为`餐饮`、`购物`、`日常`，第四项为`更多`。更多只展示补充分类，可搜索或上下滚动选择。选完返回记账页，已填金额、日期、时间和备注会保留。",
            )), tint = MaterialTheme.eco.blue)
        HelpEntryBlock(entry = HelpEntry(
            title = "编辑、删除与退款", icon = EcoIcons.Receipt, paragraphs = listOf(
                "点流水记录打开编辑页。日期、时间和分类均可修改；打开旧账时保留原时间和原分类。默认一页展示，小屏、大字号或键盘弹出时可滚动表单，底部`保存修改`保持可达。",
                "右上角`删除`会弹出确认，确认后删除该笔记录。支出记录右上角提供`退款`或`撤销退款`；如果改过表单，请先保存，再操作退款。",
                "点`退款`可填写本次金额，支持部分或全额退款；部分退款后可继续退款。待处理退款也可修改金额并选择原支出。`撤销退款`会清除这笔支出关联的退款记录并恢复其支出金额。已退款账单若要改金额或收支方向，需先撤销退款。",
                "退款信息显示在原支出上，不再作为一笔普通收入重复展示。退款会修正原消费所在周期的支出；部分退款按剩余实付金额统计，全额退款后不再消耗预算。",
            )), tint = MaterialTheme.eco.blue)
        HelpEntryBlock(entry = HelpEntry(
            title = "统一账本", icon = EcoIcons.Book, paragraphs = listOf(
                "记账、导入和确认账单均无需选择钱包。旧账及已有退款关联保留；支付宝、微信等支付来源信息仍可用于自动识别。",
            )), tint = MaterialTheme.eco.blue)
        SettingsSection(title = "商户与分类", aside = "MERCHANT")
        HelpEntryBlock(entry = HelpEntry(
            title = "沿用规则与本次例外", icon = EcoIcons.Tag, paragraphs = listOf(
                "输入商户名称时可选择已有商户建议，沿用`设置 → 商户归类`中保存的同收支方向分类。**本次手动选择的分类优先**，商户默认不会盖掉你已经手选的分类。",
                "例如学校食堂默认属于餐饮，本笔手选日常后直接保存，只把这一笔归入日常，默认规则及其他旧账保持原样。下次输入学校食堂，仍可沿用餐饮。",
                "`设为该商户的默认分类`默认不勾选。只有主动勾选并保存，才更新该商户同收支方向的规则，并同步对应旧账分类。改变商户或手选分类后，该勾选会清除，避免误改长期规则。",
                "打开已保存的例外账单不会将它改回商户默认；编辑成另一个商户时，若本次没有手选分类，可沿用新商户的规则。收入和支出分别匹配。",
            )), tint = MaterialTheme.eco.blue)
        HelpEntryBlock(entry = HelpEntry(
            title = "关联账单数", icon = EcoIcons.List, paragraphs = listOf(
                "商户归类页的`关联账单 N 笔`统计现有账本中同商户、同收支方向的正式账单，包含本次例外；不表示每笔都用了同一分类。删除对应账单后数量会减少，原始退款事件不重复计数。",
                "旧版显示的命中次数是学习次数，不能代表实际使用次数，1.5.3已改用关联账单数。",
            )), tint = MaterialTheme.eco.blue)
        SettingsSection(title = "预算与概览", aside = "BUDGET")
        HelpEntryBlock(entry = HelpEntry(
            title = "首页与预算设置", icon = EcoIcons.Home, paragraphs = listOf(
                "首页人物、已花金额、预算金额和进度条反映当前预算使用情况，点击人物的浮字只作装饰，不增加收入。人物按预算使用率每20%切换一档。",
                "在`我的`页点顶部预算卡的铅笔编辑当前预算，在`分类`中为支出分类分配预算。支出趋势小图显示最近 7 天，点开详情可选结束日期或累计月份，左右滑动图表查看金额。趋势按原付款金额统计。",
            )), tint = MaterialTheme.eco.blue)
        HelpEntryBlock(entry = HelpEntry(
            title = "分类进度条与滚动", icon = EcoIcons.Pie, paragraphs = listOf(
                "在`概览 → 预算使用`中，分类金额、合计、圆环和进度条统一排除体验预算与不计入预算的支出。每个分类显示已用百分比；进度条与分类同色，达到100%后填满，超过预算继续显示实际百分比，并用醒目文字标出超出金额。未设预算的分类显示未设预算，不显示预算进度。",
                "分类5条及以内不吸顶、不单独滚动分类列表；超过5条时上方圆环固定，下方列表滚动。上方收支卡及日/月支出日历仍保留，日/月选择后可查看对应流水。",
                "切换到`全部消费`可查看包含不计入预算项的各分类消费金额及占比。占比以全部消费为分母，右侧另列其中不计预算的总额；点击分类或圆环可高亮相应分类。圆环外环展示消费分布，预算使用视图则以外环表示预算分配、内环表示预算消耗。",
            )), tint = MaterialTheme.eco.blue)
        HelpEntryBlock(entry = HelpEntry(
            title = "预算与支出读数", icon = EcoIcons.Chart, paragraphs = listOf(
                "`首页已花`和`剩余预算`排除不计入预算的支出，并扣除已关联退款；AA支出按人数平分后逐笔取到分。",
                "`概览 → 全部消费`同样扣除退款、折算AA，但仍包含不计入预算的支出；`预算使用`则排除体验预算与不计入预算的支出，两种视图分别保持金额和图表一致。",
                "`本月结余`等于所选周期的普通收入减去扣除退款后的支出。结余不按AA人数分摊，也不排除不计入预算项；原始退款事件不会再重复当作普通收入。",
                "AA标记不会自动生成他人的还款账单。",
            )), tint = MaterialTheme.eco.blue)
        HelpEntryBlock(entry = HelpEntry(
            title = "每月首日", icon = EcoIcons.Calendar, paragraphs = listOf(
                "在`个人 → 每月首日`设置记账周期，可选1—30日或月末。设置为10日时，一期包含本月10日至下月9日；遇短月会按该月实际最后一天作为边界。流水和概览随所选周期统计。",
            )), tint = MaterialTheme.eco.blue)
        SettingsSection(title = "账单导入与导出", aside = "FILES")
        HelpEntryBlock(entry = HelpEntry(
            title = "导入微信与支付宝账单", icon = EcoIcons.File, paragraphs = listOf(
                "在`个人 → 导入 / 导出`选择账单文件，支持微信XLSX及支付宝CSV；先查看预览，再确认导入。支付宝账单中的余额宝收益会自动跳过。",
                "有可识别单号的重复行会跳过；后续导入也可能补充已有记录的退款状态。自动捕获与导入之间仍可能有重复，请核对流水。导入的退款会关联原支出，无法明确关联时到待处理记录中选择原单。",
            )), tint = MaterialTheme.eco.blue)
        HelpEntryBlock(entry = HelpEntry(
            title = "导出通用CSV", icon = EcoIcons.Download, paragraphs = listOf(
                "在`个人 → 导入 / 导出 → 导出账单`选择全部账单或指定日期区间，再点`导出并保存 CSV`，由系统选择保存位置。区间包含首尾两天，导出完整范围，不受流水分页影响。",
                "文件采用UTF-8编码，包含日期时间、收支、分类、金额、商户、备注、来源、已退款金额、实付金额、退款状态、AA人数、是否计入预算和预算归属；逗号、双引号及多行备注会保留。",
                "**金额列是原金额，实付金额列是扣除退款后的金额**；实付金额不会再按AA人数相除。原始退款事件不重复导出为收入，退款信息列在原支出行中。",
                "不同软件的导入模板不同，需要按对方要求映射字段。CSV用于查看、对账和迁移，**不是本应用的完整恢复备份**；导出不会自动上传文件。",
            )), tint = MaterialTheme.eco.blue)
        SettingsSection(title = "自动记账", aside = "CAPTURE")
        HelpEntryBlock(entry = HelpEntry(
            title = "开关与入账方式", icon = EcoIcons.Check, paragraphs = listOf(
                "对金额和分类有效的普通交易，`自动入账`、`悬浮窗入账`或`入账提醒`任一开启，都可能先入账再提醒。若希望普通交易逐条确认，需要将这三项都关闭。只关闭自动入账，不等于逐条确认。",
                "自动入账和悬浮窗入账互斥，入账提醒独立。没有识别出有效支付信息时不会凭空创建账单；退款和微信红包另有确认流程。",
            )), tint = MaterialTheme.eco.blue)
        HelpEntryBlock(entry = HelpEntry(
            title = "权限与后台", icon = EcoIcons.Shield, paragraphs = listOf(
                "在`设置 → 权限`开启所需采集通道，在`监听的 App`选择来源。通知使用权用于读取其他应用的通知，本应用通知权限用于发送提醒，两者不同。银行短信通道只接收新到短信，不读取历史短信。",
                "后台采集还受系统省电、自启动和进程管理影响，请查看`保活与自启动`。已授权但通知监听未连接时，应用会尝试恢复；系统未恢复时仍需在手机检查权限和后台设置。",
            )), tint = MaterialTheme.eco.blue)
        HelpEntryBlock(entry = HelpEntry(
            title = "悬浮卡、红包与退款", icon = EcoIcons.Overlay, paragraphs = listOf(
                "普通悬浮卡通常延迟约3秒，60秒无操作会关闭。**关闭已入账卡片会保留账单，已入账卡片的忽略或撤销会删除该笔账单**。卡片未展示时是否有通知，取决于入账提醒及通知权限。",
                "微信红包领取页的金额只作预填，需要明确确认才记账；关闭或超时保留待确认，全部确认不会自动处理红包。捕获到的退款也先进入待处理，选择原支出后再关联，不凭同金额自动猜配。",
            )), tint = MaterialTheme.eco.blue)
        SettingsSection(title = "漏记、重记与兼容性", aside = "DIAGNOSTICS")
        HelpEntryBlock(entry = HelpEntry(
            title = "排查漏记与重记", icon = EcoIcons.Warning, paragraphs = listOf(
                "先核对权限、监听来源及后台状态。监听目录中有某个应用，不代表它的所有支付场景都能识别；受保护或短暂出现的页面仍可能读不到内容。",
                "应用会综合金额、时间、交易身份和来源去重，但只有金额相同不足以可靠判断是不是同一笔。支付应用、商家和银行可能同时提供信息，仍可能出现漏记或重复，请核对后补记或删除多余记录。",
                "需要排查时，在`设置 → 开发者功能 → 记账诊断`开启记录，正常使用后导出诊断日志，并附支付来源、大概时间和金额。诊断只记录开启后的信息，不能追溯以前未采到的支付。",
                "诊断日志可能含商户、金额及页面文字，分享前请自行检查；日志不会自动上传。",
            )), tint = MaterialTheme.eco.blue)
        HelpEntryBlock(entry = HelpEntry(
            title = "银行应用与升级", icon = EcoIcons.Phone, paragraphs = listOf(
                "部分银行应用可能提示无障碍限制。遇到此类提示，可先在系统设置临时停用本应用的无障碍服务，再打开银行应用；停用期间支付页面采集也会暂停。",
                "从本项目GitHub Releases获取安装包。使用同包名、同签名的新版可覆盖安装，保留账本；**不要先卸载**。卸载会清除本机数据，通用CSV不包含本应用完整设置和恢复信息。",
            )), tint = MaterialTheme.eco.blue)
        SettingsNote("如需反馈，请保留对应支付时间、金额与诊断记录；手机上的权限、文件保存和支付兼容性需以实际使用结果为准。")
    }
}

// ---------- 数据 ----------

/** 一个说明条目：图标 + 标题 + 若干段正文（正文支持 `term` 与 **强调**） */
private data class HelpEntry(
    val title: String,
    val icon: ImageVector,
    val paragraphs: List<String>,
)

// ---------- 条目 ----------

/**
 * 一个条目：图标 + 标题 + （可选）预算环图 + 多段正文。
 * 不套卡片——条目直接排在纸面上，靠 [ENTRY_GAP] 与分组头区分层次。
 */
@Composable
private fun HelpEntryBlock(entry: HelpEntry, tint: Color) {
    val c = MaterialTheme.eco
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                entry.icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(entry.title, style = HelpTitle, color = c.ink, maxLines = 1)
        }
        entry.paragraphs.forEach { paragraph ->
            Spacer(Modifier.height(12.dp))
            HelpText(paragraph)
        }
    }
    Spacer(Modifier.height(ENTRY_GAP))
}

/** 条目之间的纵向间隔：正文段落 12dp，条目之间再放大一档，分段更明确 */
private val ENTRY_GAP = 24.dp

/**
 * 正文：`term` → 主色浅底 + 深色字；**强调** → 暖金浅底 + 加粗深字。
 * 两种标记都不改变字号，只靠底色与字重区分，长段落里也能一眼扫到关键口径。
 */
@Composable
private fun HelpText(text: String) {
    val c = MaterialTheme.eco
    val annotated = remember(text, c.blueSoft, c.blueDeep, c.amberSoft, c.ink2) {
        buildAnnotatedString {
            var cursor = 0
            INLINE_MARK.findAll(text).forEach { match ->
                if (match.range.first > cursor) append(text.substring(cursor, match.range.first))
                val term = match.groups[2]
                if (term != null) {
                    // 反引号：界面名 / 状态词
                    withStyle(
                        SpanStyle(background = c.blueSoft, color = c.blueDeep, fontWeight = FontWeight.W600),
                    ) { append(term.value) }
                } else {
                    // 星号：口径强调
                    withStyle(
                        SpanStyle(background = c.amberSoft, color = c.ink2, fontWeight = FontWeight.W700),
                    ) { append(match.groups[1]!!.value) }
                }
                cursor = match.range.last + 1
            }
            if (cursor < text.length) append(text.substring(cursor))
        }
    }
    Text(annotated, style = HelpBody, color = c.muted)
}
