package com.aion.chat.compose.ui.album

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.HomecomingAlbumStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 图库（照小红书那版三 tab 设计重做）：全部图片 / 分类 / 图册。
 * - 全部图片：我的图册横滑条 + 按日期分组的照片网格（可批量删除）
 * - 分类：图册按分类（头像/壁纸/截图/表情包/其他）分组横滑
 * - 图册：图册卡片网格 + 底部 编辑|添加 胶囊
 */
private val Ink = Color(0xFF1C1B18)
private val Sub = Color(0xFF9A968C)
private val Faint = Color(0xFFC9C4BA)
private val Line = Color(0xFFEDE7DC)
private val PageBg = Color(0xFFFCFBF8)
private val CardBg = Color(0xFFFFFFFF)
private val Danger = Color(0xFFC96B6B)
private val Accent = Color(0xFF7FA6C9)
private val Serif = FontFamily.Serif

private val KINDS = HomecomingAlbumStore.KINDS

@Composable
fun AlbumScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var photos by remember { mutableStateOf(listOf<HomecomingAlbumStore.AlbumPhoto>()) }
    var albums by remember { mutableStateOf(listOf<HomecomingAlbumStore.Album>()) }
    var tab by remember { mutableIntStateOf(0) }
    var selectMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var viewer by remember { mutableStateOf<HomecomingAlbumStore.AlbumPhoto?>(null) }
    var albumDetail by remember { mutableStateOf<String?>(null) }
    var showNewAlbum by remember { mutableStateOf(false) }
    var addTarget by remember { mutableStateOf<String?>(null) }
    var showManage by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var renameFor by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch(Dispatchers.IO) {
            val p = HomecomingAlbumStore.list(context)
            val a = HomecomingAlbumStore.albums(context)
            withContext(Dispatchers.Main) { photos = p; albums = a }
        }
    }
    LaunchedEffect(Unit) { refresh() }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(12)
    ) { uris: List<Uri> ->
        val target = addTarget ?: return@rememberLauncherForActivityResult
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val kind = albums.firstOrNull { it.name == target }?.kind ?: "其他"
            var okCount = 0
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readBytes()
                        if (HomecomingAlbumStore.addPhoto(context, bytes, "", "", target, kind) > 0) okCount++
                    }
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "已存入「$target」$okCount 张", Toast.LENGTH_SHORT).show()
                addTarget = null
                refresh()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(PageBg)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // ── 头部 ──
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp)) {
                    Spacer(Modifier.height(4.dp))
                    Row {
                        Text("‹", fontSize = 24.sp, color = Ink,
                            modifier = Modifier
                                .clickable { onBack() }
                                .padding(end = 10.dp, top = 10.dp))
                        Spacer(Modifier.weight(1f))
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("图库", fontSize = 46.sp, color = Ink, fontFamily = Serif, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.weight(1f))
                        Text("${photos.size}", fontSize = 20.sp, color = Ink,
                            modifier = Modifier.padding(bottom = 10.dp))
                    }
                    Text("/ 收集每一刻", fontSize = 14.sp, color = Sub,
                        modifier = Modifier.padding(top = 2.dp))
                    Text(
                        "stay curious\nstay kind",
                        fontSize = 12.sp, color = Faint, lineHeight = 18.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp)
                    )
                }
            }
            // ── Tabs ──
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        listOf("全部图片", "分类", "图册").forEachIndexed { i, t ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                                    .clickable { tab = i; selectMode = false; selected = emptySet() }
                            ) {
                                Text(
                                    t, fontSize = 15.sp,
                                    color = if (tab == i) Ink else Sub,
                                    fontWeight = if (tab == i) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(2.dp).background(Line)) {
                        Box(
                            Modifier
                                .align(
                                    when (tab) {
                                        0 -> Alignment.CenterStart
                                        1 -> Alignment.Center
                                        else -> Alignment.CenterEnd
                                    }
                                )
                                .padding(horizontal = if (tab == 1) 0.dp else 58.dp)
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(Ink)
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }

            // ── 全部图片 ──
            if (tab == 0) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Text("· 我的图册", fontSize = 15.sp, color = Ink, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text("查看全部 ›", fontSize = 12.sp, color = Sub,
                            modifier = Modifier.clickable { tab = 2 })
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        item {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(92.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(CardBg)
                                    .border(1.dp, Faint, RoundedCornerShape(14.dp))
                                    .clickable { showNewAlbum = true }
                                    .padding(vertical = 20.dp)
                            ) {
                                Text("＋", fontSize = 26.sp, color = Ink)
                                Spacer(Modifier.height(6.dp))
                                Text("新建图册", fontSize = 12.sp, color = Ink)
                                Spacer(Modifier.height(2.dp))
                                Text("0 张照片", fontSize = 10.sp, color = Sub)
                            }
                        }
                        items(albums) { a ->
                            AlbumCard(
                                album = a,
                                cover = photos.filter { it.album == a.name }.maxByOrNull { it.createdAt },
                                count = photos.count { it.album == a.name },
                                showMenu = false,
                                onOpen = { albumDetail = a.name }
                            )
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Text("· 全部图片", fontSize = 15.sp, color = Ink, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text("共 ${photos.size} 张", fontSize = 12.sp, color = Sub)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (selectMode) "取消" else "管理", fontSize = 13.sp, color = Ink,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable {
                                selectMode = !selectMode
                                selected = emptySet()
                            }
                        )
                    }
                }
                val groups = photos.groupBy { ph ->
                    ph.takenOn.ifBlank {
                        SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(ph.createdAt))
                    }
                }
                groups.forEach { (day, list) ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        val label = try {
                            val mm = day.split("-")
                            "${mm[1].toInt()}月${mm[2].toInt()}日"
                        } catch (e: Exception) { day }
                        Text(
                            label, fontSize = 12.sp, color = Sub,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                    list.forEach { ph ->
                        item(key = "p_" + ph.id) {
                            PhotoCell(
                                photo = ph,
                                selectMode = selectMode,
                                isSelected = selected.contains(ph.id),
                                onClick = {
                                    if (selectMode) {
                                        selected = if (selected.contains(ph.id)) selected - ph.id else selected + ph.id
                                    } else viewer = ph
                                }
                            )
                        }
                    }
                }
            }

            // ── 分类 ──
            if (tab == 1) {
                KINDS.forEach { kind ->
                    val list = albums.filter { it.kind == kind }
                    if (list.isEmpty()) return@forEach
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.padding(top = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                Text(kind, fontSize = 15.sp, color = Ink, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.weight(1f))
                                Text("编辑", fontSize = 12.sp, color = Sub,
                                    modifier = Modifier.clickable { showManage = true })
                            }
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                items(list) { a ->
                                    AlbumCard(
                                        album = a,
                                        cover = photos.filter { it.album == a.name }.maxByOrNull { it.createdAt },
                                        count = photos.count { it.album == a.name },
                                        showMenu = true,
                                        onOpen = { albumDetail = a.name },
                                        onMenu = { menuFor = a.name }
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
                if (albums.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "还没有图册。去「图册」页新建一个，再往里存照片。",
                            fontSize = 13.sp, color = Sub,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp, horizontal = 24.dp)
                        )
                    }
                }
            }

            // ── 图册 ──
            if (tab == 2) {
                albums.forEach { a ->
                    item(key = "a_" + a.name) {
                        AlbumCard(
                            album = a,
                            cover = photos.filter { it.album == a.name }.maxByOrNull { it.createdAt },
                            count = photos.count { it.album == a.name },
                            showMenu = true,
                            onOpen = { albumDetail = a.name },
                            onMenu = { menuFor = a.name }
                        )
                    }
                }
                if (albums.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "还没有图册。底部「添加」先建一个。",
                            fontSize = 13.sp, color = Sub,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp, horizontal = 24.dp)
                        )
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(110.dp)) }
        }

        // ── 底部 编辑|添加 胶囊（分类/图册 tab 显示） ──
        if (tab == 1 || tab == 2) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .border(1.dp, Line, RoundedCornerShape(50))
            ) {
                Text(
                    "编辑", fontSize = 15.sp, color = Ink,
                    modifier = Modifier
                        .clickable { showManage = true }
                        .padding(horizontal = 44.dp, vertical = 13.dp)
                )
                Box(Modifier.width(1.dp).height(20.dp).background(Line))
                Text(
                    "添加", fontSize = 15.sp, color = Ink,
                    modifier = Modifier
                        .clickable {
                            if (albums.isEmpty()) {
                                Toast.makeText(context, "先新建一个图册", Toast.LENGTH_SHORT).show()
                                showNewAlbum = true
                            } else {
                                addTarget = albums.first().name
                                pickImages.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        }
                        .padding(horizontal = 44.dp, vertical = 13.dp)
                )
            }
        }

        // ── 批量删除按钮 ──
        if (selectMode && selected.isNotEmpty()) {
            Text(
                "删除所选（${selected.size}）",
                fontSize = 14.sp, color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Danger)
                    .clickable {
                        scope.launch(Dispatchers.IO) {
                            selected.forEach { HomecomingAlbumStore.remove(context, it) }
                            withContext(Dispatchers.Main) {
                                selected = emptySet(); selectMode = false; refresh()
                            }
                        }
                    }
                    .padding(horizontal = 22.dp, vertical = 11.dp)
            )
        }
    }

    // ── 照片查看器 ──
    viewer?.let { ph ->
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xEE141210))
                .clickable { viewer = null }
        ) {
            val bmp by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(
                null, ph.id
            ) {
                withContext(Dispatchers.IO) { value = decode(ph.file(context)) }
            }
            bmp?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Text(
                "✕", fontSize = 20.sp, color = Color.White,
                modifier = Modifier
                    .statusBarsPadding()
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .clickable { viewer = null }
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color(0x66000000))
                    .padding(14.dp)
            ) {
                Text(
                    ph.takenOn.ifBlank {
                        SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(ph.createdAt))
                    },
                    fontSize = 12.sp, color = Color(0xFFCCCCCC)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    HomecomingAlbumStore.noteOrPlaceholder(ph.note),
                    fontSize = 13.sp, color = Color.White, lineHeight = 19.sp
                )
                Text(
                    "删除这张",
                    fontSize = 13.sp, color = Danger,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .clickable {
                            scope.launch(Dispatchers.IO) {
                                HomecomingAlbumStore.remove(context, ph.id)
                                withContext(Dispatchers.Main) { viewer = null; refresh() }
                            }
                        }
                        .padding(vertical = 2.dp)
                )
            }
        }
    }

    // ── 图册详情 ──
    albumDetail?.let { name ->
        val kind = albums.firstOrNull { it.name == name }?.kind ?: "其他"
        val inAlbum = photos.filter { it.album == name }
        Box(Modifier.fillMaxSize().background(PageBg)) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text("‹", fontSize = 24.sp, color = Ink,
                        modifier = Modifier
                            .clickable { albumDetail = null }
                            .padding(horizontal = 8.dp))
                    Text(name, fontSize = 19.sp, color = Ink, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Text(kind, fontSize = 11.sp, color = Sub,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White)
                            .border(1.dp, Line, RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 2.dp))
                    Spacer(Modifier.weight(1f))
                    Text(
                        "添加", fontSize = 13.sp, color = Accent,
                        modifier = Modifier
                            .clickable {
                                addTarget = name
                                pickImages.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .padding(horizontal = 8.dp)
                    )
                }
                if (inAlbum.isEmpty()) {
                    Text(
                        "图册还是空的，右上角「添加」存几张进来。",
                        fontSize = 13.sp, color = Sub,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp)
                    )
                } else {
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize().padding(4.dp)
                    ) {
                        items(inAlbum, key = { "d_" + it.id }) { ph ->
                            PhotoCell(ph, false, false) { viewer = ph }
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(40.dp)) }
                    }
                }
            }
        }
    }

    // ── 新建图册 ──
    if (showNewAlbum) {
        var name by remember { mutableStateOf("") }
        var kind by remember { mutableStateOf(KINDS.first()) }
        AlertDialog(
            onDismissRequest = { showNewAlbum = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            title = { Text("新建图册", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink) },
            text = {
                Column {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("图册名称", fontSize = 13.sp, color = Sub) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("分类", fontSize = 13.sp, color = Sub)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        KINDS.forEach { k ->
                            val sel = kind == k
                            Text(
                                k, fontSize = 13.sp,
                                color = if (sel) Color.White else Ink,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (sel) Ink else Color(0xFFF2F0EA))
                                    .clickable { kind = k }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (HomecomingAlbumStore.createAlbum(context, name, kind)) {
                        showNewAlbum = false
                        refresh()
                    } else Toast.makeText(context, "名字空着或已存在", Toast.LENGTH_SHORT).show()
                }) { Text("创建", color = Accent, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { showNewAlbum = false }) { Text("取消", color = Sub) } }
        )
    }

    // ── 图册卡片 …菜单 ──
    menuFor?.let { target ->
        AlertDialog(
            onDismissRequest = { menuFor = null },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            title = { Text(target, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink) },
            text = {
                Column {
                    Text(
                        "重命名", fontSize = 14.sp, color = Ink,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { menuFor = null; renameFor = target }
                            .padding(vertical = 10.dp)
                    )
                    Text(
                        "删除图册（照片保留在全部图片）", fontSize = 14.sp, color = Danger,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                HomecomingAlbumStore.deleteAlbum(context, target)
                                menuFor = null
                                refresh()
                            }
                            .padding(vertical = 10.dp)
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { menuFor = null }) { Text("关闭", color = Sub) } }
        )
    }

    // ── 重命名 ──
    renameFor?.let { old ->
        var newName by remember { mutableStateOf(old) }
        AlertDialog(
            onDismissRequest = { renameFor = null },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            title = { Text("重命名图册", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink) },
            text = {
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (HomecomingAlbumStore.renameAlbum(context, old, newName)) {
                        renameFor = null; refresh()
                    } else Toast.makeText(context, "名字空着或已存在", Toast.LENGTH_SHORT).show()
                }) { Text("保存", color = Accent, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { renameFor = null }) { Text("取消", color = Sub) } }
        )
    }

    // ── 编辑（图册管理） ──
    if (showManage) {
        AlertDialog(
            onDismissRequest = { showManage = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            title = { Text("管理图册", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink) },
            text = {
                Column(
                    Modifier
                        .height(300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (albums.isEmpty()) Text("还没有图册", fontSize = 13.sp, color = Sub)
                    albums.forEach { a ->
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(a.name, fontSize = 14.sp, color = Ink)
                                Text(a.kind, fontSize = 10.sp, color = Sub)
                            }
                            Text("重命名", fontSize = 12.sp, color = Accent,
                                modifier = Modifier
                                    .clickable { showManage = false; renameFor = a.name }
                                    .padding(6.dp))
                            Text("删除", fontSize = 12.sp, color = Danger,
                                modifier = Modifier
                                    .clickable {
                                        HomecomingAlbumStore.deleteAlbum(context, a.name)
                                        refresh()
                                    }
                                    .padding(6.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showManage = false; refresh() }) { Text("完成", color = Accent) } },
            dismissButton = {}
        )
    }
}

@Composable
private fun PhotoCell(
    photo: HomecomingAlbumStore.AlbumPhoto,
    selectMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFF0EEE8))
            .clickable { onClick() }
    ) {
        val bmpState = androidx.compose.runtime.produceState<android.graphics.Bitmap?>(
            null, photo.id, photo.filename
        ) {
            withContext(Dispatchers.IO) { value = decode(photo.file(context)) }
        }
        val bmp = bmpState.value
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text("?", fontSize = 14.sp, color = Faint, modifier = Modifier.align(Alignment.Center))
        }
        if (selectMode) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Accent else Color.White.copy(alpha = 0.85f))
                    .border(1.dp, Color.White, CircleShape)
            ) {
                if (isSelected) Text("✓", fontSize = 11.sp, color = Color.White, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun AlbumCard(
    album: HomecomingAlbumStore.Album,
    cover: HomecomingAlbumStore.AlbumPhoto?,
    count: Int,
    showMenu: Boolean,
    onOpen: () -> Unit,
    onMenu: (() -> Unit)? = null
) {
    val context = LocalContext.current
    Column(
        Modifier
            .width(92.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .clickable { onOpen() }
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.92f).background(Color(0xFFF0EEE8))) {
            val bmpState = androidx.compose.runtime.produceState<android.graphics.Bitmap?>(
                null, cover?.id ?: -1L
            ) {
                val c = cover ?: return@produceState
                withContext(Dispatchers.IO) { value = decode(c.file(context)) }
            }
            val bmp = bmpState.value
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    album.name, fontSize = 12.sp, color = Ink, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                if (showMenu) {
                    Text("…", fontSize = 12.sp, color = Sub,
                        modifier = Modifier.clickable { onMenu?.invoke() }.padding(start = 2.dp))
                }
            }
            Text("$count 张照片", fontSize = 10.sp, color = Sub, modifier = Modifier.padding(top = 1.dp))
        }
    }
}

private fun decode(f: File) = runCatching {
    if (!f.exists()) return@runCatching null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.absolutePath, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 512) sample *= 2
    BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()
