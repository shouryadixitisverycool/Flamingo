package yos.music.player.ui.pages.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yos.music.player.R
import yos.music.player.code.ListenStatsManager
import yos.music.player.ui.widgets.basic.RoundColumn
import yos.music.player.ui.widgets.basic.YosBottomSheetDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatsDataSection()
{
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val showClearConfirmDialog = remember { mutableStateOf(false) }
    val pendingImportUri = remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { destinationUri ->
        if (destinationUri == null) {return@rememberLauncherForActivityResult}
        scope.launch(Dispatchers.IO) {
            val exportSucceeded = runCatching {
                context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                    outputStream.write(ListenStatsManager.exportEventsAsJson().toByteArray())
                } ?: throw IllegalStateException("openOutputStream returned null")
            }.isSuccess

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    if (exportSucceeded) R.string.settings_stats_export_done_toast else R.string.settings_stats_export_failed_toast,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { sourceUri ->
        if (sourceUri != null) {pendingImportUri.value = sourceUri}
    }

    RoundColumn {
        LabelItem(
            title = stringResource(id = R.string.settings_stats_export),
            superLink = true
        ) {
            val fileTimestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            exportLauncher.launch("flamingo_stats_$fileTimestamp.json")
        }
        Divider()
        LabelItem(
            title = stringResource(id = R.string.settings_stats_import),
            superLink = true
        ) {
            importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        }
        Divider()
        LabelItem(
            title = stringResource(id = R.string.settings_stats_clear),
            superLink = true
        ) {
            showClearConfirmDialog.value = true
        }
    }

    if (showClearConfirmDialog.value)
    {
        StatsClearConfirmDialog(showClearConfirmDialog)
    }

    if (pendingImportUri.value != null)
    {
        StatsImportModeDialog(pendingImportUri)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsClearConfirmDialog(showDialog: MutableState<Boolean>)
{
    val context = LocalContext.current

    YosBottomSheetDialog(onDismissRequest = { showDialog.value = false }) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 18.dp)
        ) {
            Text(
                text = stringResource(id = R.string.settings_stats_clear_confirm_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(id = R.string.settings_stats_clear_confirm_message),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(Modifier.padding(top = 20.dp)) {
                Text(
                    text = stringResource(id = R.string.settings_stats_cancel),
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp)
                        .clickableNoIndication { showDialog.value = false }
                )
                Text(
                    text = stringResource(id = R.string.settings_stats_clear_confirm_action),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp)
                        .clickableNoIndication {
                            ListenStatsManager.clearAllEvents()
                            showDialog.value = false
                            Toast.makeText(context, R.string.settings_stats_clear_done_toast, Toast.LENGTH_SHORT).show()
                        }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsImportModeDialog(pendingImportUri: MutableState<Uri?>)
{
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val performImport: (Boolean) -> Unit = { replaceExisting ->
        val sourceUri = pendingImportUri.value
        pendingImportUri.value = null
        if (sourceUri != null)
        {
            scope.launch(Dispatchers.IO) {
                val importedCount = runCatching {
                    context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                        ListenStatsManager.importEventsFromJson(
                            inputStream.bufferedReader().readText(),
                            replaceExisting
                        )
                    } ?: -1
                }.getOrDefault(-1)

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        if (importedCount >= 0) R.string.settings_stats_import_done_toast else R.string.settings_stats_import_failed_toast,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    YosBottomSheetDialog(onDismissRequest = { pendingImportUri.value = null }) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 18.dp)
        ) {
            Text(
                text = stringResource(id = R.string.settings_stats_import_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(id = R.string.settings_stats_import_message),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(Modifier.padding(top = 20.dp)) {
                Text(
                    text = stringResource(id = R.string.settings_stats_cancel),
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp)
                        .clickableNoIndication { pendingImportUri.value = null }
                )
                Text(
                    text = stringResource(id = R.string.settings_stats_import_merge),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp)
                        .clickableNoIndication { performImport(false) }
                )
                Text(
                    text = stringResource(id = R.string.settings_stats_import_replace),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp)
                        .clickableNoIndication { performImport(true) }
                )
            }
        }
    }
}

@Composable
private fun Modifier.clickableNoIndication(onClick: () -> Unit): Modifier
{
    val interactionSource = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick
    )
}
