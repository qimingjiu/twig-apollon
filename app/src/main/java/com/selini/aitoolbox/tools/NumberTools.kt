package com.selini.aitoolbox.tools

import com.selini.aitoolbox.agent.ToolDef
import com.selini.aitoolbox.agent.ToolParam
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.concurrent.ThreadLocalRandom

object NumberTools {

    val all: List<ToolDef> = listOf(unit(), base(), random())

    // ---------- 单位换算 ----------

    private val LENGTH: Map<String, Double> = mapOf(
        "mm" to 0.001, "毫米" to 0.001, "cm" to 0.01, "厘米" to 0.01,
        "m" to 1.0, "米" to 1.0, "km" to 1000.0, "千米" to 1000.0, "公里" to 1000.0,
        "in" to 0.0254, "英寸" to 0.0254, "ft" to 0.3048, "英尺" to 0.3048,
        "yd" to 0.9144, "码" to 0.9144, "mi" to 1609.344, "英里" to 1609.344,
        "nmi" to 1852.0, "海里" to 1852.0, "里" to 500.0, "丈" to 10.0 / 3.0,
        "尺" to 1.0 / 3.0, "寸" to 1.0 / 30.0,
    )
    private val WEIGHT: Map<String, Double> = mapOf(
        "mg" to 0.000001, "毫克" to 0.000001, "g" to 0.001, "克" to 0.001,
        "kg" to 1.0, "千克" to 1.0, "公斤" to 1.0, "t" to 1000.0, "吨" to 1000.0,
        "斤" to 0.5, "两" to 0.05, "lb" to 0.45359237, "磅" to 0.45359237,
        "oz" to 0.028349523125, "盎司" to 0.028349523125,
    )
    private val DATA: Map<String, Double> = mapOf(
        "bit" to 0.125, "比特" to 0.125, "B" to 1.0, "字节" to 1.0,
        "KB" to 1024.0, "MB" to 1048576.0, "GB" to 1073741824.0,
        "TB" to 1099511627776.0, "PB" to 1125899906842624.0,
    )
    private val SPEED: Map<String, Double> = mapOf(
        "m/s" to 1.0, "mps" to 1.0, "米/秒" to 1.0, "米每秒" to 1.0,
        "km/h" to 1.0 / 3.6, "kmh" to 1.0 / 3.6, "千米/小时" to 1.0 / 3.6,
        "公里/小时" to 1.0 / 3.6, "mph" to 0.44704, "英里/小时" to 0.44704,
        "kn" to 0.5144444, "kt" to 0.5144444, "节" to 0.5144444,
    )
    private val AREA: Map<String, Double> = mapOf(
        "m2" to 1.0, "㎡" to 1.0, "平方米" to 1.0, "平米" to 1.0,
        "km2" to 1000000.0, "平方千米" to 1000000.0, "平方公里" to 1000000.0,
        "ha" to 10000.0, "公顷" to 10000.0, "亩" to 2000.0 / 3.0, "分地" to 200.0 / 3.0 * 0.1,
        "ft2" to 0.09290304, "平方英尺" to 0.09290304,
    )
    private val TABLES = listOf(
        "长度" to LENGTH, "重量" to WEIGHT, "数据大小" to DATA,
        "速度" to SPEED, "面积" to AREA,
    )
    private val TEMP_C = setOf("c", "℃", "°c", "摄氏度")
    private val TEMP_F = setOf("f", "℉", "°f", "华氏度")
    private val TEMP_K = setOf("k", "开尔文", "开氏度")

    private enum class Kind { FACTOR, C, F, K }
    private data class UnitRef(val category: String, val kind: Kind, val factor: Double = 1.0)

    private fun resolve(unitRaw: String): UnitRef {
        val u = unitRaw.trim()
        if (u in TEMP_C) return UnitRef("温度", Kind.C)
        if (u in TEMP_F) return UnitRef("温度", Kind.F)
        if (u in TEMP_K) return UnitRef("温度", Kind.K)
        val exact = TABLES.firstNotNullOfOrNull { (cat, m) -> m[u]?.let { cat to it } }
        if (exact != null) return UnitRef(exact.first, Kind.FACTOR, exact.second)
        val lower = u.lowercase()
        val loose = TABLES.firstNotNullOfOrNull { (cat, m) ->
            m.entries.firstOrNull { it.key.lowercase() == lower }?.let { cat to it.value }
        }
        if (loose != null) return UnitRef(loose.first, Kind.FACTOR, loose.second)
        throw IllegalArgumentException(
            "无法识别的单位「$unitRaw」，支持：米/厘米/英寸/英尺/英里/里、公斤/斤/磅、摄氏度/华氏度/开尔文、KB/MB/GB、km/h/mph/节、平方米/亩/公顷 等"
        )
    }

    private fun toCelsius(v: Double, u: UnitRef): Double = when (u.kind) {
        Kind.C -> v
        Kind.F -> (v - 32.0) * 5.0 / 9.0
        Kind.K -> v - 273.15
        Kind.FACTOR -> v * u.factor
    }

    private fun fromCelsius(c: Double, u: UnitRef): Double = when (u.kind) {
        Kind.C -> c
        Kind.F -> c * 9.0 / 5.0 + 32.0
        Kind.K -> c + 273.15
        Kind.FACTOR -> c / u.factor
    }

    private fun fmt(d: Double): String =
        BigDecimal.valueOf(d).setScale(8, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    private fun unit() = ToolDef(
        name = "convert_unit",
        title = "单位换算",
        emoji = "📐",
        summary = "长度、重量、温度、数据大小、速度、面积换算",
        example = "把 37.5 摄氏度换算成华氏度",
        desc = "单位换算，支持：长度（米/英尺/英里/里等）、重量（公斤/斤/磅等）、温度（摄氏/华氏/开尔文）、数据大小（KB/MB/GB，1024 进制）、速度（km/h/mph/节）、面积（平方米/亩/公顷）。",
        params = listOf(
            ToolParam("value", "number", "要换算的数值", true),
            ToolParam("from", "string", "原单位，如 米、kg、℃", true),
            ToolParam("to", "string", "目标单位", true),
        ),
    ) { args, _ ->
        val value = args.getDouble("value")
        val from = resolve(args.getString("from"))
        val to = resolve(args.getString("to"))
        require(from.category == to.category) {
            "「${args.getString("from")}」和「${args.getString("to")}」不是同一类单位（${from.category} vs ${to.category}），无法换算"
        }
        val result = fromCelsius(toCelsius(value, from), to)
        JSONObject()
            .put("ok", true)
            .put("input", value)
            .put("from", args.getString("from"))
            .put("to", args.getString("to"))
            .put("result", fmt(result))
    }

    // ---------- 进制转换 ----------

    private fun base() = ToolDef(
        name = "convert_base",
        title = "进制转换",
        emoji = "🔢",
        summary = "二进制、十进制、十六进制等任意进制互转",
        example = "把 255 转成十六进制",
        desc = "在 2~36 进制之间转换整数。",
        params = listOf(
            ToolParam("number", "string", "数字（字符串形式，不带 0x 之类前缀）", true),
            ToolParam("from_base", "integer", "原进制，2~36，默认 10", default = "10"),
            ToolParam("to_base", "integer", "目标进制，2~36，默认 16", default = "16"),
        ),
    ) { args, _ ->
        val fromBase = args.optInt("from_base", 10)
        val toBase = args.optInt("to_base", 16)
        require(fromBase in 2..36 && toBase in 2..36) { "进制需在 2~36 之间" }
        val n = args.getString("number").trim()
        val v = try {
            BigInteger(n, fromBase)
        } catch (e: Exception) {
            throw IllegalArgumentException("「$n」不是合法的 $fromBase 进制数")
        }
        JSONObject()
            .put("ok", true)
            .put("input", n)
            .put("from_base", fromBase)
            .put("to_base", toBase)
            .put("result", v.toString(toBase).uppercase())
    }

    // ---------- 随机数 ----------

    private fun random() = ToolDef(
        name = "random_number",
        title = "随机数",
        emoji = "🎲",
        summary = "生成随机整数，可当骰子/抽签用",
        example = "掷一个六面骰子",
        desc = "生成一个或多个范围内的随机整数（含两端），可用于掷骰子、抽奖、抽签。",
        params = listOf(
            ToolParam("min", "integer", "最小值（含），默认 1", default = "1"),
            ToolParam("max", "integer", "最大值（含），默认 100", default = "100"),
            ToolParam("count", "integer", "生成个数，默认 1，最多 100", default = "1"),
            ToolParam("unique", "boolean", "是否不允许重复，默认 false", default = "false"),
        ),
    ) { args, _ ->
        val min = args.optInt("min", 1)
        val max = args.optInt("max", 100)
        val count = args.optInt("count", 1).coerceIn(1, 100)
        val unique = args.optBoolean("unique", false)
        val lo = minOf(min, max)
        val hi = maxOf(min, max)
        val rng = ThreadLocalRandom.current()
        val nums: List<Int> = if (unique) {
            require((hi.toLong() - lo + 1) >= count) {
                "范围内只有 ${hi - lo + 1} 个不同的数，不够取 $count 个不重复的"
            }
            if (hi.toLong() - lo + 1 <= 100_000) {
                (lo..hi).shuffled(rng).take(count)
            } else {
                val set = LinkedHashSet<Int>()
                while (set.size < count) set.add(rng.nextInt(lo, hi + 1))
                set.toList()
            }
        } else {
            (1..count).map { rng.nextInt(lo, hi + 1) }
        }
        JSONObject()
            .put("ok", true)
            .put("min", lo)
            .put("max", hi)
            .put("numbers", JSONArray(nums))
    }
}
