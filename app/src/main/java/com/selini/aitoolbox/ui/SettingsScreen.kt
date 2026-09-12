package com.selini.aitoolbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.selini.aitoolbox.ChatViewModel
import com.selini.aitoolbox.data.LlmClient
import com.selini.aitoolbox.data.Provider
import com.selini.aitoolbox.data.ProviderPresets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(vm: ChatViewModel) {
    val providers by vm.providers.collectAsState()
    val selectedId by vm.selectedId.collectAsState()
    var editing by remember { mutableStateOf<Provider?>(null) }
    var isNew by remember { mutableStateOf(false) }

    if (editing == null) {
        ProviderList(
            providers = providers,
            selectedId = selectedId,
            onEdit = { editing = it; isNew = false },
            onAdd = {
                editing = Provider(name = "", baseUrl = "", apiKey = "")
                isNew = true
            },
            onDelete = { vm.deleteProvider(it.id) },
        )
    } else {
        ProviderEditor(
            initial = editing!!,
            isNew = isNew,
            onSave = { vm.upsertProvider(it); editing = null },
            onDelete = { vm.deleteProvider(it.id); editing = null },
            onBack = { editing = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderList(
    providers: List<Provider>,
    selectedId: String,
    onEdit: (Provider) -> Unit,
    onAdd: () -> Unit,
    onDelete: (Provider) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.titleLarge)
        Text("模型供应商", style = MaterialTheme.typography.titleMedium)
        providers.forEach { p ->
            Card(onClick = { onEdit(p) }) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            p.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (p.id == selectedId) {
                            Text(
                                "✓ 使用中",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        TextButton(onClick = { onDelete(p) }) { Text("删除") }
                    }
                    Text(p.baseUrl, style = MaterialTheme.typography.bodySmall)
                    Text(
                        buildString {
                            append(if (p.model.isBlank()) "模型：未设置" else "模型：${p.model}")
                            if (p.apiKey.isBlank()) append("  ·  未填 Key")
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Button(onClick = onAdd) { Text("＋ 添加供应商") }
        Text(
            "统一使用 OpenAI 兼容协议：填接口地址和 Key，支持 /models 的服务商会自动拉取模型列表；" +
                "火山方舟可填推理接入点 ep-xxx，中转站（one-api / new-api）直接填它们的地址。" +
                "点击卡片编辑，✓ 表示当前聊天使用的供应商。",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderEditor(
    initial: Provider,
    isNew: Boolean,
    onSave: (Provider) -> Unit,
    onDelete: (Provider) -> Unit,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var model by remember { mutableStateOf(initial.model) }
    var showKey by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf<List<String>?>(null) }
    var fetching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val preset = ProviderPresets.all.firstOrNull { it.name == name && it.baseUrl == baseUrl }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Text(
                if (isNew) "添加供应商" else "编辑供应商",
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Text("预设模板", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProviderPresets.all.forEach { p ->
                FilterChip(
                    selected = name == p.name && baseUrl == p.baseUrl,
                    onClick = { name = p.name; baseUrl = p.baseUrl },
                    label = { Text(p.name) },
                )
            }
        }
        preset?.hint?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("名称") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("接口地址 Base URL") },
            placeholder = { Text("https://api.example.com/v1") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key") },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { showKey = !showKey }) {
                    Text(if (showKey) "隐藏" else "显示")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Text("模型", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text("模型名（可手动填写）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    fetching = true
                    error = null
                    scope.launch {
                        models = try {
                            withContext(Dispatchers.IO) {
                                LlmClient.listModels(
                                    Provider(id = initial.id, name = name, baseUrl = baseUrl, apiKey = apiKey)
                                )
                            }
                        } catch (e: Exception) {
                            error = e.message
                            null
                        }
                        fetching = false
                    }
                },
                enabled = baseUrl.isNotBlank() && apiKey.isNotBlank() && !fetching,
            ) { Text(if (fetching) "拉取中…" else "拉取模型列表") }
            error?.let {
                Text(
                    "⚠️ $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        models?.let { list ->
            Text(
                "共 ${list.size} 个模型，点选即用" + if (list.size > 60) "（仅显示前 60）" else "",
                style = MaterialTheme.typography.bodySmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                list.take(60).forEach { m ->
                    FilterChip(
                        selected = model == m,
                        onClick = { model = m },
                        label = { Text(m, style = MaterialTheme.typography.bodySmall) },
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onSave(
                        Provider(
                            id = initial.id,
                            name = name.trim(),
                            baseUrl = baseUrl.trim().trimEnd('/'),
                            apiKey = apiKey.trim(),
                            model = model.trim(),
                        )
                    )
                },
                enabled = name.isNotBlank() && baseUrl.isNotBlank(),
            ) { Text("保存") }
            if (!isNew) {
                OutlinedButton(onClick = { onDelete(initial) }) { Text("删除") }
            }
        }
    }
}
