package com.selini.aitoolbox.ui

import android.Manifest
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** 图片产出物的就地动作：保存到相册 / 调起系统分享。二维码卡片和工具直接操作页共用 */
@Composable
internal fun ImageActions(bitmap: Bitmap) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var actionStatus by remember { mutableStateOf<String?>(null) }

    fun runSave() {
        scope.launch {
            actionStatus = withContext(Dispatchers.IO) { saveImageToGallery(context, bitmap) }
        }
    }

    val savePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            runSave()
        } else {
            actionStatus = "未授予存储权限，无法保存；可改用「分享」"
        }
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                val needLegacyPermission = Build.VERSION.SDK_INT < 29 &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    ) != PackageManager.PERMISSION_GRANTED
                if (needLegacyPermission) {
                    savePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    runSave()
                }
            }) { Text("保存到相册") }
            TextButton(onClick = {
                scope.launch {
                    actionStatus = withContext(Dispatchers.IO) { shareImage(context, bitmap) }
                }
            }) { Text("分享") }
        }
        actionStatus?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** 保存到相册：Android 10+ 走 MediaStore 免权限；26~28 靠清单里的 WRITE_EXTERNAL_STORAGE（点按时运行时申请） */
private fun saveImageToGallery(context: Context, bitmap: Bitmap): String {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "aitoolbox_${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= 29) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AIToolbox")
        }
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: return "保存失败：相册不可用"
    return try {
        resolver.openOutputStream(uri)?.use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "压缩失败" }
        } ?: error("无法写入")
        "已保存到相册"
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        "保存失败：${e.message}"
    }
}

/** 分享：图片落到缓存目录，经 FileProvider 授权给接收方，无需任何权限 */
private fun shareImage(context: Context, bitmap: Bitmap): String = try {
    val dir = File(context.cacheDir, "images").apply { mkdirs() }
    val file = File(dir, "img_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, "图片", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享图片"))
    "已调起系统分享"
} catch (e: Exception) {
    "分享失败：${e.message}"
}
