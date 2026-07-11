package com.example.data.parser

import com.example.data.model.EPGProgram
import java.util.*

object EpgParser {
    // Generate simulated EPG programs for any channel name to provide instant visual delight
    fun generateSimulatedEpg(channelName: String): List<EPGProgram> {
        val programs = mutableListOf<EPGProgram>()
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dayStart = calendar.timeInMillis

        // Let's divide 24 hours into blocks
        val schedules = listOf(
            ScheduleTemplate("00:00", "02:00", "深夜经典电影点播", "播放中外经典优秀故事片"),
            ScheduleTemplate("02:00", "06:00", "精彩体育赛事重播", "重温昨日精彩赛事瞬间"),
            ScheduleTemplate("06:00", "08:00", "晨光新闻与天气", "最及时的早间新闻报道，天气早知道"),
            ScheduleTemplate("08:00", "09:30", "热播电视剧第一集", "黄金档电视剧精彩重播"),
            ScheduleTemplate("09:30", "12:00", "纪录片精选：华夏地理", "探索祖国大好河山与人文历史地理"),
            ScheduleTemplate("12:00", "12:30", "正午新闻30分", "最新国内外大事、社会热点追踪报道"),
            ScheduleTemplate("12:30", "14:00", "午间情感剧场", "都市温情生活剧精选"),
            ScheduleTemplate("14:00", "16:00", "综艺大舞台", "精彩趣味互动，合家欢搞笑视频"),
            ScheduleTemplate("16:00", "18:00", "少儿动画世界", "陪伴孩子们开心度过下午茶时光"),
            ScheduleTemplate("18:00", "19:00", "财经前沿观察", "解析今日股市走势，聚焦经济前沿热点"),
            ScheduleTemplate("19:00", "19:30", "每日焦点新闻联播", "权威、全面、准确的信息直达"),
            ScheduleTemplate("19:30", "22:00", "超清首播：黄金档王牌剧场", "热播好剧震撼上映，精彩不容错过"),
            ScheduleTemplate("22:00", "23:30", "国际时事深度访谈", "专家面对面，聚焦国际局势风云变幻"),
            ScheduleTemplate("23:30", "24:00", "深夜音乐电台 / 晚安曲", "在温暖的旋律中进入梦乡")
        )

        for (item in schedules) {
            val startParts = item.start.split(":")
            val endParts = item.end.split(":")

            val startCal = Calendar.getInstance().apply {
                timeInMillis = dayStart
                set(Calendar.HOUR_OF_DAY, startParts[0].toInt())
                set(Calendar.MINUTE, startParts[1].toInt())
            }

            val endCal = Calendar.getInstance().apply {
                timeInMillis = dayStart
                if (endParts[0] == "24") {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                } else {
                    set(Calendar.HOUR_OF_DAY, endParts[0].toInt())
                    set(Calendar.MINUTE, endParts[1].toInt())
                }
            }

            // Append channel identifier in title if CCTV
            val title = if (channelName.contains("CCTV")) {
                item.title.replace("电视剧", "央视剧场").replace("新闻", "央视新闻")
            } else {
                item.title
            }

            programs.add(
                EPGProgram(
                    channelName = channelName,
                    title = title,
                    startTime = startCal.timeInMillis,
                    endTime = endCal.timeInMillis,
                    description = item.description
                )
            )
        }

        return programs
    }

    private data class ScheduleTemplate(
        val start: String,
        val end: String,
        val title: String,
        val description: String
    )
}
