package com.walkman.tv.di

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.File

/**
 * Tiny in-app HTTP server that exposes mobile-friendly forms over the LAN so the user's phone
 * can push input into the TV (search keyword, custom-source script URL, or uploaded .js file).
 * The TV side advertises the URL via a QR dialog.
 *
 * Routes (GET = HTML form, POST = receive payload):
 *   GET  /search             - search input form
 *   POST /api/search         - field "q"    -> events.qrSearchKeyword
 *   GET  /script             - script-import form (URL + file upload)
 *   POST /api/script-url     - field "url"  -> events.qrScriptUrl
 *   POST /api/script-file    - multipart field "file" -> events.qrScriptText
 *   GET  /playlist-name      - playlist name input form (Chinese-friendly via phone IME)
 *   POST /api/playlist-name  - field "name" -> events.qrPlaylistName
 *   GET  /songlist-url       - songlist URL paste form (avoids TV-side IME entirely)
 *   POST /api/songlist-url   - field "url"  -> events.qrSonglistUrl
 */
class LocalServer private constructor(
    private val events: AppEvents,
    // Returns hot-search columns as a JSON string: [{"source":"kw","name":"酷我","words":[...]}].
    // Supplied by AppContainer; called on NanoHTTPD's worker thread (blocking is fine here).
    private val hotSearchJson: () -> String,
    port: Int,
) : NanoHTTPD(port) {

    /** Port the server is bound to. */
    val boundPort: Int get() = listeningPort

    private fun tryStart(): Boolean = runCatching {
        start(SOCKET_READ_TIMEOUT, false)
        Log.i(TAG, "LocalServer up on port $boundPort")
        true
    }.getOrElse {
        Log.w(TAG, "port $listeningPort failed: ${it.message}")
        false
    }

    override fun serve(session: IHTTPSession): Response {
        return try {
            when (session.method) {
                Method.GET -> handleGet(session)
                Method.POST -> handlePost(session)
                else -> notFound()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "serve error", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message ?: "error")
        }
    }

    private fun handleGet(session: IHTTPSession): Response = when (session.uri.trimEnd('/')) {
        "/api/hotsearch" -> json(runCatching { hotSearchJson() }.getOrDefault("[]"))
        "/search" -> html(SEARCH_HTML)
        "/script" -> html(SCRIPT_HTML)
        "/playlist-name" -> html(PLAYLIST_NAME_HTML)
        "/songlist-url" -> html(SONGLIST_URL_HTML)
        "" -> html(INDEX_HTML)
        else -> notFound()
    }

    private fun handlePost(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files) // populates params for url-encoded; stores tmp paths for multipart
        val params = session.parameters
        return when (session.uri.trimEnd('/')) {
            "/api/search" -> {
                val q = params["q"]?.firstOrNull().orEmpty().trim()
                if (q.isNotEmpty()) events.postQrSearchKeyword(q)
                html(donePage("已发送到电视：$q"))
            }
            "/api/script-url" -> {
                val url = params["url"]?.firstOrNull().orEmpty().trim()
                if (url.isNotEmpty()) events.postQrScriptUrl(url)
                html(donePage("URL 已发送：$url"))
            }
            "/api/script-file" -> {
                val tmpPath = files["file"]
                val text = tmpPath?.let { runCatching { File(it).readText() }.getOrNull() }
                if (!text.isNullOrEmpty()) {
                    events.postQrScriptText(text)
                    html(donePage("已上传 ${text.length} 字符的脚本"))
                } else {
                    html(donePage("未收到文件"))
                }
            }
            "/api/playlist-name" -> {
                val name = params["name"]?.firstOrNull().orEmpty().trim()
                if (name.isNotEmpty()) events.postQrPlaylistName(name)
                html(donePage("已发送：$name"))
            }
            "/api/songlist-url" -> {
                val url = params["url"]?.firstOrNull().orEmpty().trim()
                if (url.isNotEmpty()) events.postQrSonglistUrl(url)
                html(donePage("已发送歌单链接"))
            }
            else -> notFound()
        }
    }

    private fun html(body: String): Response =
        newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", body)

    private fun json(body: String): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", body)

    private fun notFound(): Response =
        newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")

    companion object {
        private const val TAG = "LocalServer"

        /** Start the local server, trying a small range of ports in case 8765 is busy. */
        fun start(events: AppEvents, hotSearchJson: () -> String = { "[]" }): LocalServer? {
            for (port in listOf(8765, 8766, 8767, 8768)) {
                val server = LocalServer(events, hotSearchJson, port)
                if (server.tryStart()) return server
            }
            return null
        }

        private const val PAGE_CSS = """
            <style>
              body{margin:0;padding:24px;font-family:-apple-system,system-ui,sans-serif;
                   background:#0A0D14;color:#fff;}
              h2{margin:0 0 16px;font-weight:700;color:#4ADE80;}
              p{color:#a0a0b0;margin:0 0 18px;line-height:1.5;font-size:14px;}
              .card{background:#14171F;border-radius:12px;padding:18px;margin-bottom:14px;}
              input,textarea{width:100%;padding:13px;font-size:16px;border-radius:8px;
                             border:1px solid #2A2D38;background:#0A0D14;color:#fff;
                             box-sizing:border-box;margin-bottom:10px;}
              input[type=file]{padding:8px;}
              button{width:100%;padding:14px;background:#4ADE80;color:#0A0D14;border:0;
                     border-radius:8px;font-size:16px;font-weight:700;cursor:pointer;}
              .ok{color:#4ADE80;font-size:18px;font-weight:700;}
              a{color:#4ADE80;text-decoration:none;}
            </style>
        """

        private val INDEX_HTML = """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>随便听 - 手机助手</title>$PAGE_CSS</head>
            <body>
              <h2>随便听</h2>
              <p>选择要在电视上做的事：</p>
              <div class="card"><a href="/search"><h2>🔍 搜索歌曲</h2></a></div>
              <div class="card"><a href="/script"><h2>📜 导入自定义音源</h2></a></div>
              <div class="card"><a href="/playlist-name"><h2>✏️ 输入歌单名</h2></a></div>
              <div class="card"><a href="/songlist-url"><h2>🔗 推送歌单链接</h2></a></div>
            </body></html>
        """.trimIndent()

        private val SEARCH_HTML = """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>搜索 · 随便听</title>$PAGE_CSS
            <style>
              .toast{position:fixed;left:50%;top:24px;transform:translateX(-50%);
                     background:#4ADE80;color:#0A0D14;font-weight:700;font-size:15px;
                     padding:12px 22px;border-radius:24px;opacity:0;transition:opacity .25s;
                     pointer-events:none;z-index:9;box-shadow:0 6px 20px rgba(0,0,0,.4);}
              .toast.show{opacity:1;}
              .hotgrp{margin-bottom:16px;}
              .hottitle{font-weight:700;font-size:15px;margin:0 0 10px;}
              .hotwords{display:flex;flex-wrap:wrap;gap:8px;}
              .hotword{display:inline-flex;align-items:baseline;gap:6px;padding:8px 12px;
                       border-radius:18px;cursor:pointer;font-size:14px;color:#fff;
                       background:#0A0D14;border:1px solid #2A2D38;max-width:100%;}
              .hotword:active{background:#1c2029;}
              .hotword span.w{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}
              .rank{font-weight:700;font-size:12px;color:#666;flex:none;}
              .rank.top{color:inherit;}
            </style></head>
            <body>
              <div id="toast" class="toast"></div>
              <h2>搜索歌曲</h2>
              <p>输入关键词点发送，电视会自动开始搜索，发送后可继续搜下一个。也可点下方热搜词。</p>
              <div class="card">
                <input id="q" placeholder="歌曲名 / 歌手 / 歌单" autofocus>
                <button onclick="send()">发送到电视</button>
              </div>
              <div id="hot"></div>
              <script>
                var TINT={kw:'#F26D4D',kg:'#1AB0F0',tx:'#33CC8C',wy:'#F24552'};
                function toast(msg){
                  var t=document.getElementById('toast');
                  t.textContent=msg; t.className='toast show';
                  setTimeout(function(){t.className='toast';},1600);
                }
                function send(){
                  var q=document.getElementById('q').value.trim();
                  if(!q) return;
                  var body='q='+encodeURIComponent(q);
                  fetch('/api/search',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:body})
                    .then(function(){toast('已发送到电视：'+q);})
                    .catch(function(){toast('发送失败，请重试');});
                }
                function pick(w){
                  document.getElementById('q').value=w;
                  send();
                }
                document.getElementById('q').addEventListener('keydown',function(e){
                  if(e.key==='Enter') send();
                });
                fetch('/api/hotsearch').then(function(r){return r.json();}).then(function(cols){
                  var wrap=document.getElementById('hot');
                  if(!cols||!cols.length) return;
                  var h='<div class="card"><h2 style="margin-bottom:12px">热门搜索</h2>';
                  cols.forEach(function(c){
                    if(!c.words||!c.words.length) return;
                    var tint=TINT[c.source]||'#4ADE80';
                    h+='<div class="hotgrp"><div class="hottitle" style="color:'+tint+'">'+c.name+'</div><div class="hotwords">';
                    c.words.forEach(function(w,i){
                      var rankStyle=i<3?'color:'+tint:'';
                      h+='<div class="hotword" onclick="pick(this.dataset.w)" data-w="'+w.replace(/"/g,'&quot;')+'">'
                        +'<span class="rank'+(i<3?' top':'')+'" style="'+rankStyle+'">'+(i+1)+'</span>'
                        +'<span class="w">'+w+'</span></div>';
                    });
                    h+='</div></div>';
                  });
                  h+='</div>';
                  wrap.innerHTML=h;
                });
              </script>
            </body></html>
        """.trimIndent()

        private val PLAYLIST_NAME_HTML = """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>歌单名 · 随便听</title>$PAGE_CSS</head>
            <body>
              <h2>给歌单起个名字</h2>
              <p>用手机输入法（中英文都行），提交后名字会出现在电视的对话框里。</p>
              <form class="card" method="POST" action="/api/playlist-name">
                <input name="name" placeholder="例如：开车听的歌" maxlength="24" autofocus>
                <button>发送到电视</button>
              </form>
            </body></html>
        """.trimIndent()

        private val SONGLIST_URL_HTML = """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>推送歌单 · 随便听</title>$PAGE_CSS</head>
            <body>
              <h2>推送歌单链接</h2>
              <p>从酷我 / 酷狗 / QQ 音乐 / 网易云分享一个歌单链接（或纯数字 ID），粘进来发送给电视，电视会自动识别并导入。</p>
              <form class="card" method="POST" action="/api/songlist-url">
                <textarea name="url" placeholder="例如 https://music.163.com/playlist?id=2034742057" rows="3" autofocus></textarea>
                <button>发送到电视</button>
              </form>
            </body></html>
        """.trimIndent()

        private val SCRIPT_HTML = """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>自定义音源 · 随便听</title>$PAGE_CSS</head>
            <body>
              <h2>导入自定义音源</h2>
              <p>支持两种方式：粘贴脚本 URL，或直接上传 .js 文件。提交后电视会自动加载。</p>
              <form class="card" method="POST" action="/api/script-url">
                <input name="url" placeholder="脚本 URL（例如 https://.../source.js）">
                <button>发送 URL</button>
              </form>
              <form class="card" method="POST" action="/api/script-file" enctype="multipart/form-data">
                <input type="file" name="file">
                <button>上传脚本文件</button>
              </form>
            </body></html>
        """.trimIndent()

        private fun donePage(message: String): String = """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>已发送 · 随便听</title>$PAGE_CSS</head>
            <body>
              <div class="card" style="text-align:center;padding:32px 18px;">
                <div class="ok">✓ ${escapeHtml(message)}</div>
                <p style="margin-top:18px;">可以回到电视查看结果。</p>
                <p><a href="/">返回首页</a></p>
              </div>
            </body></html>
        """.trimIndent()

        private fun escapeHtml(s: String): String = s
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;")
    }
}
