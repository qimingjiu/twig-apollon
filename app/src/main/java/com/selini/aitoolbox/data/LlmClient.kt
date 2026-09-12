package com.selini.aitoolbox.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiException(message: String) : Exception(message)

/**
 * 统一的 OpenAI 兼容客户端。
 * 任何「Base URL + Key」的供应商（DeepSeek / GLM / Kimi / 方舟 / OpenRouter / 中转站 / Ollama…）都能接：
 *  - 对话：POST {base}/chat/completions
 *  - 模型列表：GET  {base}/models
 */
object LlmClient {

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    /** messages: OpenAI 风格消息数组（含 tool / tool_calls）；tools: ToolRegistry.openAiSchemas() */
    fun chat(provider: Provider, model: String, messages: JSONArray, tools: JSONArray): JSONObject {
        val url = provider.baseUrl.trimEnd('/') + "/chat/completions"
        val body = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("tools", tools)

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${provider.apiKey}")
            .post(body.toString().toRequestBody(jsonType))
            .build()

        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val root = try {
                JSONObject(text)
            } catch (e: Exception) {
                JSONObject()
            }
            if (!resp.isSuccessful) throw ApiException(errorText(resp.code, text, root))
            return root
        }
    }

    /** 拉取模型列表；不支持 /models 的服务商会抛出可读错误，让用户手动填模型名 */
    fun listModels(provider: Provider): List<String> {
        val url = provider.baseUrl.trimEnd('/') + "/models"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${provider.apiKey}")
            .get()
            .build()

        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val root = try {
                JSONObject(text)
            } catch (e: Exception) {
                JSONObject()
            }
            if (!resp.isSuccessful) throw ApiException(errorText(resp.code, text, root))
            val data = root.optJSONArray("data") ?: JSONArray()
            val ids = (0 until data.length())
                .mapNotNull { data.optJSONObject(it)?.optString("id")?.takeIf { id -> id.isNotBlank() } }
                .distinct()
                .sorted()
            if (ids.isEmpty()) throw ApiException("服务商返回了空模型列表，请手动填写模型名")
            return ids
        }
    }

    private fun errorText(code: Int, raw: String, root: JSONObject): String {
        val detail = root.optJSONObject("error")?.let { e ->
            e.optString("message").ifBlank { e.optString("code") }
        }.orEmpty().ifBlank { raw.take(200) }
        return when (code) {
            401 -> "API Key 无效或未填写"
            402 -> "账户余额不足"
            403 -> "没有权限（403）：$detail"
            404 -> "接口不存在（404），请检查接口地址：$detail"
            429 -> "请求太频繁或额度受限：$detail"
            else -> "请求失败（$code）：$detail"
        }
    }
}
