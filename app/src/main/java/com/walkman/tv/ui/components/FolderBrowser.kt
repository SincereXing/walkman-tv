package com.walkman.tv.ui.components

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.walkman.tv.ui.theme.AppColors
import java.io.File

/**
 * In-app filesystem folder picker — the SAF-free alternative for Android TVs that ship without a
 * DocumentsUI (`ACTION_OPEN_DOCUMENT_TREE` → "no app can perform this action"). Browses plain
 * java.io.File directories starting from the device's storage volumes (internal / SD / USB).
 * Requires the caller to have already secured storage read access.
 *
 * D-pad: folders are focusable rows (OK enters); "⬆ 上级" goes up; 选此文件夹 picks the current dir.
 */
@Composable
fun FolderBrowser(onPick: (File) -> Unit, onCancel: () -> Unit) {
    // Empty stack = the volume-roots view; otherwise last() is the folder being browsed.
    var stack by remember { mutableStateOf(emptyList<File>()) }
    val current = stack.lastOrNull()
    val entries = remember(current) {
        if (current == null) storageRoots()
        else (current.listFiles()?.asList().orEmpty())
            .filter { it.isDirectory && !it.isHidden && it.canRead() }
            .sortedBy { it.name.lowercase() }
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .width(560.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AppColors.BgPanel)
                .padding(horizontal = 22.dp, vertical = 20.dp),
        ) {
            Text("选择音乐文件夹", color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(4.dp))
            Text(
                current?.absolutePath ?: "选择一个存储位置（内部存储 / SD 卡 / U 盘）",
                color = AppColors.TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (current != null) {
                    item {
                        FolderRow(label = "⬆ 上级", onClick = { stack = stack.dropLast(1) })
                    }
                }
                if (entries.isEmpty()) {
                    item {
                        Text(
                            if (current == null) "未找到可读取的存储位置" else "（此文件夹没有子文件夹）",
                            color = AppColors.TextMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        )
                    }
                }
                items(entries, key = { it.absolutePath }) { dir ->
                    FolderRow(
                        label = "📁  ${rootLabel(dir, isRoot = current == null)}",
                        onClick = { stack = stack + dir },
                    )
                }
            }

            Spacer(Modifier.size(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TvPill(onClick = onCancel, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
                    Text("取消", fontSize = 13.sp)
                }
                if (current != null) {
                    TvPill(
                        onClick = { onPick(current) },
                        selected = true,
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
                    ) {
                        Text("选此文件夹", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderRow(label: String, onClick: () -> Unit) {
    TvFocusable(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            color = AppColors.TextPrimary,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

/** A friendlier name for a storage-root volume; plain folder name elsewhere. */
private fun rootLabel(dir: File, isRoot: Boolean): String {
    if (!isRoot) return dir.name
    val path = dir.absolutePath
    return when {
        path == Environment.getExternalStorageDirectory()?.absolutePath -> "内部存储"
        path.startsWith("/storage/") || path.startsWith("/mnt/media_rw/") -> "外置存储（${dir.name}）"
        else -> dir.name.ifBlank { path }
    }
}

/** Readable storage volumes: internal storage + mounted SD/USB volumes. */
private fun storageRoots(): List<File> {
    val roots = LinkedHashSet<File>()
    Environment.getExternalStorageDirectory()?.takeIf { it.canRead() }?.let { roots += it }
    File("/storage").listFiles()?.forEach { v ->
        if (v.isDirectory && v.canRead() && v.name != "self" && v.name != "emulated") roots += v
    }
    File("/mnt/media_rw").listFiles()?.forEach { v ->
        if (v.isDirectory && v.canRead()) roots += v
    }
    if (roots.isEmpty()) File("/storage/emulated/0").takeIf { it.canRead() }?.let { roots += it }
    return roots.toList()
}
