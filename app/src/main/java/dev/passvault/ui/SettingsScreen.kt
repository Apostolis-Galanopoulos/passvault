package dev.passvault.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.passvault.AUTO_LOCK_MS
import dev.passvault.CLIPBOARD_CLEAR_MS
import dev.passvault.MIN_MASTER_PASSWORD_LENGTH
import dev.passvault.VaultViewModel
import dev.passvault.crypto.VaultCrypto
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: VaultViewModel, snackbar: SnackbarHostState, onBack: () -> Unit) {
    var showChangePassword by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> uri?.let(vm::exportBackup) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importBackup)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            ListItem(
                headlineContent = { Text("Change master password") },
                supportingContent = { Text("Re-encrypts the vault with a new key") },
                modifier = Modifier.clickable(enabled = !vm.busy) { showChangePassword = true },
            )
            ListItem(
                headlineContent = { Text("Export encrypted backup") },
                supportingContent = { Text("Saves the encrypted vault file. You need its master password to restore it.") },
                modifier = Modifier.clickable(enabled = !vm.busy) { exportLauncher.launch("passvault-backup.pvlt") },
            )
            ListItem(
                headlineContent = { Text("Restore from backup") },
                supportingContent = { Text("Replaces this vault with a backup file") },
                modifier = Modifier.clickable(enabled = !vm.busy) { confirmRestore = true },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Security") },
                supportingContent = {
                    Text(
                        "Entries are encrypted with AES-256-GCM. The key is derived from your master password " +
                            "using PBKDF2-HMAC-SHA256 with ${NumberFormat.getInstance().format(VaultCrypto.DEFAULT_ITERATIONS)} iterations " +
                            "and never stored. The vault locks after ${AUTO_LOCK_MS / 1000} s in the background, " +
                            "copied secrets are cleared from the clipboard after ${CLIPBOARD_CLEAR_MS / 1000} s, " +
                            "and screenshots are blocked."
                    )
                },
            )
        }
    }

    if (showChangePassword) {
        ChangePasswordDialog(
            onDismiss = { showChangePassword = false },
            onConfirm = { current, new ->
                vm.changePassword(current, new)
                showChangePassword = false
            },
        )
    }
    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore backup?") },
            text = { Text("All entries in the current vault will be replaced by the backup. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    importLauncher.launch(arrayOf("*/*"))
                }) { Text("Choose file") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val tooShort = new.isNotEmpty() && new.length < MIN_MASTER_PASSWORD_LENGTH
    val mismatch = confirm.isNotEmpty() && confirm != new
    val valid = current.isNotEmpty() && new.length >= MIN_MASTER_PASSWORD_LENGTH && new == confirm

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change master password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PasswordField(current, { current = it }, "Current password", imeAction = ImeAction.Next)
                PasswordField(
                    new, { new = it }, "New password",
                    isError = tooShort,
                    supportingText = if (tooShort) "At least $MIN_MASTER_PASSWORD_LENGTH characters" else null,
                    imeAction = ImeAction.Next,
                )
                PasswordField(
                    confirm, { confirm = it }, "Confirm new password",
                    isError = mismatch,
                    supportingText = if (mismatch) "Passwords don't match" else null,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current, new) }, enabled = valid) { Text("Change") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
