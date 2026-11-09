package com.wangzan101.mika.ui

import com.wangzan101.mika.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.wangzan101.mika.ui.components.MikaDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.ui.components.ButtonKind
import com.wangzan101.mika.ui.components.ButtonSize
import com.wangzan101.mika.ui.components.EmptyNote
import com.wangzan101.mika.ui.components.GroupCard
import com.wangzan101.mika.ui.components.GroupLabel
import com.wangzan101.mika.ui.components.KeyboardDismissArea
import com.wangzan101.mika.ui.components.ListRow
import com.wangzan101.mika.ui.components.MikaButton
import com.wangzan101.mika.ui.components.RowActions
import com.wangzan101.mika.ui.components.RowChevron
import com.wangzan101.mika.ui.components.keepsKeyboard
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType

/**
 * 用户自建的 App 说明(在「技能」段里、App 知识下面):一份说明管一个 App/游戏,按包名认。
 * 正文(APP.md)和每个附件都能在这里改;保存时整份校验,不过只提示、不落盘(见 MemoryViewModel.saveGuide)。
 */
@Composable
internal fun GuidesSection(vm: MemoryViewModel, guides: List<GuideRow>) {
    var editingMain by remember { mutableStateOf<GuideRow?>(null) }
    var filesFor by remember { mutableStateOf<GuideRow?>(null) }
    var editingFile by remember { mutableStateOf<Pair<GuideRow, String>?>(null) }
    var addingFile by remember { mutableStateOf<GuideRow?>(null) }
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    GroupLabel(stringResource(R.string.guides_title))
    GroupCard {
        if (guides.isEmpty()) EmptyNote(stringResource(R.string.guides_empty))
        guides.forEachIndexed { i, g ->
            Column(Modifier.testTag("guide-row")) {
                ListRow(null, g.display, g.packageName.ifBlank { null }, first = i == 0,
                    detail = if (g.valid) g.description.ifBlank { null } else stringResource(R.string.guides_invalid))
                RowActions {
                    MikaButton(stringResource(R.string.guides_edit_main), { editingMain = g }, kind = ButtonKind.Tonal, size = ButtonSize.Small)
                    MikaButton(stringResource(R.string.guides_files_btn, g.files.size), { filesFor = g }, kind = ButtonKind.Tonal, size = ButtonSize.Small)
                    if (confirmDelete == g.id) {
                        MikaButton(stringResource(R.string.guides_delete_confirm), { vm.deleteGuide(g.id); confirmDelete = null }, size = ButtonSize.Small)
                        MikaButton(stringResource(R.string.cancel), { confirmDelete = null }, kind = ButtonKind.Tonal, size = ButtonSize.Small)
                    } else {
                        MikaButton(stringResource(R.string.guides_delete), { confirmDelete = g.id }, kind = ButtonKind.Tonal, size = ButtonSize.Small)
                    }
                }
            }
        }
    }
    GroupCard {
        ListRow(null, stringResource(R.string.guides_new), stringResource(R.string.guides_new_sub), first = true, onClick = { picking = true }) {
            RowChevron()
        }
    }

    editingMain?.let { g ->
        var text by remember(g.id) { mutableStateOf(g.markdown) }
        MikaDialog(
            onDismissRequest = { editingMain = null },
            title = { Text(stringResource(R.string.guides_edit_main)) },
            text = { KeyboardDismissArea { OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 240.dp).keepsKeyboard().testTag("guide-markdown")) } },
            confirmButton = { TextButton({ vm.saveGuideMain(g.id, text); editingMain = null }) { Text(stringResource(R.string.guides_save)) } },
            dismissButton = { TextButton({ editingMain = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    filesFor?.let { picked ->
        val g = guides.find { it.id == picked.id } ?: picked
        MikaDialog(
            onDismissRequest = { filesFor = null },
            title = { Text(stringResource(R.string.guides_files_title, g.display)) },
            text = {
                Column {
                    if (g.files.isEmpty()) Text(stringResource(R.string.guides_no_files), style = MikaType.Hint, color = MikaTheme.colors.ink3)
                    g.files.keys.sorted().forEach { path ->
                        ListRow(null, path, null, first = false, onClick = { editingFile = g to path })
                    }
                    Text(stringResource(R.string.guides_files_hint), Modifier.padding(top = 8.dp), style = MikaType.Hint, color = MikaTheme.colors.ink3)
                }
            },
            confirmButton = { TextButton({ addingFile = g }) { Text(stringResource(R.string.guides_add_file)) } },
            dismissButton = { TextButton({ filesFor = null }) { Text(stringResource(R.string.guides_close)) } },
        )
    }

    editingFile?.let { (picked, path) ->
        val g = guides.find { it.id == picked.id } ?: picked
        var text by remember(g.id, path) { mutableStateOf(g.files[path].orEmpty()) }
        MikaDialog(
            onDismissRequest = { editingFile = null },
            title = { Text(path) },
            text = { KeyboardDismissArea { OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 240.dp).keepsKeyboard().testTag("guide-file")) } },
            confirmButton = { TextButton({ vm.saveGuideFile(g.id, path, text); editingFile = null }) { Text(stringResource(R.string.guides_save)) } },
            dismissButton = {
                Row {
                    TextButton({ vm.deleteGuideFile(g.id, path); editingFile = null }) { Text(stringResource(R.string.guides_delete)) }
                    TextButton({ editingFile = null }) { Text(stringResource(R.string.cancel)) } }
            },
        )
    }

    addingFile?.let { g ->
        var name by remember(g.id) { mutableStateOf("") }
        var problem by remember(g.id) { mutableStateOf<String?>(null) }
        MikaDialog(
            onDismissRequest = { addingFile = null },
            title = { Text(stringResource(R.string.guides_add_file)) },
            text = {
                KeyboardDismissArea {
                    Column {
                        OutlinedTextField(name, { name = it; problem = null }, Modifier.fillMaxWidth().keepsKeyboard().testTag("guide-file-name"),
                            label = { Text(stringResource(R.string.guides_file_name)) })
                        Text(stringResource(R.string.guides_file_name_hint), Modifier.padding(top = 6.dp), style = MikaType.Hint, color = MikaTheme.colors.ink3)
                        problem?.let { Text(it, Modifier.padding(top = 6.dp), style = MikaType.Hint, color = MikaTheme.colors.bad) }
                    }
                }
            },
            confirmButton = {
                TextButton({ problem = vm.addGuideFile(g.id, name); if (problem == null) addingFile = null }, enabled = name.isNotBlank()) {
                    Text(stringResource(R.string.guides_create))
                }
            },
            dismissButton = { TextButton({ addingFile = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (picking) {
        val candidates = vm.guideCandidates()
        MikaDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(R.string.guides_pick_app)) },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    if (candidates.isEmpty()) Text(stringResource(R.string.guides_no_candidates), style = MikaType.Hint, color = MikaTheme.colors.ink3)
                    candidates.forEachIndexed { i, app ->
                        ListRow(null, app.label, app.packageName, first = i == 0, onClick = { vm.createGuide(app.packageName, app.label); picking = false })
                    }
                }
            },
            dismissButton = { TextButton({ picking = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
