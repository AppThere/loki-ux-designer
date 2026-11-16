package app.loki.figthere.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.loki.kit.SheetDialog
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Batch rename batch rename dialog: translated from `dialogs/batch_rename.rs` (fn `batch_rename_dialog`, line 41) by egui2compose.
 *
 * The controls and their order are the desktop dialog's. NOT translated: the starting values (the
 * desktop reads them from its own state), anything it draws by hand, and enabling rules. The
 * parameter names are guessed from the Rust fields each control was bound to: check them against
 * the command's params doc before trusting OK.
 *   command: object.batchRename
 *   params:  {ids?, find? (match; empty: none), regex? (find is a regular expression), replace? (for each match), name? (pattern for the whole name), start? (where $n starts and $N stops; default 1)} codes in replace and name: $& current name (or the match), $` and $' text before and after the match, $1…$9 group
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BatchRenameBatchRenameDialogDialog(onRun: (command: String, params: JsonObject) -> Unit, onDismiss: () -> Unit) {
    var f0 by remember { mutableStateOf("") }
    var f1 by remember { mutableStateOf(false) }
    var f2 by remember { mutableStateOf("") }
    var f3 by remember { mutableStateOf("") }
    SheetDialog(
        "Batch rename batch rename dialog", "", onDismiss,
        buttons = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TextButton(onClick = {
                val params = buildJsonObject {
                if (f0.isNotBlank()) put("find", f0)
                put("ui", f1)
                if (f2.isNotBlank()) put("to", f2)
                f3.trim().replace(',', '.').toDoubleOrNull()?.let { put("start", it) }
                }
                onRun("object.batchRename", params)
                onDismiss()
            }) { Text("OK") }
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(f0, { f0 = it }, label = { Text("Find") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().clickable { f1 = !f1 }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(f1, { f1 = it })
                Text("Ui")
            }
            OutlinedTextField(f2, { f2 = it }, label = { Text("To") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(f3, { f3 = it }, label = { Text("Start ascending from / stop descending at") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        }
    }
}
