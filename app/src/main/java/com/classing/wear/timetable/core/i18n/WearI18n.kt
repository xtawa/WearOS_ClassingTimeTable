package com.classing.wear.timetable.core.i18n

import java.util.Locale

object WearI18n {
    private enum class Lang { ZH_CN, ZH_TW, EN }

    private fun currentLang(): Lang {
        val locale = Locale.getDefault()
        val language = locale.language.lowercase(Locale.ROOT)
        val script = locale.script.lowercase(Locale.ROOT)
        val country = locale.country.uppercase(Locale.ROOT)

        return when {
            language == "zh" && (script == "hant" || country == "TW" || country == "HK" || country == "MO") -> Lang.ZH_TW
            language == "zh" -> Lang.ZH_CN
            else -> Lang.EN
        }
    }

    fun weekLabel(index: Int): String {
        return when (currentLang()) {
            Lang.ZH_CN -> "第${index}周"
            Lang.ZH_TW -> "第${index}週"
            Lang.EN -> "Week $index"
        }
    }

    fun semesterNotSet(): String {
        return when (currentLang()) {
            Lang.ZH_CN -> "未设置学期"
            Lang.ZH_TW -> "未設定學期"
            Lang.EN -> "Semester not set"
        }
    }

    fun syncNever(): String {
        return when (currentLang()) {
            Lang.ZH_CN -> "尚未同步"
            Lang.ZH_TW -> "尚未同步"
            Lang.EN -> "Never synced"
        }
    }

    fun courseNotFound(): String {
        return when (currentLang()) {
            Lang.ZH_CN -> "课程不存在"
            Lang.ZH_TW -> "課程不存在"
            Lang.EN -> "Course not found"
        }
    }

    fun countdownSoon(): String {
        return when (currentLang()) {
            Lang.ZH_CN -> "即将开始"
            Lang.ZH_TW -> "即將開始"
            Lang.EN -> "Starting soon"
        }
    }

    fun countdownInHoursAndMinutes(hours: Long, minutes: Long): String {
        return when (currentLang()) {
            Lang.ZH_CN -> "${hours}小时${minutes}分后"
            Lang.ZH_TW -> "${hours}小時${minutes}分後"
            Lang.EN -> "In ${hours}h ${minutes}m"
        }
    }

    fun countdownInMinutes(minutes: Long): String {
        return when (currentLang()) {
            Lang.ZH_CN -> "${minutes}分钟后"
            Lang.ZH_TW -> "${minutes}分鐘後"
            Lang.EN -> "In ${minutes} min"
        }
    }

    fun tilePlaceholderTitle(): String {
        return when (currentLang()) {
            Lang.ZH_CN -> "下一节课"
            Lang.ZH_TW -> "下一節課"
            Lang.EN -> "Next Class"
        }
    }

    fun tilePlaceholderSubtitle(): String {
        return when (currentLang()) {
            Lang.ZH_CN -> "接入 Wear Tiles API 后显示实时数据"
            Lang.ZH_TW -> "接入 Wear Tiles API 後顯示即時資料"
            Lang.EN -> "Show live data after integrating Wear Tiles API"
        }
    }

    fun tileNoClassTitle(): String {
        return when (currentLang()) {
            Lang.ZH_CN -> "今日无后续课程"
            Lang.ZH_TW -> "今日無後續課程"
            Lang.EN -> "No more classes"
        }
    }

    fun tileNoClassSubtitle(): String {
        return when (currentLang()) {
            Lang.ZH_CN -> "请稍后同步或查看下周课表"
            Lang.ZH_TW -> "請稍後同步或查看下週課表"
            Lang.EN -> "Sync later or check weekly schedule"
        }
    }

    fun locationUnknown(): String {
        return when (currentLang()) {
            Lang.ZH_CN -> "地点待定"
            Lang.ZH_TW -> "地點待定"
            Lang.EN -> "Location TBD"
        }
    }

    fun complicationNextClassTitle(): String {
        return when (currentLang()) {
            Lang.ZH_CN -> "下节课"
            Lang.ZH_TW -> "下節課"
            Lang.EN -> "Next class"
        }
    }

    fun complicationNoClassLongText(): String {
        return when (currentLang()) {
            Lang.ZH_CN -> "暂无后续课程"
            Lang.ZH_TW -> "暫無後續課程"
            Lang.EN -> "No upcoming class"
        }
    }

    fun complicationNoClassShortText(): String {
        return when (currentLang()) {
            Lang.ZH_CN -> "无课"
            Lang.ZH_TW -> "無課"
            Lang.EN -> "No class"
        }
    }

    fun complicationPlaceholderLongText(): String {
        return when (currentLang()) {
            Lang.ZH_CN -> "接入 Complication API 后显示倒计时"
            Lang.ZH_TW -> "接入 Complication API 後顯示倒數"
            Lang.EN -> "Show countdown after integrating Complication API"
        }
    }
}
