package com.selini.aitoolbox.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** 一个模型供应商 = OpenAI 兼容的 Base URL + Key + 当前选用的模型 */
data class Provider(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val baseUrl: String,
    val apiKey: String = "",
    val model: String = "",
)

object ProviderPresets {

    data class Preset(val name: String, val baseUrl: String, val hint: String = "")

    val all = listOf(
        Preset("DeepSeek", "https://api.deepseek.com"),
        Preset("智谱 GLM", "https://open.bigmodel.cn/api/paas/v4"),
        Preset("Kimi", "https://api.moonshot.cn/v1"),
        Preset("火山方舟", "https://ark.cn-beijing.volces.com/api/v3", "模型名可填 Model ID（doubao-…）或推理接入点 ep-xxx"),
        Preset("OpenRouter", "https://openrouter.ai/api/v1"),
        Preset("硅基流动", "https://api.siliconflow.cn/v1"),
        Preset("Ollama 本机", "http://10.0.2.2:11434/v1", "模拟器里 10.0.2.2 指向电脑；真机请改成电脑的局域网 IP"),
        Preset("自定义", "", "任何 OpenAI 兼容端点都行，包括 one-api / new-api 类中转"),
    )
}

/** 供应商配置的本地持久化（SharedPreferences + JSON） */
class ProviderStore(context: Context) {

    private val sp: SharedPreferences = context.getSharedPreferences("providers", Context.MODE_PRIVATE)

    /** @return (选中的供应商 id, 供应商列表) */
    fun load(): Pair<String, List<Provider>> {
        val raw = sp.getString("data", null) ?: return seed()
        return try {
            val root = JSONObject(raw)
            val arr = root.optJSONArray("providers") ?: JSONArray()
            val providers = (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Provider(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    baseUrl = o.optString("baseUrl"),
                    apiKey = o.optString("apiKey"),
                    model = o.optString("model"),
                )
            }.filter { it.id.isNotBlank() && it.baseUrl.isNotBlank() }
            if (providers.isEmpty()) seed() else root.optString("selected") to providers
        } catch (e: Exception) {
            seed()
        }
    }

    fun save(selectedId: String, providers: List<Provider>) {
        val arr = JSONArray()
        providers.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("baseUrl", p.baseUrl)
                    .put("apiKey", p.apiKey)
                    .put("model", p.model)
            )
        }
        sp.edit()
            .putString("data", JSONObject().put("selected", selectedId).put("providers", arr).toString())
            .apply()
    }

    /** 首次启动预置一个 DeepSeek 供应商 */
    private fun seed(): Pair<String, List<Provider>> {
        val p = Provider(name = "DeepSeek", baseUrl = "https://api.deepseek.com", model = "deepseek-chat")
        save(p.id, listOf(p))
        return p.id to listOf(p)
    }
}
