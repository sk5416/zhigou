package com.sk5416.zhigou

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sk5416.zhigou.data.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { ZhigouApp() } }
}
private fun date(time: Long) = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(time))
private fun mode(mode: String) = when (mode) { "test" -> "自测"; "review" -> "错题复习"; else -> "章节练习" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ZhigouApp(vm: LearningViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val preview by vm.restorePreview.collectAsStateWithLifecycle()
    val dark = when (state.preferences.theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFF8BD6BB)) else lightColorScheme(primary = Color(0xFF246B5B), background = Color(0xFFF7F8F3), surface = Color(0xFFF7F8F3))) {
        var page by rememberSaveable { mutableStateOf("home") }
        var chapter by rememberSaveable { mutableStateOf("") }
        var anchor by rememberSaveable { mutableStateOf("") }
        var session by rememberSaveable { mutableStateOf("") }
        var clearConfirm by remember { mutableStateOf(false) }
        val catalog = state.catalog
        val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::export) }
        val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::preview) }
        val openChapter: (String, String) -> Unit = { id, fragment -> chapter = id; anchor = fragment; page = "reader" }
        val openSession: (String) -> Unit = { session = it; page = "quiz" }
        BackHandler(page in listOf("reader", "quiz")) { page = if (page == "reader") "courses" else "home" }
        Scaffold(topBar = {
            TopAppBar(title = { Text(if (page == "reader") catalog?.chapters?.find { it.id == chapter }?.title ?: "课程" else "知构 · ${when(page) { "courses" -> "课程"; "review" -> "复习"; "progress" -> "进度"; "settings" -> "设置"; "quiz" -> "练习"; else -> "学习" }}", maxLines = 1) },
                navigationIcon = { if (page in listOf("reader", "quiz")) TextButton(onClick = { page = if (page == "reader") "courses" else "home" }) { Text("返回") } })
        }, bottomBar = {
            if (page !in listOf("reader", "quiz")) NavigationBar {
                listOf("home" to "学习", "courses" to "课程", "review" to "复习", "progress" to "进度", "settings" to "设置").forEach { (id, title) ->
                    NavigationBarItem(selected = page == id, onClick = { page = id }, icon = { Text(title.take(1)) }, label = { Text(title) })
                }
            }
        }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(4.dp)) else Spacer(Modifier.height(4.dp))
                if (catalog == null) { Text("正在准备离线课程…", Modifier.padding(24.dp)) }
                else when (page) {
                    "home" -> Home(state, openChapter, openSession, { page = "courses" }, { page = "review" })
                    "courses" -> Courses(state, openChapter, { ids -> vm.start("test", ids, openSession) })
                    "reader" -> catalog.chapters.find { it.id == chapter }?.let { c -> Reader(c, anchor, state, dark, vm, openChapter, { vm.start("practice", setOf(c.id), openSession) }) }
                    "quiz" -> state.sessions.find { it.id == session }?.let { Quiz(it, catalog, vm, busy, openChapter) } ?: Text("练习记录已清空", Modifier.padding(24.dp))
                    "review" -> Review(state, { ids -> vm.start("review", ids, openSession) }, openChapter)
                    "progress" -> Progress(state, openSession)
                    "settings" -> Settings(state, vm, { export.launch("zhigou-${System.currentTimeMillis()}.backup.json") }, { restore.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, { clearConfirm = true })
                }
            }
        }
        if (catalog != null && !state.preferences.welcomed) AlertDialog(onDismissRequest = {}, title = { Text("欢迎使用知构") }, text = { Text("先读结论与案例，再做练习解释判断依据。熟悉的章节可以先自测。\n\n已读、成绩和错题分别记录；本应用仅供自查。所有教材离线使用。\n\n当前为开发验证版，全套课程已内置；正式发布验收仍在进行。") }, confirmButton = { TextButton(onClick = { vm.preferences(state.preferences.copy(welcomed = true)) }) { Text("开始学习") } })
        error?.let { AlertDialog(onDismissRequest = { vm.error.value = null }, title = { Text("操作提示") }, text = { Text(it) }, confirmButton = { TextButton(onClick = { vm.error.value = null }) { Text("知道了") } }) }
        preview?.let { b -> AlertDialog(onDismissRequest = { vm.restorePreview.value = null }, title = { Text("确认替换学习数据") }, text = { Text("备份课程版本：${b.contentVersion}\n阅读记录 ${b.readings.size} 条，练习 ${b.sessions.size} 次，作答 ${b.attempts.size} 条。\n${b.sessions.count { !LearningRules.compatible(Codec.json.decodeFromString(it.payload), catalog!!) }} 个会话题目版本有变化，恢复后仅供回顾。\n将替换全部现有记录与阅读设置。") }, confirmButton = { TextButton(onClick = { vm.restore() }, enabled = !busy) { Text("替换恢复") } }, dismissButton = { TextButton(onClick = { vm.restorePreview.value = null }) { Text("取消") } }) }
        if (clearConfirm) AlertDialog(onDismissRequest = { clearConfirm = false }, title = { Text("清空全部学习数据？") }, text = { Text("阅读位置、书签、练习、错题和设置均会清空。教材仍保留，请先导出需要的备份。") }, confirmButton = { TextButton(onClick = { clearConfirm = false; vm.clear() }) { Text("确认清空") } }, dismissButton = { TextButton(onClick = { clearConfirm = false }) { Text("取消") } })
    }
}

@Composable private fun Tile(title: String, subtitle: String, action: (() -> Unit)? = null) {
    Card(onClick = { action?.invoke() }, enabled = action != null, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(subtitle, style = MaterialTheme.typography.bodyMedium) }
    }
}
@Composable private fun Home(s: LearningState, read: (String, String) -> Unit, quiz: (String) -> Unit, courses: () -> Unit, review: () -> Unit) {
    val chapters = s.catalog!!.chapters.filter { it.published && it.id.startsWith("C") }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
        item { Text("把知识，构成判断力。", style = MaterialTheme.typography.headlineSmall) }
        item { Tile("今日从这里继续", "已读 ${s.readings.count { it.completed && it.chapterId.startsWith("C") }} / ${chapters.size} 章 · 待复习 ${currentMistakes(s).count { !it.corrected }} 题", courses) }
        val last = s.readings.firstOrNull { r -> chapters.any { it.id == r.chapterId } }
        val next = chapters.find { it.id == last?.chapterId } ?: chapters.firstOrNull()
        next?.let { c -> item { Tile("继续阅读 · ${c.id}", c.title) { read(c.id, "") } } }
        item { Tile("错题复习", "回到误区，重新解释判断依据", review) }
        items(s.sessions.filter { !it.finished }.take(3), key = { it.id }) { session -> Tile("继续${mode(session.mode)}", "${session.submitted.size}/${session.questions.size} 题 · ${date(session.updatedAt)}") { quiz(session.id) } }
        item { Text("近期练习", style = MaterialTheme.typography.titleLarge) }
        items(s.sessions.filter { it.finished }.take(3), key = { it.id }) { session -> SessionTile(session, quiz) }
        item { Text("开发验证版 · 当前 ${chapters.size}/73 章，全部内容无需联网。", style = MaterialTheme.typography.bodySmall) }
    }
}
@Composable private fun Courses(s: LearningState, read: (String, String) -> Unit, test: (Set<String>) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var bookmarks by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    val chapters = s.catalog!!.chapters.filter { it.published }
    val results = remember(query, chapters) { chapters.filter { query.isBlank() || it.title.contains(query, true) || it.body.contains(query, true) } }
    LazyColumn(Modifier.padding(horizontal = 20.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
        item { OutlinedTextField(query, { query = it }, label = { Text("搜索课程和附录") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { FilterChip(bookmarks, { bookmarks = !bookmarks }, label = { Text("只看书签") }); FilterChip(false, { selected = LearningRules.completedChapters(s.readings, s.catalog).toList() }, label = { Text("选择已读") }) } }
        item { Button(onClick = { test(selected.toSet()) }, enabled = selected.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("所选 ${selected.size} 章自测 · 最多 20 题") } }
        item { Text("共用题库，仅供学习自查。勾选章节可自由组合自测。", style = MaterialTheme.typography.bodySmall) }
        results.filter { !bookmarks || s.readings.any { r -> r.chapterId == it.id && r.bookmarked } }.groupBy { it.part }.forEach { (part, group) ->
            item { Text(part, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
            items(group, key = { it.id }) { c ->
                val r = s.readings.find { it.chapterId == c.id }
                val result = latestScore(s, c.id)
                Row {
                    if (c.id.startsWith("C")) Checkbox(c.id in selected, { selected = if (c.id in selected) selected - c.id else selected + c.id })
                    Column(Modifier.weight(1f)) {
                        Tile("${c.id} · ${c.title}", "${c.depth} ${c.priority} · ${if (r?.completed == true) "已读" else "未读"}${if (r?.bookmarked == true) " · 已收藏" else ""}${result?.let { " · 自测 $it" } ?: ""}") { read(c.id, LearningRules.searchAnchor(c, query)) }
                        if (query.isNotBlank()) Text(snippet(c.body, query), Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (results.isEmpty()) item { Text("没有找到相关内容") }
    }
}
private fun snippet(body: String, query: String): String { val i = body.indexOf(query, ignoreCase = true).coerceAtLeast(0); return body.substring((i - 24).coerceAtLeast(0), (i + 90).coerceAtMost(body.length)).replace("\n", " ") }
private fun currentMistakes(s: LearningState) = s.mistakes.filter { m -> s.catalog!!.questions.any { it.id == m.questionId && it.revision == m.revision } }
private fun latestScore(s: LearningState, chapter: String): String? {
    val session = s.sessions.firstOrNull { it.finished && it.mode == "test" && it.questions.any { q -> q.chapterId == chapter } && LearningRules.compatible(it, s.catalog!!) } ?: return null
    val qs = session.questions.filter { it.chapterId == chapter }
    return "${qs.count { LearningRules.correct(it, session.selections[it.id].orEmpty()) }}/${qs.size}"
}
@Composable private fun Review(s: LearningState, start: (Set<String>) -> Unit, read: (String, String) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("") }
    val mistakes = currentMistakes(s).filter { filter.isBlank() || it.chapterId == filter }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("错题是下一次判断的线索", style = MaterialTheme.typography.headlineSmall) }
        item { OutlinedTextField(filter, { filter = it.uppercase().trim() }, label = { Text("章节筛选，如 C12；留空为全部") }) }
        item { Button(onClick = { start(mistakes.filter { !it.corrected }.map { it.chapterId }.toSet()) }, enabled = mistakes.any { !it.corrected }) { Text("复习 ${mistakes.count { !it.corrected }} 道待订正题") } }
        if (mistakes.isEmpty()) item { Text("这里还没有错题。完成练习后，答错的题目会自动收录。") }
        items(mistakes, key = { "${it.questionId}@${it.revision}" }) { m ->
            val q = s.catalog!!.questions.first { it.id == m.questionId }
            Tile("${m.chapterId} · ${if (m.corrected) "已订正" else "待复习"}", q.stem) { read(m.chapterId, "") }
        }
        val old = s.mistakes.size - currentMistakes(s).size
        if (old > 0) item { Text("另有 $old 条旧版本错题保留在历史，当前复习使用新版题目。") }
    }
}
@Composable private fun SessionTile(s: Session, open: (String) -> Unit) {
    Tile("${mode(s.mode)} · ${date(s.updatedAt)}", if (s.finished) "${s.questions.count { LearningRules.correct(it, s.selections[it.id].orEmpty()) }}/${s.questions.size} 正确 · 查看解析" else "未完成 · 继续作答") { open(s.id) }
}
@Composable private fun Progress(s: LearningState, open: (String) -> Unit) {
    val chapters = s.catalog!!.chapters.filter { it.published && it.id.startsWith("C") }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("学习留下痕迹", style = MaterialTheme.typography.headlineSmall) }
        item { Text("已读 ${s.readings.count { it.completed && it.chapterId.startsWith("C") }}/${chapters.size} 章（${100 * s.readings.count { it.completed && it.chapterId.startsWith("C") } / chapters.size.coerceAtLeast(1)}%） · 累计作答 ${s.attempts.size} 次\n待复习 ${currentMistakes(s).count { !it.corrected }} 题。阅读完成不等于能力掌握。") }
        items(chapters, key = { it.id }) { c -> Text("${c.id} ${c.title}\n最近自测：${latestScore(s, c.id) ?: "尚无当前版本记录"}") }
        item { Text("全部练习历史", style = MaterialTheme.typography.titleLarge) }
        items(s.sessions, key = { it.id }) { SessionTile(it, open) }
    }
}
@Composable private fun Settings(s: LearningState, vm: LearningViewModel, export: () -> Unit, restore: () -> Unit, clear: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("阅读与数据", style = MaterialTheme.typography.headlineSmall)
        Text("主题")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (id, title) -> FilterChip(s.preferences.theme == id, { vm.preferences(s.preferences.copy(theme = id)) }, label = { Text(title) }) } }
        Text("正文字号：${s.preferences.font}")
        Slider(s.preferences.font.toFloat(), { vm.preferences(s.preferences.copy(font = it.toInt())) }, valueRange = 14f..28f, steps = 13)
        Button(export, Modifier.fillMaxWidth()) { Text("导出学习备份") }
        OutlinedButton(restore, Modifier.fillMaxWidth()) { Text("选择备份并预览恢复") }
        Text("恢复会替换全部记录与设置。备份不包含教材，请保管导出的文件。", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(clear, Modifier.fillMaxWidth()) { Text("清空学习数据") }
        HorizontalDivider()
        Text("知构 ${BuildConfig.VERSION_NAME}\n课程 ${s.catalog!!.version}\n数据库 1 · Android 10+\n个人单机使用 · 无网络权限")
    }
}
