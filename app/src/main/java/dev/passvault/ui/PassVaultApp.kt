package dev.passvault.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.passvault.MIN_MASTER_PASSWORD_LENGTH
import dev.passvault.VaultViewModel
import dev.passvault.VaultViewModel.Status

private const val ROUTE_LIST = "list"
private const val ROUTE_NEW = "new"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_EDIT_PREFIX = "edit:"

@Composable
fun PassVaultApp(vm: VaultViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        val text = vm.message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        vm.consumeMessage()
    }

    when (vm.status) {
        Status.NO_VAULT -> SetupScreen(vm, snackbar)
        Status.LOCKED -> UnlockScreen(vm, snackbar)
        Status.UNLOCKED -> UnlockedVault(vm, snackbar)
    }
}

@Composable
private fun UnlockedVault(vm: VaultViewModel, snackbar: SnackbarHostState) {
    var route by rememberSaveable { mutableStateOf(ROUTE_LIST) }
    BackHandler(enabled = route != ROUTE_LIST) { route = ROUTE_LIST }

    when {
        route == ROUTE_LIST -> EntryListScreen(
            entries = vm.entries,
            snackbar = snackbar,
            onOpen = { route = ROUTE_EDIT_PREFIX + it },
            onAdd = { route = ROUTE_NEW },
            onSettings = { route = ROUTE_SETTINGS },
            onLock = vm::lock,
        )
        route == ROUTE_SETTINGS -> SettingsScreen(vm, snackbar, onBack = { route = ROUTE_LIST })
        else -> {
            val id = route.removePrefix(ROUTE_EDIT_PREFIX)
            val entry = vm.entries.find { it.id == id }
            if (route != ROUTE_NEW && entry == null) {
                LaunchedEffect(Unit) { route = ROUTE_LIST }
                return
            }
            EditEntryScreen(
                entry = entry,
                snackbar = snackbar,
                onSave = { vm.upsert(it); route = ROUTE_LIST },
                onDelete = { vm.delete(it); route = ROUTE_LIST },
                onBack = { route = ROUTE_LIST },
            )
        }
    }
}

@Composable
private fun SetupScreen(vm: VaultViewModel, snackbar: SnackbarHostState) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val tooShort = password.isNotEmpty() && password.length < MIN_MASTER_PASSWORD_LENGTH
    val mismatch = confirm.isNotEmpty() && confirm != password
    val canSubmit = password.length >= MIN_MASTER_PASSWORD_LENGTH && password == confirm && !vm.busy
    val submit = {
        if (canSubmit) {
            vm.create(password)
            password = ""
            confirm = ""
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importBackup)
    }

    CenteredForm(snackbar, busy = vm.busy) {
        Text("Create your vault", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Choose a strong master password. It is the only key to your vault and cannot be recovered if you forget it.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        PasswordField(
            value = password,
            onValueChange = { password = it },
            label = "Master password",
            isError = tooShort,
            supportingText = if (tooShort) "At least $MIN_MASTER_PASSWORD_LENGTH characters" else null,
            imeAction = ImeAction.Next,
        )
        PasswordField(
            value = confirm,
            onValueChange = { confirm = it },
            label = "Confirm master password",
            isError = mismatch,
            supportingText = if (mismatch) "Passwords don't match" else null,
            onImeAction = submit,
        )
        Button(onClick = submit, enabled = canSubmit, modifier = Modifier.fillMaxWidth()) {
            Text("Create vault")
        }
        TextButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, enabled = !vm.busy) {
            Text("Restore from backup file")
        }
    }
}

@Composable
private fun UnlockScreen(vm: VaultViewModel, snackbar: SnackbarHostState) {
    var password by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val submit = {
        if (password.isNotEmpty() && !vm.busy) {
            vm.unlock(password)
            password = ""
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    CenteredForm(snackbar, busy = vm.busy) {
        Text("Vault locked", style = MaterialTheme.typography.headlineMedium)
        PasswordField(
            value = password,
            onValueChange = { password = it },
            label = "Master password",
            onImeAction = submit,
            modifier = Modifier.focusRequester(focus),
        )
        Button(
            onClick = submit,
            enabled = password.isNotEmpty() && !vm.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (vm.busy) "Unlocking..." else "Unlock")
        }
    }
}

@Composable
private fun CenteredForm(
    snackbar: SnackbarHostState,
    busy: Boolean,
    content: @Composable () -> Unit,
) {
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp),
                )
                content()
                if (busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}
