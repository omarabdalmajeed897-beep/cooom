package com.compressor.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.compressor.app.pdf.AppLanguage
import com.compressor.app.pdf.CompressionLevel
import com.compressor.app.pdf.ThemePreference
import com.compressor.app.ui.theme.CompressorTheme
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: CompressorViewModel = viewModel()
            val state by vm.uiState.collectAsState()

            val layoutDirection = if (state.language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                CompressorTheme(preference = state.themePreference) {
                    CompressorAppShell(viewModel = vm)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressorAppShell(viewModel: CompressorViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var pendingSaveFile by remember { mutableStateOf<File?>(null) }

    val pickPdfsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addSelectedUris(uris)
        }
    }

    // Native Android folder/location picker ("Save As..." into Downloads, Documents, Drive, etc.)
    val saveToFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { targetUri ->
        val source = pendingSaveFile
        pendingSaveFile = null
        if (targetUri != null && source != null && source.exists()) {
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                source.inputStream().use { input -> input.copyTo(out) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    TextButton(onClick = { viewModel.toggleLanguage() }) {
                        Text(if (state.language == AppLanguage.ENGLISH) "العربية" else "EN")
                    }
                    IconButton(onClick = { viewModel.cycleTheme() }) {
                        Icon(
                            imageVector = when (state.themePreference) {
                                ThemePreference.DARK -> Icons.Outlined.DarkMode
                                ThemePreference.LIGHT -> Icons.Outlined.LightMode
                                ThemePreference.SYSTEM -> Icons.Outlined.BrightnessAuto
                            },
                            contentDescription = stringResource(R.string.theme_toggle)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.select_pdfs_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.local_privacy_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { pickPdfsLauncher.launch(arrayOf("application/pdf")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.UploadFile, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.select_pdfs_btn))
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.compression_level),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    CompressionLevel.entries.forEachIndexed { index, level ->
                        SegmentedButton(
                            selected = state.selectedLevel == level,
                            onClick = { viewModel.setCompressionLevel(level) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = CompressionLevel.entries.size
                            )
                        ) {
                            Text(
                                when (level) {
                                    CompressionLevel.HIGH -> stringResource(R.string.level_high)
                                    CompressionLevel.MEDIUM -> stringResource(R.string.level_medium)
                                    CompressionLevel.LOW -> stringResource(R.string.level_low)
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.estimate_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(state.queue, key = { it.id }) { item ->
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = item.originalName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        AnimatedVisibility(visible = item.isCompressing) {
                            LinearProgressIndicator(
                                progress = { item.progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item.compressedFile?.let { file ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    pendingSaveFile = file
                                    saveToFolderLauncher.launch(file.name)
                                }) {
                                    Text(stringResource(R.string.action_save))
                                }
                                OutlinedButton(onClick = {
                                    shareCompressedPdf(context, file)
                                }) {
                                    Text(stringResource(R.string.action_share))
                                }
                                OutlinedButton(onClick = {
                                    openCompressedPdf(context, file)
                                }) {
                                    Text(stringResource(R.string.action_open))
                                }
                            }
                        }
                    }
                }
            }

            if (state.queue.isNotEmpty()) {
                item {
                    Button(
                        onClick = { viewModel.compressAll() },
                        enabled = !state.isBatchCompressing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(stringResource(R.string.compress_all_btn))
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

private fun shareCompressedPdf(context: android.content.Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, file.name))
}

private fun openCompressedPdf(context: android.content.Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, file.name))
}
