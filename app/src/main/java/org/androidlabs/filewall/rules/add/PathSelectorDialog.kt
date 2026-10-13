package org.androidlabs.filewall.rules.add

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.androidlabs.filewall.R
import org.androidlabs.filewall.provider.FilewallProvider
import java.io.File

@Composable
fun PathSelectorDialog(
    fileItem: FileItem,
    onDismiss: () -> Unit,
    onItemSelected: (FileItem) -> Unit
) {
    val context = LocalContext.current
    var currentDir by remember { mutableStateOf(fileItem.folderPath) }
    var fileList by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var navigationHistory by remember { mutableStateOf(listOf("/")) }
    var canNavigateUp by remember { mutableStateOf(currentDir != "/") }
    var selectedFileIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(currentDir) {
        val directory = File(FilewallProvider.getBaseDir(context), currentDir)
        val files = directory.listFiles()
            ?.map {
                FileItem(
                    name = it.name,
                    path = it.absolutePath,
                    isDirectory = it.isDirectory
                )
            }
            ?.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.name.lowercase() })
            ?: emptyList()

        fileList = files
        canNavigateUp = currentDir != "/"

        selectedFileIndex = -1
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.select_path),
                    style = MaterialTheme.typography.headlineSmall,
                )

                Row(
                    modifier = Modifier
                        .height(48.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canNavigateUp) {
                        IconButton(
                            modifier = Modifier.width(24.dp),
                            onClick = {
                                if (navigationHistory.isNotEmpty()) {
                                    val previous = navigationHistory.dropLast(1)
                                    navigationHistory = previous
                                    currentDir = previous.lastOrNull() ?: "/"
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.ic_home_24),
                            contentDescription = stringResource(R.string.folder),
                            colorFilter = ColorFilter.tint(
                                MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "${stringResource(R.string.path)}: $currentDir",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                HorizontalDivider()

                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    itemsIndexed(fileList) { index, file ->
                        val isSelected = index == selectedFileIndex

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (file.isDirectory) {
                                        navigationHistory = navigationHistory + currentDir
                                        currentDir = file.path.removePrefix(
                                            FilewallProvider.getBaseDir(context).path
                                        )
                                    } else {
                                        selectedFileIndex = if (index != selectedFileIndex) {
                                            index
                                        } else {
                                            -1
                                        }
                                    }
                                }
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                                )
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = if (file.isDirectory) R.drawable.ic_folder_24 else R.drawable.ic_file_24),
                                contentDescription = stringResource(if (file.isDirectory) R.string.folder else R.string.file),
                                colorFilter = ColorFilter.tint(
                                    if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (fileList.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Folder is empty",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val buttonText = if (selectedFileIndex >= 0) {
                        stringResource(R.string.select_this_file)
                    } else {
                        stringResource(R.string.select_this_folder)
                    }

                    Button(
                        modifier = Modifier
                            .fillMaxWidth(),
                        onClick = {
                            if (selectedFileIndex >= 0) {
                                val selectedFile = fileList[selectedFileIndex]
                                val item = FileItem(
                                    name = selectedFile.name,
                                    path = selectedFile.path.removePrefix(
                                        FilewallProvider.getBaseDir(context).path
                                    ),
                                    isDirectory = false
                                )
                                onItemSelected(item)
                            } else {
                                val item = FileItem(
                                    name = currentDir,
                                    path = currentDir,
                                    isDirectory = true
                                )
                                onItemSelected(item)
                            }
                        }
                    ) {
                        Text(buttonText)
                    }
                }
            }
        }
    }
}