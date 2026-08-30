package com.phonelm.ui

import android.content.Context
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@Composable
fun HomeScreen(
    onModelSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val models = remember {
        mutableStateListOf<File>()
    }

    LaunchedEffect(Unit) {
        // D4 mechanism: copy bundled asset GGUFs to filesDir once so the
        // engine (which needs a real file path) can load them offline.
        try {
            val bundledDir = com.phonelm.core.ModelLocator.bundledCopyDir(context.filesDir)
            if (!bundledDir.exists()) bundledDir.mkdirs()
            context.assets.list(com.phonelm.core.ModelLocator.ASSETS_MODEL_DIR)?.forEach { name ->
                val out = File(bundledDir, name)
                if (!out.exists() || out.length() == 0L) {
                    context.assets.open("${com.phonelm.core.ModelLocator.ASSETS_MODEL_DIR}/$name").use { input ->
                        out.outputStream().use { input.copyTo(it) }
                    }
                }
            }
        } catch (_: Exception) {
            // No bundled models present — Downloads scan below still applies.
        }

        val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "PhoneLM")
        val candidates = buildList {
            com.phonelm.core.ModelLocator.bundledCopyDir(context.filesDir).listFiles()?.let { addAll(it) }
            downloadDir.listFiles()?.let { addAll(it) }
        }
        val resolved = com.phonelm.core.ModelLocator.resolveModel(candidates)
        models.clear()
        resolved?.let { models.add(it) }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text(
            text = "Model Gallery",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (models.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No .gguf models found in Downloads/PhoneLM")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(models) { model ->
                    ModelCard(model, onModelSelected)
                }
            }
        }
    }
}

@Composable
fun ModelCard(file: File, onClick: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clickable { onClick(file.absolutePath) },
        shape = RoundedCornerShape(24.dp), // User requested 24dp rounded corners
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = Icons.Default.SdStorage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${file.length() / 1024 / 1024} MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
