package dev.passvault.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.passvault.CLIPBOARD_CLEAR_MS
import dev.passvault.SecureClipboard
import dev.passvault.crypto.PasswordGenerator
import dev.passvault.data.Entry
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEntryScreen(
    entry: Entry?,
    snackbar: SnackbarHostState,
    onSave: (Entry) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
) {
    val original = remember { entry ?: Entry() }
    var title by remember { mutableStateOf(original.title) }
    var username by remember { mutableStateOf(original.username) }
    var password by remember { mutableStateOf(original.password) }
    var url by remember { mutableStateOf(original.url) }
    var notes by remember { mutableStateOf(original.notes) }
    var showGenerator by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val edited = original.copy(title = title.trim(), username = username, password = password, url = url.trim(), notes = notes)
    val dirty = edited.copy(updatedAt = original.updatedAt) != original
    val canSave = title.isNotBlank()
    val back = { if (dirty) confirmDiscard = true else onBack() }
    BackHandler(onBack = back)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val copy = { label: String, value: String ->
        SecureClipboard.copy(context, label, value)
        scope.launch { snackbar.showSnackbar("$label copied, clears in ${CLIPBOARD_CLEAR_MS / 1000} s") }
        Unit
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (entry == null) "New entry" else "Edit entry") },
                navigationIcon = {
                    IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (entry != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete entry") }
                    }
                    IconButton(onClick = { onSave(edited) }, enabled = canSave) { Icon(Icons.Filled.Check, "Save") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                isError = title.isBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false),
                trailingIcon = {
                    if (username.isNotEmpty()) {
                        IconButton(onClick = { copy("Username", username) }) { Icon(Icons.Filled.ContentCopy, "Copy username") }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            PasswordField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                extraActions = {
                    IconButton(onClick = { showGenerator = true }) { Icon(Icons.Filled.Refresh, "Generate password") }
                    if (password.isNotEmpty()) {
                        IconButton(onClick = { copy("Password", password) }) { Icon(Icons.Filled.ContentCopy, "Copy password") }
                    }
                },
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showGenerator) {
        GeneratorDialog(
            onDismiss = { showGenerator = false },
            onUse = {
                password = it
                showGenerator = false
            },
        )
    }
    if (confirmDelete && entry != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete entry?") },
            text = { Text("\"${entry.title}\" will be permanently deleted.") },
            confirmButton = { TextButton(onClick = { onDelete(entry.id) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            confirmButton = { TextButton(onClick = onBack) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
}

@Composable
private fun GeneratorDialog(onDismiss: () -> Unit, onUse: (String) -> Unit) {
    var length by remember { mutableFloatStateOf(20f) }
    var lower by remember { mutableStateOf(true) }
    var upper by remember { mutableStateOf(true) }
    var digits by remember { mutableStateOf(true) }
    var symbols by remember { mutableStateOf(true) }
    var nonce by remember { mutableIntStateOf(0) }

    val options = PasswordGenerator.Options(length.toInt(), lower, upper, digits, symbols)
    val generated = remember(options, nonce) {
        if (options.isValid) PasswordGenerator.generate(options) else ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    generated.ifEmpty { "Select at least one character set" },
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text("Length: ${length.toInt()}", style = MaterialTheme.typography.labelLarge)
                Slider(value = length, onValueChange = { length = it }, valueRange = 8f..64f, steps = 55)
                CheckRow("Lowercase (a-z)", lower) { lower = it }
                CheckRow("Uppercase (A-Z)", upper) { upper = it }
                CheckRow("Digits (0-9)", digits) { digits = it }
                CheckRow("Symbols (!@#...)", symbols) { symbols = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onUse(generated) }, enabled = generated.isNotEmpty()) { Text("Use") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { nonce++ }) { Text("Regenerate") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}
