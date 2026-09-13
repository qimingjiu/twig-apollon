package com.selini.aitoolbox.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.BatteryManager
import android.os.Build
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.selini.aitoolbox.agent.ToolDef
import com.selini.aitoolbox.agent.ToolParam
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object MiscTools {

    val all: List<ToolDef> = listOf(qrCode(), dateCalc(), deviceInfo())

    private fun qrCode() = ToolDef(
        name = "generate_qr_code",
        title = "二维码生成",
        emoji = "🔳",
        summary = "把文字或链接变成二维码图片",
        example = "生成一个内容为“周六晚 7 点老地方见”的二维码",
        desc = "把一段文本或链接生成为二维码图片，图片会直接显示在聊天里。适合分享网址、WiFi 信息、短语等。",
        params = listOf(
            ToolParam("text", "string", "二维码内容（链接或文本）", true),
            ToolParam("size", "integer", "图片边长（像素），默认 512，范围 128~1024", default = "512"),
        ),
        category = "生成与图像",
    ) { args, _ ->
        val text = args.getString("text")
        val size = args.optInt("size", 512).coerceIn(128, 1024)
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
        )
        val matrix = try {
            QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        } catch (e: Exception) {
            throw IllegalArgumentException("生成失败：内容太长或含有无法编码的字符（二维码内容一般不超过 1000 字）")
        }
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        bmp.setPixels(pixels, 0, size, 0, 0, size, size)
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
        bmp.recycle()
        JSONObject()
            .put("ok", true)
            .put("format", "png")
            .put("size", size)
            .put("text", text)
            .put("image_base64", Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP))
    }

    private fun weekdayCn(d: LocalDate): String = when (d.dayOfWeek.value) {
        1 -> "星期一"; 2 -> "星期二"; 3 -> "星期三"; 4 -> "星期四"
        5 -> "星期五"; 6 -> "星期六"; else -> "星期日"
    }

    private fun parseDate(s: String?): LocalDate {
        require(!s.isNullOrBlank()) { "缺少日期参数，格式应为 yyyy-MM-dd" }
        return try {
            LocalDate.parse(s.trim())
        } catch (e: Exception) {
            throw IllegalArgumentException("日期「$s」格式不对，应为 yyyy-MM-dd，例如 2026-10-01")
        }
    }

    private fun dateCalc() = ToolDef(
        name = "date_calc",
        title = "日期计算",
        emoji = "📅",
        summary = "今天日期、加减天数、日期间隔、星期几",
        example = "距离 2026-10-01 还有多少天",
        desc = "日期计算。operation 取值：today=查今天的日期和星期；add_days=日期加减 N 天；days_between=算两个日期相差几天；weekday=查某天是星期几。日期格式 yyyy-MM-dd。",
        params = listOf(
            ToolParam(
                "operation", "string", "操作类型", true,
                listOf("today", "add_days", "days_between", "weekday"),
            ),
            ToolParam("date", "string", "日期 yyyy-MM-dd（operation=today 时不用传）"),
            ToolParam("date2", "string", "第二个日期，days_between 时必填"),
            ToolParam("days", "integer", "加减的天数，add_days 时使用，可为负数"),
        ),
        category = "数字与计算",
    ) { args, _ ->
        val op = args.getString("operation")
        val out = JSONObject().put("ok", true).put("operation", op)
        when (op) {
            "today" -> {
                val d = LocalDate.now()
                out.put("date", d.toString()).put("weekday", weekdayCn(d))
            }
            "weekday" -> {
                val d = parseDate(args.optString("date"))
                out.put("date", d.toString()).put("weekday", weekdayCn(d))
            }
            "add_days" -> {
                val d = parseDate(args.optString("date")).plusDays(args.optLong("days", 0L))
                out.put("result_date", d.toString()).put("weekday", weekdayCn(d))
            }
            "days_between" -> {
                val d1 = parseDate(args.optString("date"))
                val d2 = parseDate(args.optString("date2"))
                out.put("from", d1.toString()).put("to", d2.toString())
                    .put("days", ChronoUnit.DAYS.between(d1, d2))
            }
            else -> throw IllegalArgumentException("不支持的操作：$op")
        }
        out
    }

    private fun deviceInfo() = ToolDef(
        name = "get_device_info",
        title = "设备信息",
        emoji = "📱",
        summary = "查看本机型号、系统、屏幕、电池等",
        example = "看看我的手机是什么型号、电量还剩多少",
        desc = "获取本机设备信息：品牌、型号、系统版本、屏幕分辨率与密度、CPU 架构与核数、电池电量。无参数。",
        params = listOf(),
        category = "设备与系统",
    ) { _, ctx ->
        val app = ctx.applicationContext
        val dm = app.resources.displayMetrics
        val bm = app.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val battery = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: Int.MIN_VALUE
        JSONObject()
            .put("ok", true)
            .put("品牌", Build.MANUFACTURER)
            .put("型号", Build.MODEL)
            .put("系统", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            .put("CPU架构", Build.SUPPORTED_ABIS.firstOrNull() ?: "未知")
            .put("CPU核数", Runtime.getRuntime().availableProcessors())
            .put("屏幕", "${dm.widthPixels}×${dm.heightPixels} px，密度 ${dm.density}x")
            .put("电量", if (battery in 1..100) "$battery%" else "未知")
    }
}
