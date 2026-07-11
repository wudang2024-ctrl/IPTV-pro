package com.example.data.server

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import kotlin.concurrent.thread

class RemotePushServer(
    private val context: Context,
    private val port: Int,
    private val onPlaylistPushed: (name: String, url: String, content: String) -> Unit,
    private val onDirectPlayPushed: (url: String) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        thread(start = true, name = "RemotePushServerThread") {
            try {
                serverSocket = ServerSocket(port)
                Log.d("RemotePushServer", "Server started on port $port")
                while (isRunning) {
                    val socket = serverSocket?.accept() ?: break
                    handleClient(socket)
                }
            } catch (e: Exception) {
                Log.e("RemotePushServer", "Error in server loop: ${e.message}")
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serverSocket = null
    }

    fun getDeviceIpAddress(): String {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ipAddress = wifiManager.connectionInfo.ipAddress
            Formatter.formatIpAddress(ipAddress)
        } catch (e: Exception) {
            "127.0.0.1"
        }
    }

    private fun handleClient(socket: Socket) {
        thread {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val output = socket.getOutputStream()

                val firstLine = reader.readLine() ?: return@thread
                val parts = firstLine.split(" ")
                if (parts.size < 2) return@thread
                val method = parts[0]
                val path = parts[1]

                // Read headers to find Content-Length for POST
                var contentLength = 0
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line!!.trim().isEmpty()) break
                    if (line!!.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = line!!.substringAfter(":").trim().toInt()
                    }
                }

                if (method == "POST") {
                    val bodyBuffer = CharArray(contentLength)
                    var read = 0
                    while (read < contentLength) {
                        val result = reader.read(bodyBuffer, read, contentLength - read)
                        if (result == -1) break
                        read += result
                    }
                    val body = String(bodyBuffer)

                    if (path == "/push-playlist") {
                        handlePushPlaylist(body, output)
                    } else if (path == "/push-play") {
                        handlePushPlay(body, output)
                    } else {
                        sendResponse(output, 404, "text/plain", "Not Found")
                    }
                } else {
                    // Serve static landing page
                    if (path == "/" || path == "/index.html") {
                        serveLandingPage(output)
                    } else {
                        sendResponse(output, 404, "text/plain", "Not Found")
                    }
                }
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun serveLandingPage(output: OutputStream) {
        val html = """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>星辰IPTV 远程管理助手</title>
                <style>
                    body {
                        font-family: 'SF Pro Display', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                        background: linear-gradient(135deg, #12131C 0%, #1A1C29 100%);
                        color: #E2E8F0;
                        margin: 0;
                        padding: 0;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        min-height: 100vh;
                    }
                    .container {
                        background: rgba(30, 41, 59, 0.7);
                        backdrop-filter: blur(16px);
                        border: 1px solid rgba(255, 255, 255, 0.1);
                        border-radius: 24px;
                        padding: 40px;
                        box-shadow: 0 20px 40px rgba(0, 0, 0, 0.4);
                        max-width: 500px;
                        width: 90%;
                    }
                    h1 {
                        font-size: 28px;
                        margin-bottom: 8px;
                        text-align: center;
                        color: #38BDF8;
                        font-weight: 700;
                    }
                    p.subtitle {
                        text-align: center;
                        color: #94A3B8;
                        margin-bottom: 30px;
                        font-size: 14px;
                    }
                    .section {
                        margin-bottom: 25px;
                        background: rgba(15, 23, 42, 0.4);
                        padding: 20px;
                        border-radius: 16px;
                        border: 1px solid rgba(255, 255, 255, 0.05);
                    }
                    .section-title {
                        font-size: 16px;
                        font-weight: 600;
                        margin-bottom: 15px;
                        color: #F1F5F9;
                        border-left: 4px solid #38BDF8;
                        padding-left: 10px;
                    }
                    label {
                        display: block;
                        font-size: 13px;
                        color: #94A3B8;
                        margin-bottom: 6px;
                    }
                    input[type="text"], textarea {
                        width: 100%;
                        background: #0F172A;
                        border: 1px solid rgba(255, 255, 255, 0.15);
                        border-radius: 10px;
                        padding: 12px;
                        color: #F8FAFC;
                        font-size: 14px;
                        box-sizing: border-box;
                        margin-bottom: 12px;
                        transition: all 0.3s ease;
                    }
                    input[type="text"]:focus, textarea:focus {
                        border-color: #38BDF8;
                        outline: none;
                        box-shadow: 0 0 0 3px rgba(56, 189, 248, 0.2);
                    }
                    button {
                        width: 100%;
                        background: linear-gradient(135deg, #0284C7 0%, #0369A1 100%);
                        color: white;
                        border: none;
                        border-radius: 10px;
                        padding: 14px;
                        font-size: 15px;
                        font-weight: 600;
                        cursor: pointer;
                        transition: all 0.2s ease;
                    }
                    button:hover {
                        transform: translateY(-2px);
                        box-shadow: 0 4px 12px rgba(2, 132, 199, 0.4);
                    }
                    button:active {
                        transform: translateY(0);
                    }
                    .footer {
                        text-align: center;
                        margin-top: 30px;
                        font-size: 12px;
                        color: #64748B;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>星辰IPTV 远程推流助手</h1>
                    <p class="subtitle">无需在电视输入繁琐链接，一键将直播源推送到您的电视</p>
                    
                    <div class="section">
                        <div class="section-title">导入M3U直播源</div>
                        <form action="/push-playlist" method="POST">
                            <label for="playlistName">播放列表名称</label>
                            <input type="text" id="playlistName" name="name" placeholder="例如：我的超清频道" required>
                            
                            <label for="playlistUrl">M3U链接 (URL)</label>
                            <input type="text" id="playlistUrl" name="url" placeholder="http://example.com/live.m3u">
                            
                            <label for="playlistContent">或者直接粘贴 M3U 文本内容</label>
                            <textarea id="playlistContent" name="content" rows="6" placeholder="#EXTM3U&#10;#EXTINF:-1,CCTV1&#10;http://..."></textarea>
                            
                            <button type="submit">推送并导入播放列表</button>
                        </form>
                    </div>

                    <div class="section">
                        <div class="section-title">远程一键投屏播放</div>
                        <form action="/push-play" method="POST">
                            <label for="playUrl">直播流 URL / 直播视频链接</label>
                            <input type="text" id="playUrl" name="url" placeholder="http://...m3u8, .mp4, .mkv 等" required>
                            
                            <button type="submit" style="background: linear-gradient(135deg, #10B981 0%, #059669 100%);">立即在电视播放</button>
                        </form>
                    </div>

                    <div class="footer">
                        Powered by 星辰IPTV | 确保手机/电脑与电视在同一局域网下
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
        sendResponse(output, 200, "text/html; charset=utf-8", html)
    }

    private fun handlePushPlaylist(body: String, output: OutputStream) {
        try {
            val params = parseFormBody(body)
            val name = params["name"] ?: "远程推送"
            val url = params["url"] ?: ""
            val content = params["content"] ?: ""

            onPlaylistPushed(name, url, content)

            val successHtml = """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                    <meta charset="UTF-8">
                    <title>推送成功</title>
                    <style>
                        body { background: #12131C; color: #E2E8F0; text-align: center; padding-top: 100px; font-family: sans-serif; }
                        .card { background: #1E293B; border-radius: 16px; display: inline-block; padding: 40px; box-shadow: 0 10px 30px rgba(0,0,0,0.5); }
                        h1 { color: #10B981; }
                        a { color: #38BDF8; text-decoration: none; display: inline-block; margin-top: 20px; }
                    </style>
                </head>
                <body>
                    <div class="card">
                        <h1>✓ 推送成功！</h1>
                        <p>播放列表已成功推送到电视播放器中，请在电视端查看。</p>
                        <a href="/">返回主页</a>
                    </div>
                </body>
                </html>
            """.trimIndent()
            sendResponse(output, 200, "text/html; charset=utf-8", successHtml)
        } catch (e: Exception) {
            e.printStackTrace()
            sendResponse(output, 500, "text/plain", "Error: ${e.message}")
        }
    }

    private fun handlePushPlay(body: String, output: OutputStream) {
        try {
            val params = parseFormBody(body)
            val url = params["url"] ?: ""

            if (url.isNotEmpty()) {
                onDirectPlayPushed(url)
            }

            val successHtml = """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                    <meta charset="UTF-8">
                    <title>播放指令已发送</title>
                    <style>
                        body { background: #12131C; color: #E2E8F0; text-align: center; padding-top: 100px; font-family: sans-serif; }
                        .card { background: #1E293B; border-radius: 16px; display: inline-block; padding: 40px; box-shadow: 0 10px 30px rgba(0,0,0,0.5); }
                        h1 { color: #10B981; }
                        a { color: #38BDF8; text-decoration: none; display: inline-block; margin-top: 20px; }
                    </style>
                </head>
                <body>
                    <div class="card">
                        <h1>✓ 投屏成功！</h1>
                        <p>播放指令已成功发送，电视机正为您启动播放直播流：</p>
                        <code style="background:#0F172A; padding: 8px 12px; border-radius: 6px; display:block; color:#E2E8F0; margin:15px 0;">$url</code>
                        <a href="/">返回主页</a>
                    </div>
                </body>
                </html>
            """.trimIndent()
            sendResponse(output, 200, "text/html; charset=utf-8", successHtml)
        } catch (e: Exception) {
            e.printStackTrace()
            sendResponse(output, 500, "text/plain", "Error: ${e.message}")
        }
    }

    private fun parseFormBody(body: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val pairs = body.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            if (idx != -1) {
                val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                result[key] = value
            }
        }
        return result
    }

    private fun sendResponse(output: OutputStream, statusCode: Int, contentType: String, content: String) {
        val bytes = content.toByteArray()
        val headers = """
            HTTP/1.1 $statusCode OK
            Content-Type: $contentType
            Content-Length: ${bytes.size}
            Connection: close
            Access-Control-Allow-Origin: *
            
        """.trimIndent() + "\r\n"
        output.write(headers.toByteArray())
        output.write(bytes)
        output.flush()
    }
}
