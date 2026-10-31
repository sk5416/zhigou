package com.sk5416.zhigou

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.*
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.sk5416.zhigou.data.*
import java.io.ByteArrayInputStream

@OptIn(ExperimentalLayoutApi::class)
@Composable fun Reader(c: Chapter, anchor: String, state: LearningState, dark: Boolean, vm: LearningViewModel, open: (String, String) -> Unit, practice: () -> Unit) {
    val r = state.readings.find { it.chapterId == c.id } ?: Reading(c.id)
    LaunchedEffect(c.id) { vm.read(c.id) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton({ vm.read(c.id, completed = !r.completed) }) { Text(if (r.completed) "已读 ✓" else "标记已读") }
            TextButton({ vm.read(c.id, bookmark = !r.bookmarked) }) { Text(if (r.bookmarked) "已收藏 ★" else "收藏") }
            if (c.id.startsWith("C")) TextButton(practice) { Text("本章练习") }
        }
        key(c.id, anchor, dark, state.preferences.font) {
            val initialScroll = remember { r.scroll }
            var view by remember { mutableStateOf<WebView?>(null) }
            var ready by remember { mutableStateOf(false) }
            var lastSaved by remember { mutableLongStateOf(0L) }
            val latestRead by rememberUpdatedState(vm)
            AndroidView(modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds(), factory = { context ->
                val loader = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context)).build()
                WebView(context).apply {
                    view = this
                    // Static documents do not need GPU effects. Software drawing avoids
                    // blank Chromium layers when embedded in Compose on older WebViews.
                    setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                    clipToOutline = true
                    settings.javaScriptEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.blockNetworkLoads = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.defaultTextEncodingName = "UTF-8"
                    settings.textZoom = state.preferences.font * 100 / 18
                    setBackgroundColor(if (dark) 0xFF151C19.toInt() else 0xFFF7F8F3.toInt())
                    val html = context.assets.open(c.html).bufferedReader().use { it.readText() }
                    val colors = if (dark) "body{background:#151c19;color:#e0e9e2}a{color:#8bd6bb}pre,blockquote{background:#23332c}th,td{border-color:#617168}" else ""
                    val rendered = html.replace("</head>", "<style>$colors</style></head>").toByteArray(Charsets.UTF_8)
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse {
                            if (request.url.host == "appassets.androidplatform.net" && request.url.path == "/assets/${c.html}") {
                                return WebResourceResponse("text/html", "UTF-8", ByteArrayInputStream(rendered))
                            }
                            return loader.shouldInterceptRequest(request.url) ?: WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                        }
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val uri = request.url
                            if (uri.scheme == "zhigou" && uri.host == "chapter") {
                                uri.lastPathSegment?.let { id -> if (state.catalog!!.chapters.any { it.id == id && it.published }) open(id, uri.fragment.orEmpty()) }
                                return true
                            }
                            return uri.host != "appassets.androidplatform.net" || uri.path != "/assets/${c.html}"
                        }
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) { ready = false }
                        override fun onPageFinished(web: WebView, url: String?) {
                            web.postDelayed({ if (anchor.isBlank()) web.scrollTo(0, initialScroll); ready = true }, 150)
                        }
                    }
                    loadUrl("https://appassets.androidplatform.net/assets/${c.html}${if (anchor.isNotBlank()) "#${Uri.encode(anchor)}" else ""}")
                    setOnScrollChangeListener { _, _, y, _, _ ->
                        val now = System.currentTimeMillis()
                        if (ready && now - lastSaved > 600) { lastSaved = now; latestRead.read(c.id, scroll = y) }
                    }
                }
            })
            DisposableEffect(Unit) { onDispose { view?.let { web -> if (ready) vm.read(c.id, scroll = web.scrollY); web.stopLoading(); web.destroy() } } }
        }
        val chapters = state.catalog!!.chapters.filter { it.published }
        val i = chapters.indexOfFirst { it.id == c.id }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton({ open(chapters[i - 1].id, "") }, enabled = i > 0) { Text("上一章") }
            TextButton({ open(chapters[i + 1].id, "") }, enabled = i >= 0 && i < chapters.lastIndex) { Text("下一章") }
        }
        val links = (c.prerequisites + c.related).distinct().filter { id -> chapters.any { it.id == id } }
        if (links.isNotEmpty()) FlowRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Text("关联", Modifier.padding(top = 12.dp))
            links.forEach { id -> TextButton({ open(id, "") }) { Text(id) } }
        }
    }
}
