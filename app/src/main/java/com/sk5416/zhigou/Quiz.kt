package com.sk5416.zhigou

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sk5416.zhigou.data.*

@Composable fun Quiz(s: Session, catalog: Catalog, vm: LearningViewModel, busy: Boolean, read: (String, String) -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    val q = s.questions[s.index]
    val chosen = s.selections[q.id].orEmpty()
    val revealed = s.finished || (s.mode != "test" && q.id in s.submitted)
    val compatible = LearningRules.compatible(s, catalog)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${if (s.mode == "test") "自测" else if (s.mode == "review") "错题复习" else "章节练习"} · ${s.index + 1}/${s.questions.size}", style = MaterialTheme.typography.titleLarge)
        if (s.finished) Text("已完成 · ${s.questions.count { LearningRules.correct(it, s.selections[it.id].orEmpty()) }}/${s.questions.size} 正确。自测仅供自查。")
        if (!compatible) Text("此会话含旧版题目，原始答案和解析保留供回顾。请从课程目录新建练习。")
        Text("${q.chapterId} · ${if (q.type == "multiple") "多选题 · 可选多个答案，全对得分" else "单选题"} · 修订 ${q.revision}", style = MaterialTheme.typography.bodySmall)
        Text(q.stem, style = MaterialTheme.typography.titleMedium)
        q.options.forEach { option ->
            OutlinedCard(onClick = { vm.select(s.id, q.id, option.id) }, enabled = !revealed && !busy && compatible, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp)) {
                    if (q.type == "multiple") Checkbox(option.id in chosen, null) else RadioButton(option.id in chosen, null)
                    Text("${option.id}. ${option.text}", Modifier.padding(start = 8.dp))
                }
            }
        }
        if (revealed) {
            Text(if (LearningRules.correct(q, chosen)) "回答正确" else "回答有误 · 你的答案：${chosen.sorted().joinToString().ifEmpty { "未答" }}", color = MaterialTheme.colorScheme.primary)
            Text("正确答案：${q.answers.sorted().joinToString()}", style = MaterialTheme.typography.titleMedium)
            q.options.forEach { Text("${it.id}：${it.explanation}") }
            Text("关键误区：${q.misconception}")
            Text("知识点：${q.knowledge.joinToString()} · ${q.difficulty} · ${q.ability}", style = MaterialTheme.typography.bodySmall)
            TextButton({ read(q.chapterId, "") }) { Text("回到本章理解依据") }
        } else if (s.mode != "test") Button({ vm.submit(s.id) }, enabled = chosen.isNotEmpty() && !busy && compatible) { Text("提交本题") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton({ vm.navigate(s.id, s.index - 1) }, enabled = s.index > 0 && !busy) { Text("上一题") }
            OutlinedButton({ vm.navigate(s.id, s.index + 1) }, enabled = s.index < s.questions.lastIndex && !busy) { Text("下一题") }
        }
        if (s.mode == "test" && !s.finished) Button({ confirm = true }, enabled = !busy && compatible, modifier = Modifier.fillMaxWidth()) { Text("交卷并查看解析") }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("确认交卷") }, text = { Text("还有 ${s.questions.count { s.selections[it.id].orEmpty().isEmpty() }} 题未作答，交卷后未答计错，答案无法更改。") }, confirmButton = { TextButton({ confirm = false; vm.submit(s.id, true) }) { Text("确认交卷") } }, dismissButton = { TextButton({ confirm = false }) { Text("继续作答") } })
}
