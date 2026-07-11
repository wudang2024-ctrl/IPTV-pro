package com.example.data.parser

import com.example.data.model.Channel
import java.io.BufferedReader
import java.io.StringReader

object M3uParser {
    fun parse(m3uContent: String, playlistId: Long): List<Channel> {
        val channels = mutableListOf<Channel>()
        
        // Detect if it is a TXT playlist (common for PHP subscriptions) or standard M3U
        val lines = m3uContent.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val isM3u = lines.any { it.startsWith("#EXTM3U") || it.startsWith("#EXTINF:") }
        
        if (isM3u) {
            val reader = BufferedReader(StringReader(m3uContent))
            var line: String?
            var currentName = ""
            var currentLogoUrl = ""
            var currentCategory = "其他"

            try {
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line!!.trim()
                    if (trimmed.isEmpty()) continue

                    if (trimmed.startsWith("#EXTINF:")) {
                        // Extract info
                        currentName = parseAttribute(trimmed, "tvg-name")
                        if (currentName.isEmpty()) {
                            currentName = parseAttribute(trimmed, "name")
                        }
                        val displayName = trimmed.substringAfterLast(",").trim()
                        if (currentName.isEmpty()) {
                            currentName = displayName
                        }
                        if (currentName.isEmpty()) {
                            currentName = "未知频道"
                        }

                        currentLogoUrl = parseAttribute(trimmed, "tvg-logo")
                        if (currentLogoUrl.isEmpty()) {
                            currentLogoUrl = parseAttribute(trimmed, "logo")
                        }

                        currentCategory = parseAttribute(trimmed, "group-title")
                        if (currentCategory.isEmpty()) {
                            currentCategory = "其他"
                        }
                    } else if (!trimmed.startsWith("#")) {
                        // This is the URL line
                        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("rtmp://") || trimmed.startsWith("rtsp://") || trimmed.startsWith("p2p://") || trimmed.isNotEmpty()) {
                            channels.add(
                                Channel(
                                    playlistId = playlistId,
                                    name = if (currentName.isNotEmpty()) currentName else "频道 ${channels.size + 1}",
                                    logoUrl = currentLogoUrl,
                                    streamUrl = trimmed,
                                    category = currentCategory,
                                    isFavorite = false
                                )
                            )
                            // Reset temporary storage
                            currentName = ""
                            currentLogoUrl = ""
                            currentCategory = "其他"
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                reader.close()
            }
        } else {
            // Parse TXT style subscription (common for PHP dynamic source/subscription format)
            var currentCategory = "其他"
            for (line in lines) {
                if (line.contains("#genre#")) {
                    currentCategory = line.substringBefore("#genre#").trim()
                    continue
                }
                if (line.contains(",")) {
                    val parts = line.split(",", limit = 2)
                    if (parts.size == 2) {
                        val name = parts[0].trim()
                        val urlPart = parts[1].trim()
                        // Parse potential multi-source separated by '#' or '$'
                        val urls = urlPart.split(Regex("[#$]")).map { it.trim() }.filter { it.isNotEmpty() }
                        for ((index, url) in urls.withIndex()) {
                            if (url.startsWith("http") || url.startsWith("rtmp") || url.startsWith("rtsp") || url.isNotEmpty()) {
                                val channelName = if (urls.size > 1) "$name #${index + 1}" else name
                                channels.add(
                                    Channel(
                                        playlistId = playlistId,
                                        name = channelName,
                                        logoUrl = "",
                                        streamUrl = url,
                                        category = currentCategory,
                                        isFavorite = false
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        return channels
    }

    private fun parseAttribute(line: String, attrName: String): String {
        val keys = listOf("$attrName=\"", "$attrName=")
        for (key in keys) {
            val index = line.indexOf(key)
            if (index != -1) {
                val start = index + key.length
                val quote = key.endsWith("\"")
                if (quote) {
                    val end = line.indexOf("\"", start)
                    if (end != -1) {
                        return line.substring(start, end)
                    }
                } else {
                    var end = line.indexOf(" ", start)
                    if (end == -1) end = line.indexOf(",", start)
                    if (end != -1) {
                        return line.substring(start, end)
                    }
                }
            }
        }
        return ""
    }
}
