package org.androidlabs.filewall.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Section(
    header: String,
    footer: String? = null,
    content: @Composable () -> Unit
) {
    val color = MaterialTheme.colorScheme.onSurface.copy(0.9f)
    val fontSize = 14.sp
    val smallerPadding = 8.dp
    val biggerPadding = 16.dp

    Column {
        Text(
            text = header,
            color = color,
            fontSize = fontSize,
            modifier = Modifier.padding(horizontal = biggerPadding, vertical = smallerPadding)
        )

        content()

        footer?.let {
            Text(
                text = footer,
                color = color,
                fontSize = fontSize,
                modifier = Modifier.padding(
                    top = smallerPadding,
                    bottom = biggerPadding,
                    start = biggerPadding,
                    end = biggerPadding
                )
            )
        } ?: run {
            Spacer(modifier = Modifier.height(biggerPadding))
        }
    }
}
