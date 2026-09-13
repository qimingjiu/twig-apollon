package com.selini.aitoolbox.agent

import android.content.Context
import com.selini.aitoolbox.tools.MiscTools
import com.selini.aitoolbox.tools.NumberTools
import com.selini.aitoolbox.tools.TextTools
import org.json.JSONArray
import org.json.JSONObject

/** 一个工具参数的描述，会转换成 OpenAI/DeepSeek 的 JSON Schema */
class ToolParam(
    val name: String,
    val type: String, // string | integer | number | boolean
    val desc: String,
    val required: Boolean = false,
    val enum: List<String>? = null,
    /** 仅 UI 预填用（不进 AI Schema）；须与工具实现内的默认值保持一致，boolean 用 "true"/"false" */
    val default: String? = null,
)

/**
 * 一个本地工具 = 给 AI 看的元数据（name/desc/params）+ 给用户看的元数据（title/summary/example）
 * + 真正的执行函数 run。执行函数必须返回带 "ok" 字段的 JSONObject。
 */
class ToolDef(
    val name: String,
    val title: String,
    val emoji: String,
    val summary: String,
    val example: String,
    val desc: String,
    val params: List<ToolParam>,
    /** 仅 UI 分组用（书架书脊），不进 AI Schema。须在 run 之前：尾随 lambda 只能绑定最后一个参数 */
    val category: String = "其他",
    val run: (args: JSONObject, context: Context) -> JSONObject,
)

object ToolRegistry {

    val tools: List<ToolDef> = TextTools.all + NumberTools.all + MiscTools.all

    fun find(name: String): ToolDef? = tools.firstOrNull { it.name == name }

    /** 生成 DeepSeek function calling 的 tools 数组 */
    fun openAiSchemas(): JSONArray {
        val arr = JSONArray()
        for (t in tools) {
            val props = JSONObject()
            val required = JSONArray()
            for (p in t.params) {
                val prop = JSONObject().put("type", p.type).put("description", p.desc)
                if (p.enum != null) prop.put("enum", JSONArray(p.enum))
                props.put(p.name, prop)
                if (p.required) required.put(p.name)
            }
            arr.put(
                JSONObject()
                    .put("type", "function")
                    .put(
                        "function",
                        JSONObject()
                            .put("name", t.name)
                            .put("description", t.desc)
                            .put(
                                "parameters",
                                JSONObject()
                                    .put("type", "object")
                                    .put("properties", props)
                                    .put("required", required),
                            ),
                    ),
            )
        }
        return arr
    }

    /** 执行工具：参数解析失败、执行抛异常都转成 ok=false 的结果回传给模型，让它自我纠正 */
    fun execute(tool: ToolDef, rawArgs: String, context: Context): JSONObject {
        val args = try {
            JSONObject(rawArgs)
        } catch (e: Exception) {
            return JSONObject().put("ok", false).put("error", "参数不是合法 JSON：${e.message}")
        }
        return try {
            tool.run(args, context)
        } catch (e: Exception) {
            JSONObject().put("ok", false).put("error", e.message ?: e.javaClass.simpleName)
        }
    }
}
