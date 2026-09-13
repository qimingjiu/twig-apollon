package com.selini.aitoolbox

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.selini.aitoolbox.agent.ToolRegistry
import com.selini.aitoolbox.data.ChatHistoryStore
import com.selini.aitoolbox.data.LlmClient
import com.selini.aitoolbox.data.Provider
import com.selini.aitoolbox.data.ProviderStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** 聊天列表里的一条内容 */
sealed class ChatItem {
    data class User(val text: String) : ChatItem()
    data class Assistant(val text: String) : ChatItem()
    data class ToolCall(
        val emoji: String,
        val title: String,
        val args: JSONObject,
        val result: JSONObject,
        val imageBase64: String?,
    ) : ChatItem()
    data class Failure(val text: String) : ChatItem()
}

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val store = ProviderStore(app)
    private val historyStore = ChatHistoryStore(app)

    val items = MutableStateFlow<List<ChatItem>>(emptyList())
    val busy = MutableStateFlow<String?>(null)
    val draft = MutableStateFlow("")
    val providers = MutableStateFlow<List<Provider>>(emptyList())
    val selectedId = MutableStateFlow("")

    init {
        val (selected, list) = store.load()
        providers.value = list
        selectedId.value = selected
        // 先恢复再开始监听：collect 的首个 emission 是当前全量，顺序反了会用空列表覆盖已存文件
        viewModelScope.launch(Dispatchers.IO) {
            val restored = historyStore.load()
            if (restored.isNotEmpty()) items.value = restored
            items.collect { historyStore.save(it) }
        }
    }

    val selected: Provider?
        get() = providers.value.firstOrNull { it.id == selectedId.value }

    // ---------- 供应商管理 ----------

    fun upsertProvider(p: Provider) {
        val list = providers.value
        providers.value =
            if (list.any { it.id == p.id }) list.map { if (it.id == p.id) p else it } else list + p
        if (selectedId.value == p.id || selectedId.value.isBlank()) selectedId.value = p.id
        store.save(selectedId.value, providers.value)
    }

    fun deleteProvider(id: String) {
        providers.value = providers.value.filterNot { it.id == id }
        if (selectedId.value == id) {
            selectedId.value = providers.value.firstOrNull()?.id.orEmpty()
        }
        store.save(selectedId.value, providers.value)
    }

    fun selectProvider(id: String) {
        selectedId.value = id
        store.save(selectedId.value, providers.value)
    }

    // ---------- 聊天 ----------

    fun send() {
        val text = draft.value.trim()
        if (text.isEmpty() || busy.value != null) return
        draft.value = ""
        items.value += ChatItem.User(text)
        val p = selected
        if (p == null || p.apiKey.isBlank() || p.model.isBlank()) {
            items.value += ChatItem.Failure("请先到「设置」页配置供应商的 API Key 和模型。")
            return
        }
        busy.value = "正在思考…"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                runAgent()
            } catch (e: Exception) {
                items.value += ChatItem.Failure(e.message ?: "发生未知错误")
            } finally {
                busy.value = null
            }
        }
    }

    /** function calling 循环：模型要工具就执行，把结果喂回去，直到给出最终回答 */
    private suspend fun runAgent() = withContext(Dispatchers.IO) {
        val p = selected ?: return@withContext
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", systemPrompt()))
        for (item in items.value) {
            when (item) {
                is ChatItem.User -> messages.put(
                    JSONObject().put("role", "user").put("content", item.text)
                )
                is ChatItem.Assistant -> messages.put(
                    JSONObject().put("role", "assistant").put("content", item.text)
                )
                else -> {}
            }
        }

        var answered = false
        for (round in 1..MAX_ROUNDS) {
            busy.value = if (round == 1) "正在思考…" else "正在整理结果…"
            val resp = LlmClient.chat(p, p.model, messages, ToolRegistry.openAiSchemas())
            val msg = resp.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            val calls = msg.optJSONArray("tool_calls")
            if (calls == null || calls.length() == 0) {
                items.value += ChatItem.Assistant(
                    msg.optString("content").ifBlank { "（模型没有返回内容，请重试）" }
                )
                answered = true
                break
            }
            messages.put(msg)
            for (i in 0 until calls.length()) {
                val call = calls.getJSONObject(i)
                val fn = call.getJSONObject("function")
                val name = fn.optString("name")
                val rawArgs = fn.optString("arguments").ifBlank { "{}" }
                val def = ToolRegistry.find(name)
                busy.value = "正在执行工具：${def?.title ?: name}…"
                val result = if (def == null) {
                    JSONObject().put("ok", false).put("error", "没有名为 $name 的工具")
                } else {
                    ToolRegistry.execute(def, rawArgs, getApplication())
                }
                if (def != null) {
                    items.value += ChatItem.ToolCall(
                        emoji = def.emoji,
                        title = def.title,
                        args = try {
                            JSONObject(rawArgs)
                        } catch (e: Exception) {
                            JSONObject()
                        },
                        result = result,
                        imageBase64 = result.optString("image_base64").ifBlank { null },
                    )
                }
                messages.put(
                    JSONObject()
                        .put("role", "tool")
                        .put("tool_call_id", call.optString("id"))
                        .put("content", result.toString())
                )
            }
        }
        if (!answered) {
            items.value += ChatItem.Failure("工具调用超过 $MAX_ROUNDS 轮仍未完成，已中止。")
        }
    }

    private fun systemPrompt(): String {
        val today = LocalDate.now()
        val weekName = when (today.dayOfWeek.value) {
            1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"; 5 -> "五"; 6 -> "六"; else -> "日"
        }
        return "你是「AI工具箱」App 内置的智能助手，用户手机里安装了一批本地工具，你可以直接调用。" +
            "规则：" +
            "1) 用户的请求如果和某个工具相关，必须先调用工具，再根据工具返回的真实结果回答，绝不编造结果；" +
            "2) 参数不确定时选择最合理的默认值，并在回答里说明；" +
            "3) 工具返回 ok=false 时，先修正参数重试一次；" +
            "4) 用简体中文简洁回答；" +
            "5) 与工具无关的日常问题正常聊天即可。" +
            "今天的日期是 $today 星期$weekName。"
    }

    private companion object {
        const val MAX_ROUNDS = 6
    }
}
