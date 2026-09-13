package com.selini.aitoolbox.tools

import android.util.Base64
import com.selini.aitoolbox.agent.ToolDef
import com.selini.aitoolbox.agent.ToolParam
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

object TextTools {

    val all: List<ToolDef> = listOf(password(), codec(), jsonFormat(), amountChinese())

    private fun password() = ToolDef(
        name = "generate_password",
        title = "生成密码",
        emoji = "🔑",
        summary = "生成密码学安全的随机密码",
        example = "帮我生成一个 20 位、包含符号的强密码",
        desc = "生成一个或多个密码学安全的随机密码。用户想要密码、随机字符串时使用。",
        params = listOf(
            ToolParam("length", "integer", "密码长度，默认 16，范围 4~64", default = "16"),
            ToolParam("count", "integer", "生成个数，默认 1，最多 10", default = "1"),
            ToolParam("include_uppercase", "boolean", "是否包含大写字母，默认 true", default = "true"),
            ToolParam("include_lowercase", "boolean", "是否包含小写字母，默认 true", default = "true"),
            ToolParam("include_digits", "boolean", "是否包含数字，默认 true", default = "true"),
            ToolParam("include_symbols", "boolean", "是否包含符号 !@#$%^&*-_+=?，默认 true", default = "true"),
            ToolParam("exclude_ambiguous", "boolean", "是否排除易混淆字符 0O1lI，默认 false", default = "false"),
        ),
        category = "生成与图像",
    ) { args, _ ->
        val length = args.optInt("length", 16).coerceIn(4, 64)
        val count = args.optInt("count", 1).coerceIn(1, 10)
        var pools = buildList {
            if (args.optBoolean("include_uppercase", true)) add("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
            if (args.optBoolean("include_lowercase", true)) add("abcdefghijklmnopqrstuvwxyz")
            if (args.optBoolean("include_digits", true)) add("0123456789")
            if (args.optBoolean("include_symbols", true)) add("!@#\$%^&*-_+=?")
        }
        if (args.optBoolean("exclude_ambiguous", false)) {
            pools = pools.mapNotNull { p -> p.filterNot { it in "0O1lI" }.takeIf { it.isNotEmpty() } }
        }
        require(pools.isNotEmpty()) { "至少需要保留一类字符" }
        require(length >= pools.size) { "密码长度不能小于启用的字符类别数（${pools.size}）" }
        val allChars = pools.joinToString("")
        val rng = SecureRandom()
        val list = (1..count).map {
            val chars = mutableListOf<Char>()
            pools.forEach { p -> chars.add(p[rng.nextInt(p.length)]) }
            while (chars.size < length) chars.add(allChars[rng.nextInt(allChars.length)])
            chars.shuffle(rng)
            chars.joinToString("")
        }
        JSONObject().put("ok", true).put("length", length).put("passwords", JSONArray(list))
    }

    private fun codec() = ToolDef(
        name = "encode_decode",
        title = "文本编码/摘要",
        emoji = "🔐",
        summary = "Base64、URL 编码解码，MD5/SHA 摘要",
        example = "把“你好，工具箱”转成 Base64",
        desc = "对文本做 Base64 编解码、URL 编解码，或计算 MD5/SHA-1/SHA-256 摘要（摘要不可逆）。",
        params = listOf(
            ToolParam("text", "string", "要处理的文本", true),
            ToolParam(
                "method", "string", "处理方式", true,
                listOf("base64_encode", "base64_decode", "url_encode", "url_decode", "md5", "sha1", "sha256"),
            ),
        ),
        category = "文本与编码",
    ) { args, _ ->
        val text = args.getString("text")
        val method = args.getString("method")
        val result = when (method) {
            "base64_encode" -> Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            "base64_decode" -> try {
                String(Base64.decode(text, Base64.DEFAULT), Charsets.UTF_8)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("不是合法的 Base64 字符串")
            }
            "url_encode" -> URLEncoder.encode(text, "UTF-8")
            "url_decode" -> URLDecoder.decode(text, "UTF-8")
            "md5", "sha1", "sha256" -> MessageDigest.getInstance(method)
                .digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            else -> throw IllegalArgumentException("不支持的处理方式：$method")
        }
        JSONObject().put("ok", true).put("method", method).put("result", result)
    }

    private fun jsonFormat() = ToolDef(
        name = "format_json",
        title = "JSON 格式化",
        emoji = "🧾",
        summary = "校验并美化 JSON 文本",
        example = "把这个 JSON 格式化：{\"a\":1,\"b\":[2,3]}",
        desc = "校验 JSON 是否合法并按缩进美化输出。text 参数是 JSON 字符串本身。",
        params = listOf(
            ToolParam("text", "string", "JSON 字符串", true),
            ToolParam("indent", "integer", "缩进空格数，默认 2，0 表示压缩成一行", default = "2"),
        ),
        category = "文本与编码",
    ) { args, _ ->
        val text = args.getString("text").trim()
        val indent = args.optInt("indent", 2).coerceIn(0, 8)
        val isArray = text.startsWith("[")
        val formatted = if (isArray) JSONArray(text).toString(indent) else JSONObject(text).toString(indent)
        JSONObject()
            .put("ok", true)
            .put("type", if (isArray) "array" else "object")
            .put("formatted", formatted)
    }

    private fun amountChinese() = ToolDef(
        name = "amount_to_chinese",
        title = "人民币大写",
        emoji = "💴",
        summary = "数字金额转中文大写（报账/发票用）",
        example = "把 1024.5 转成人民币大写",
        desc = "把数字金额转换成人民币大写汉字，例如 1024.5 → 壹仟零贰拾肆元伍角。金额范围 0 ~ 1 万亿。",
        params = listOf(
            ToolParam("amount", "number", "金额（元）", true),
        ),
        category = "文本与编码",
    ) { args, _ ->
        val amount = args.getDouble("amount")
        require(amount >= 0.0 && amount < 1e12) { "金额需在 0 ~ 1 万亿之间" }
        val cents = Math.round(amount * 100.0)
        JSONObject().put("ok", true).put("amount", amount).put("chinese", toUpper(cents))
    }

    private val DIGITS = arrayOf("零", "壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖")
    private val SMALL_UNITS = arrayOf("", "拾", "佰", "仟")
    private val GROUP_UNITS = arrayOf("", "万", "亿")

    private fun toUpper(cents: Long): String {
        val yuan = cents / 100
        val jiao = (cents % 100 / 10).toInt()
        val fen = (cents % 10).toInt()
        val sb = StringBuilder()
        if (yuan > 0L || (jiao == 0 && fen == 0)) sb.append(intChinese(yuan)).append("元")
        if (jiao > 0) sb.append(DIGITS[jiao]).append("角")
        else if (yuan > 0L && fen > 0) sb.append("零")
        if (fen > 0) sb.append(DIGITS[fen]).append("分")
        if (jiao == 0 && fen == 0) sb.append("整")
        return sb.toString()
    }

    private fun intChinese(num: Long): String {
        if (num == 0L) return "零"
        val groups = mutableListOf<Int>()
        var n = num
        while (n > 0L) {
            groups.add((n % 10000L).toInt())
            n /= 10000L
        }
        var out = ""
        for (gi in groups.indices.reversed()) {
            val g = groups[gi]
            if (g == 0) continue
            if (out.isNotEmpty() && g < 1000) out += "零"
            out += fourDigits(g) + GROUP_UNITS[gi]
        }
        while (out.contains("零零")) out = out.replace("零零", "零")
        return out.trimEnd('零')
    }

    private fun fourDigits(n: Int): String {
        val sb = StringBuilder()
        var needZero = false
        var started = false
        for (i in 3 downTo 0) {
            val base = when (i) {
                3 -> 1000
                2 -> 100
                1 -> 10
                else -> 1
            }
            val d = n / base % 10
            if (d == 0) {
                if (started) needZero = true
            } else {
                if (needZero) {
                    sb.append('零')
                    needZero = false
                }
                sb.append(DIGITS[d]).append(SMALL_UNITS[i])
                started = true
            }
        }
        return sb.toString()
    }
}
