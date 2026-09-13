package com.selini.aitoolbox.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.selini.aitoolbox.agent.ToolDef
import com.selini.aitoolbox.agent.ToolRegistry

/** 书脊配色：底色（深色纯色）+ 分类章（四类四色，克制低饱和） */
private data class SpineStyle(val bg: Color, val seal: Color)

private val SPINE_STYLES = mapOf(
    "文本与编码" to SpineStyle(Color(0xFF39465E), Color(0xFF9FB4D8)),
    "数字与计算" to SpineStyle(Color(0xFF3B5747), Color(0xFF97C4AB)),
    "生成与图像" to SpineStyle(Color(0xFF574263), Color(0xFFC6A3D6)),
    "设备与系统" to SpineStyle(Color(0xFF5E5240), Color(0xFFD3BD92)),
)
private val FALLBACK_STYLE = SpineStyle(Color(0xFF474747), Color(0xFFB0B0B0))

/** 工具箱「书架」形态 v1（静态版）：书脊竖排分类名，点书脊展开该分类的胶囊（三行横向滚动） */
@Composable
fun ToolboxScreen(onOpen: (ToolDef) -> Unit) {
    var expanded by remember { mutableStateOf<String?>(null) }
    val categories = remember { ToolRegistry.tools.groupBy { it.category } }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            "工具箱",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Text(
            "点书脊展开分类；想让 AI 代劳，去聊天里说一句",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(10.dp))
        categories.forEach { (name, tools) ->
            ShelfRow(
                name = name,
                tools = tools,
                style = SPINE_STYLES[name] ?: FALLBACK_STYLE,
                expanded = expanded == name,
                onToggle = { expanded = if (expanded == name) null else name },
                onOpen = onOpen,
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ShelfRow(
    name: String,
    tools: List<ToolDef>,
    style: SpineStyle,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpen: (ToolDef) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .animateContentSize(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 书脊：深色纯色圆角竖条，分类名逐字竖排居中，底部一枚分类色圆章
            Column(
                modifier = Modifier
                    .width(88.dp)
                    .fillMaxHeight()
                    .heightIn(min = 150.dp)
                    .background(style.bg, RoundedCornerShape(10.dp))
                    .clickable(onClick = onToggle)
                    .padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    name.forEach { ch ->
                        Text(
                            ch.toString(),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Box(modifier = Modifier.size(13.dp).background(style.seal, CircleShape))
            }
            if (expanded) {
                // 展开区：该分类工具 round-robin 分三行，每行一条横向可滚动的胶囊列
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    (0..2).map { row -> tools.filterIndexed { i, _ -> i % 3 == row } }.forEach { rowTools ->
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(rowTools) { tool ->
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.clickable { onOpen(tool) },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(tool.emoji)
                                        Text(tool.title, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // 层板：书脊下垫一条细横板
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .height(5.dp)
                .background(Color(0xFF4A4139), RoundedCornerShape(2.dp)),
        )
    }
}
