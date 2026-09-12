package com.selini.aitoolbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.selini.aitoolbox.agent.ToolDef
import com.selini.aitoolbox.agent.ToolRegistry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolboxScreen(onOpen: (ToolDef) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "工具箱",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Text(
            "点卡片直接打开使用；想让 AI 代劳，去聊天里说一句就行",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(ToolRegistry.tools.size) { index ->
                val tool = ToolRegistry.tools[index]
                Card(onClick = { onOpen(tool) }) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(tool.emoji, style = MaterialTheme.typography.headlineMedium)
                        Text(tool.title, style = MaterialTheme.typography.titleMedium)
                        Text(tool.summary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
