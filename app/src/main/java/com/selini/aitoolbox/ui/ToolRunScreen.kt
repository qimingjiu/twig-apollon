package com.selini.aitoolbox.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.selini.aitoolbox.agent.ToolDef
import com.selini.aitoolbox.agent.ToolParam
import com.selini.aitoolbox.agent.ToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 工具直接操作页：不经过 AI，表单由 ToolDef.params 自动生成。
 * 参数留空即不发送，工具内部默认值生效（与 C2 协议一致）；图片结果复用 ImageActions。
 */
@Composable
fun ToolRunScreen(
    tool: ToolDef,
    onBack: () -> Unit,
    onAskAi: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 非布尔参数按 default 预填（无 default 留空），布尔按 default 解析出初始态——三态 chips 已消灭
    val texts = remember(tool.name) {
        mutableStateMapOf<String, String>().apply {
            tool.params.filter { it.type != "boolean" }.forEach { p ->
                p.default?.let { put(p.name, it) }
            }
        }
    }
    val bools = remember(tool.name) {
        mutableStateMapOf<String, Boolean>().apply {
            tool.params.filter { it.type == "boolean" }.forEach { p ->
                put(p.name, p.default == "true")
            }
        }
    }
    var running by remember(tool.name) { mutableStateOf(false) }
    var result by remember(tool.name) { mutableStateOf<JSONObject?>(null) }
    var inputError by remember(tool.name) { mutableStateOf<String?>(null) }

    val requiredFilled = tool.params.filter { it.required }.all { p ->
        !texts[p.name].isNullOrBlank()
    }

    fun buildArgs(): JSONObject? {
        val args = JSONObject()
        for (p in tool.params) {
            when (p.type) {
                "boolean" -> bools[p.name]?.let { args.put(p.name, it) }
                "integer" -> {
                    val raw = texts[p.name]?.trim().orEmpty()
                    if (raw.isNotEmpty()) {
                        val v = raw.toIntOrNull()
                        if (v == null) {
                            inputError = "「${p.desc}」需要填整数"
                            return null
                        }
                        args.put(p.name, v)
                    }
                }
                "number" -> {
                    val raw = texts[p.name]?.trim().orEmpty()
                    if (raw.isNotEmpty()) {
                        val v = raw.toDoubleOrNull()
                        if (v == null) {
                            inputError = "「${p.desc}」需要填数字"
                            return null
                        }
                        args.put(p.name, v)
                    }
                }
                else -> {
                    val raw = texts[p.name].orEmpty()
                    if (raw.isNotBlank() || p.required) args.put(p.name, raw)
                }
            }
        }
        return args
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("${tool.emoji} ${tool.title}", style = MaterialTheme.typography.titleLarge)
        }
        Text(tool.summary, style = MaterialTheme.typography.bodySmall)

        tool.params.forEach { p ->
            when {
                p.type == "boolean" -> BoolParamField(p, bools[p.name] ?: false) { bools[p.name] = it }
                p.enum != null -> EnumParamField(p, texts[p.name]) { texts[p.name] = it }
                else -> TextParamField(p, texts[p.name].orEmpty()) { texts[p.name] = it }
            }
        }

        inputError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = {
                inputError = null
                val args = buildArgs() ?: return@Button
                running = true
                result = null
                scope.launch {
                    val r = withContext(Dispatchers.IO) {
                        ToolRegistry.execute(tool, args.toString(), context)
                    }
                    result = r
                    running = false
                }
            },
            enabled = !running && requiredFilled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (running) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Text("运行")
            }
        }

        result?.let { ResultCard(it) }

        TextButton(
            onClick = { onAskAi(tool.example) },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("参数拿不准？让 AI 来执行 →")
        }
    }
}

@Composable
private fun ParamCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** caption 后缀三态：必填 / 有 default 可留空 / 无 default 的选填 */
private fun captionSuffix(p: ToolParam): String = when {
    p.required -> "（必填）"
    p.default != null -> "（留空用默认值）"
    else -> "（选填）"
}

@Composable
private fun TextParamField(p: ToolParam, value: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ParamCaption(p.desc + captionSuffix(p))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = p.name != "text",
            maxLines = if (p.name == "text") 5 else 1,
            keyboardOptions = when (p.type) {
                "integer", "number" -> KeyboardOptions(keyboardType = KeyboardType.Number)
                else -> KeyboardOptions.Default
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnumParamField(p: ToolParam, selected: String?, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ParamCaption(p.desc + captionSuffix(p))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            p.enum.orEmpty().forEach { option ->
                PickChip(option, selected == option) { onSelect(option) }
            }
        }
    }
}

@Composable
private fun BoolParamField(p: ToolParam, value: Boolean, onSelect: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ParamCaption(p.desc, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onSelect)
    }
}

@Composable
private fun PickChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ResultCard(result: JSONObject) {
    if (!result.optBoolean("ok")) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "⚠️ " + result.optString("error").ifBlank { "执行失败" },
                modifier = Modifier.padding(12.dp),
            )
        }
        return
    }
    val fields = remember(result.toString()) {
        val r = JSONObject(result.toString())
        r.remove("ok")
        r.remove("image_base64")
        buildString {
            val keys = r.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                append(k).append("：").append(r.optString(k)).append('\n')
            }
        }.trim()
    }
    val imgBase64 = result.optString("image_base64").ifBlank { null }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (fields.isNotEmpty()) {
                Text(fields, style = MaterialTheme.typography.bodySmall)
            }
            imgBase64?.let { b64 ->
                val bitmap = remember(b64) {
                    runCatching {
                        val data = Base64.decode(b64, Base64.NO_WRAP)
                        BitmapFactory.decodeByteArray(data, 0, data.size)
                    }.getOrNull()
                }
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "结果图片",
                        modifier = Modifier.size(220.dp).align(Alignment.CenterHorizontally),
                    )
                    ImageActions(bitmap)
                }
            }
        }
    }
}
