package com.selini.aitoolbox.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.selini.aitoolbox.ChatItem
import com.selini.aitoolbox.ChatViewModel
import com.selini.aitoolbox.agent.ToolRegistry
import org.json.JSONObject

@Composable
fun ChatScreen(vm: ChatViewModel) {
    val items by vm.items.collectAsState()
    val busy by vm.busy.collectAsState()
    val draft by vm.draft.collectAsState()
    val providers by vm.providers.collectAsState()
    val selectedId by vm.selectedId.collectAsState()
    val ready = providers.firstOrNull { it.id == selectedId }
        ?.let { it.apiKey.isNotBlank() && it.model.isNotBlank() } == true
    val listState = rememberLazyListState()

    LaunchedEffect(items.size, busy != null) {
        val target = if (busy != null) items.size else items.size - 1
        if (target >= 0) listState.animateScrollToItem(target)
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        Text(
            "AI 工具箱",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        if (!ready) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            ) {
                Text(
                    "还没配置模型供应商和 API Key，请到「设置」页填写",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
        if (items.isEmpty() && busy == null) {
            EmptyGuide(
                onPick = { vm.draft.value = it },
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items) { item -> MessageRow(item) }
                if (busy != null) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(busy.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { vm.draft.value = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("让 AI 帮你调用工具…") },
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
            )
            if (busy == null) {
                Button(
                    onClick = { vm.send() },
                    enabled = draft.isNotBlank(),
                    modifier = Modifier.padding(bottom = 6.dp),
                ) { Text("发送") }
            } else {
                Button(
                    onClick = { vm.stop() },
                    modifier = Modifier.padding(bottom = 6.dp),
                ) { Text("停止") }
            }
        }
    }
}

/** 空状态即首次引导：一句人话 + 3 个真实工具的示例入口。点按只填草稿不代发，发出第一条消息后自然消失 */
@Composable
private fun EmptyGuide(onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val picks = remember {
        listOf("generate_password", "generate_qr_code", "convert_unit")
            .mapNotNull { ToolRegistry.find(it) }
            .ifEmpty { ToolRegistry.tools.take(3) }
    }
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "直接说想做的事，我会调用手机里的本地工具来办。",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "不知道能做什么？点一个试试：",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
        )
        picks.forEach { tool ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onPick(tool.example) },
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(tool.emoji)
                    Text(tool.example, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun MessageRow(item: ChatItem) {
    when (item) {
        is ChatItem.User -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp),
            ) {
                Text(item.text, modifier = Modifier.padding(12.dp).widthIn(max = 280.dp))
            }
        }
        is ChatItem.Assistant -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
            ) {
                Text(item.text, modifier = Modifier.padding(12.dp).widthIn(max = 300.dp))
            }
        }
        is ChatItem.Failure -> Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("⚠️ ${item.text}", modifier = Modifier.padding(12.dp))
        }
        is ChatItem.ToolCall -> ToolCallCard(item)
    }
}

@Composable
private fun ToolCallCard(item: ChatItem.ToolCall) {
    val display = remember(item.result.toString()) {
        val r = try {
            JSONObject(item.result.toString())
        } catch (e: Exception) {
            JSONObject()
        }
        r.remove("image_base64")
        val s = r.toString()
        if (s.length > 400) s.take(400) + "…" else s
    }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${item.emoji} ${item.title}", style = MaterialTheme.typography.titleSmall)
            Text(display, style = MaterialTheme.typography.bodySmall)
            item.imageBase64?.let { b64 ->
                val bitmap = remember(b64) {
                    runCatching {
                        val data = Base64.decode(b64, Base64.NO_WRAP)
                        BitmapFactory.decodeByteArray(data, 0, data.size)
                    }.getOrNull()
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "二维码",
                        modifier = Modifier.size(220.dp).align(Alignment.CenterHorizontally),
                    )
                    ImageActions(bitmap)
                }
            }
        }
    }
}
