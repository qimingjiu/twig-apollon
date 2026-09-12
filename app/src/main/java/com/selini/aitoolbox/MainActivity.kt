package com.selini.aitoolbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.selini.aitoolbox.agent.ToolDef
import com.selini.aitoolbox.ui.ChatScreen
import com.selini.aitoolbox.ui.SettingsScreen
import com.selini.aitoolbox.ui.ToolRunScreen
import com.selini.aitoolbox.ui.ToolboxScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                App()
            }
        }
    }
}

@Composable
fun App(vm: ChatViewModel = viewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var runningTool by remember { mutableStateOf<ToolDef?>(null) }

    val tool = runningTool
    if (tool != null) {
        BackHandler { runningTool = null }
        ToolRunScreen(
            tool = tool,
            onBack = { runningTool = null },
            onAskAi = { example ->
                vm.draft.value = example
                runningTool = null
                tab = 0
            },
        )
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("聊天" to "💬", "工具箱" to "🧰", "设置" to "⚙️")
                    .forEachIndexed { index, (label, emoji) ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Text(emoji) },
                            label = { Text(label) },
                        )
                    }
            }
        },
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> ChatScreen(vm)
                1 -> ToolboxScreen(onOpen = { runningTool = it })
                else -> SettingsScreen(vm)
            }
        }
    }
}
