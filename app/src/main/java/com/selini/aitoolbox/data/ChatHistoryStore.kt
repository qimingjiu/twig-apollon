package com.selini.aitoolbox.data

import android.content.Context
import com.selini.aitoolbox.ChatItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 聊天记录的展示层持久化（filesDir/chat_history.json）。
 * 只负责「杀进程后恢复聊天列表」；发给模型的历史仍按 C4 只带 user/assistant 文本（见 ChatViewModel）。
 */
class ChatHistoryStore(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)
    private val tmp = File(context.filesDir, "$FILE_NAME.tmp")

    /** 文件不存在或损坏时返回空列表，绝不崩溃 */
    fun load(): List<ChatItem> {
        val raw = try {
            file.readText()
        } catch (e: Exception) {
            return emptyList()
        }
        return try {
            val arr = JSONObject(raw).optJSONArray("items") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i -> decode(arr.optJSONObject(i)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 全量重写：先写临时文件再替换，进程中途被杀不会留下半个坏文件；上限保留最近 MAX_ITEMS 条 */
    fun save(items: List<ChatItem>) {
        try {
            val arr = JSONArray()
            items.takeLast(MAX_ITEMS).forEach { arr.put(encode(it)) }
            tmp.writeText(JSONObject().put("items", arr).toString())
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            // 写失败（磁盘满等）不崩溃，下一次 items 变更会整体重写
        }
    }

    private fun encode(item: ChatItem): JSONObject {
        val o = JSONObject()
        when (item) {
            is ChatItem.User -> o.put("type", "user").put("text", item.text)
            is ChatItem.Assistant -> o.put("type", "assistant").put("text", item.text)
            is ChatItem.Failure -> o.put("type", "failure").put("text", item.text)
            is ChatItem.ToolCall -> {
                // image_base64 剔除：二维码图片不随进程恢复（新取舍，见设计文档 §6）
                val result = JSONObject(item.result.toString())
                result.remove("image_base64")
                o.put("type", "tool_call")
                    .put("emoji", item.emoji)
                    .put("title", item.title)
                    .put("args", item.args)
                    .put("result", result)
            }
        }
        return o
    }

    private fun decode(o: JSONObject?): ChatItem? {
        if (o == null) return null
        return when (o.optString("type")) {
            "user" -> ChatItem.User(o.optString("text"))
            "assistant" -> ChatItem.Assistant(o.optString("text"))
            "failure" -> ChatItem.Failure(o.optString("text"))
            "tool_call" -> {
                val result = o.optJSONObject("result") ?: JSONObject()
                ChatItem.ToolCall(
                    emoji = o.optString("emoji"),
                    title = o.optString("title"),
                    args = o.optJSONObject("args") ?: JSONObject(),
                    result = result,
                    imageBase64 = result.optString("image_base64").ifBlank { null },
                )
            }
            else -> null
        }
    }

    private companion object {
        const val FILE_NAME = "chat_history.json"
        const val MAX_ITEMS = 200
    }
}
