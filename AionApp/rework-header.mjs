import fs from 'fs';

const path = 'AionApp/app/src/main/kotlin/com/aion/chat/compose/ui/home/HomeScreen.kt';
let s = fs.readFileSync(path, 'utf8');

// ── 1. 顶部卡 → 无卡头区（Sean 左 ♥ Yuri 右，头像下名字，天数块，可编辑情话） ──
const headerStart = s.indexOf('            // 1~3. 顶部卡');
const headerEnd = s.indexOf('            // 4. 一起听歌');
if (headerStart < 0 || headerEnd < 0 || headerEnd <= headerStart) {
  console.log('ERR: 头区锚点未命中', headerStart, headerEnd);
  process.exit(1);
}
const header = `            // 1~3. 顶部头区：直接浮在背景大图上（无卡无框）。Sean 左 ｜ 连接符 ｜ Yuri 右
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 30.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        GlassAvatar(initial = "S", size = 74)
                        Text("Sean", style = glassText(alpha = 0.95f, size = 13), modifier = Modifier.padding(top = 6.dp))
                    }
                    Text(
                        "♥",
                        style = glassText(alpha = 0.95f, size = 26),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        GlassAvatar(initial = "Y", size = 74)
                        Text("Yuri", style = glassText(alpha = 0.95f, size = 13), modifier = Modifier.padding(top = 6.dp))
                    }
                }
                // 在一起天数（大号数字 + 天）
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(top = 14.dp)
                ) {
                    Text(
                        HomecomingData.daysTogether().toString(),
                        style = glassText(size = 54, weight = FontWeight.Light, serif = true)
                    )
                    Text(
                        "天",
                        style = glassText(alpha = 0.9f, size = 16),
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )
                }
                // 今日情话：点一下就能改（本地即时保存；Supabase 配置后远程同步）
                Text(
                    quote,
                    style = glassText(alpha = 0.95f, size = 14),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clickable {
                            editDraft = quote
                            showQuoteEditor = true
                        }
                )
            }

`;
s = s.slice(0, headerStart) + header + s.slice(headerEnd);

// ── 2. 状态变量：情话 + 编辑器 ──
const varAnchor = '    LaunchedEffect(Unit) {';
const varAdd = `    var quote by remember { mutableStateOf(HomecomingData.quoteForToday()) }
    var showQuoteEditor by remember { mutableStateOf(false) }
    var editDraft by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

`;
if (!s.includes(varAnchor)) { console.log('ERR: LaunchedEffect 锚点未命中'); process.exit(1); }
s = s.replace(varAnchor, varAdd + varAnchor);

// ── 3. LaunchedEffect：本地情话 + Supabase 远程拉取 ──
const leOld = `    LaunchedEffect(Unit) {
        recent = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            HomecomingData.loadRecent(context)
        }
    }`;
const leNew = `    LaunchedEffect(Unit) {
        recent = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val local = HomecomingData.loadQuote(context)
            val remote = SupabaseQuoteSync.pull()
            if (!remote.isNullOrBlank()) {
                HomecomingData.saveQuote(context, remote)
                quote = remote
            } else {
                quote = local
            }
            HomecomingData.loadRecent(context)
        }
    }`;
if (!s.includes(leOld)) { console.log('ERR: LaunchedEffect 原文未命中'); process.exit(1); }
s = s.replace(leOld, leNew);

// ── 4. 页面 Column 底部留出导航胶囊净空 ──
s = s.replace(
  '.padding(horizontal = 18.dp, vertical = 16.dp),',
  '.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 112.dp),'
);

// ── 5. 情话编辑弹窗：插在 HomeScreen 收尾（Box 内） ──
const tail = '        }\n    }\n}\n';
const tailIdx = s.lastIndexOf(tail);
if (tailIdx < 0) { console.log('ERR: 尾部锚点未命中'); process.exit(1); }
const dialog = '        }\n\n        if (showQuoteEditor) {\n' +
  '            androidx.compose.material3.AlertDialog(\n' +
  '                onDismissRequest = { showQuoteEditor = false },\n' +
  '                title = { Text("改一下这句话", fontSize = 16.sp, color = HomecomingColors.Ink) },\n' +
  '                text = {\n' +
  '                    androidx.compose.material3.OutlinedTextField(\n' +
  '                        value = editDraft,\n' +
  '                        onValueChange = { editDraft = it },\n' +
  '                        minLines = 2,\n' +
  '                        modifier = Modifier.fillMaxWidth()\n' +
  '                    )\n' +
  '                },\n' +
  '                confirmButton = {\n' +
  '                    androidx.compose.material3.TextButton(onClick = {\n' +
  '                        val v = editDraft.trim()\n' +
  '                        if (v.isNotEmpty()) {\n' +
  '                            quote = v\n' +
  '                            HomecomingData.saveQuote(context, v)\n' +
  '                            scope.launch { SupabaseQuoteSync.push(v) }\n' +
  '                        }\n' +
  '                        showQuoteEditor = false\n' +
  '                    }) { Text("保存", color = HomecomingColors.Accent) }\n' +
  '                },\n' +
  '                dismissButton = {\n' +
  '                    androidx.compose.material3.TextButton(onClick = { showQuoteEditor = false }) {\n' +
  '                        Text("取消", color = HomecomingColors.InkSoft)\n' +
  '                    }\n' +
  '                }\n' +
  '            )\n' +
  '        }\n' +
  '    }\n' +
  '}\n';
s = s.slice(0, tailIdx) + dialog + s.slice(tailIdx + tail.length);

// ── 6. import：SupabaseQuoteSync / rememberCoroutineScope / launch ──
if (!s.includes('SupabaseQuoteSync')) {
  s = s.replace(
    'import com.aion.chat.compose.data.HomecomingData',
    'import com.aion.chat.compose.data.HomecomingData\nimport com.aion.chat.compose.data.SupabaseQuoteSync'
  );
}
if (!s.includes('rememberCoroutineScope')) {
  s = s.replace(
    'import androidx.compose.runtime.setValue',
    'import androidx.compose.runtime.rememberCoroutineScope\nimport androidx.compose.runtime.setValue'
  );
}
if (!s.includes('import kotlinx.coroutines.launch')) {
  s = s.replace(
    'import kotlinx.coroutines.withContext',
    'import kotlinx.coroutines.launch\nimport kotlinx.coroutines.withContext'
  );
}

fs.writeFileSync(path, s);
console.log('顶部头区重构 + 情话编辑器完成');
