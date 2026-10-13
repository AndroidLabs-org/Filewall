package org.androidlabs.filewall.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Badge(
    title: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .background(color = color, shape = MaterialTheme.shapes.small)
    ) {
        Text(title, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp))
    }
}