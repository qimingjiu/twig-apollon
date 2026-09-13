package com.selini.aitoolbox.data

import okhttp3.Call
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

    /**
     * 流式对话（SSE）：请求体加 "stream": true，逐行读 data: 前缀，[DONE] 结束。
     * content 增量经 onContentDelta 回调（首个增量起调用方即可上屏）；tool_calls 增量按 index
     * 合并 arguments 片段，方法返回时拼出与非流式同形的完整 assistant 消息（供工具轮回传 API）。
     * onCall 在 execute 前回调，供调用方持有以支持中途取消。非 2xx 仍走 errorText 人话。
     */
    fun chatStream(
        provider: Provider,
        model: String,
        messages: JSONArray,
        tools: JSONArray,
        onCall: (Call) -> Unit,
        onContentDelta: (String) -> Unit,
    ): JSONObject {
        val url = provider.baseUrl.trimEnd('/') + "/chat/completions"
        val body = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("tools", tools)
            .put("stream", true)

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${provider.apiKey}")
            .post(body.toString().toRequestBody(jsonType))
            .build()

        val call = client.newCall(request)
        onCall(call)
        call.execute().use { resp ->
            if (!resp.isSuccessful) {
                val text = resp.body?.string().orEmpty()
                val root = try {
                    JSONObject(text)
                } catch (e: Exception) {
                    JSONObject()
                }
                throw ApiException(errorText(resp.code, text, root))
            }
            val source = resp.body?.source() ?: throw ApiException("响应内容为空")

            val content = StringBuilder()
            class PartialCall {
                var id = ""
                var name = ""
                val args = StringBuilder()
            }
            val partials = sortedMapOf<Int, PartialCall>()

            while (true) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload == "[DONE]") break
                val chunk = try {
                    JSONObject(payload)
                } catch (e: Exception) {
                    continue
                }
                val delta = chunk.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta") ?: continue
                val piece = delta.optText("content")
                if (piece.isNotEmpty()) {
                    content.append(piece)
                    onContentDelta(piece)
                }
                val tc = delta.optJSONArray("tool_calls") ?: continue
                for (i in 0 until tc.length()) {
                    val o = tc.optJSONObject(i) ?: continue
                    val p = partials.getOrPut(o.optInt("index", 0)) { PartialCall() }
                    val id = o.optText("id")
                    if (id.isNotEmpty()) p.id = id
                    o.optJSONObject("function")?.let { fn ->
                        val name = fn.optText("name")
                        if (name.isNotEmpty()) p.name = name
                        val args = fn.optText("arguments")
                        if (args.isNotEmpty()) p.args.append(args)
                    }
                }
            }

            val msg = JSONObject().put("role", "assistant").put("content", content.toString())
            if (partials.isNotEmpty()) {
                val arr = JSONArray()
                for ((index, p) in partials) {
                    arr.put(
                        JSONObject()
                            .put("id", p.id)
                            .put("type", "function")
                            .put(
                                "function",
                                JSONObject().put("name", p.name).put("arguments", p.args.toString()),
                            )
                    )
                }
                msg.put("tool_calls", arr)
            }
            return msg
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

    /** optString 在「键存在但为 JSON null」时会把哨兵值 toString 成 "null"（SSE 增量里常见），这里统一按空串处理 */
    private fun JSONObject.optText(name: String): String {
        val v = opt(name) ?: return ""
        if (v === JSONObject.NULL) return ""
        return v as? String ?: v.toString()
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
