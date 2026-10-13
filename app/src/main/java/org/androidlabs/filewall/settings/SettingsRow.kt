package org.androidlabs.filewall.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsRow(
    title: String,
    subtitle: String?,
    iconView: @Composable () -> Unit,
    rightView: @Composable () -> Unit,
    onClick: (() -> Unit)? = null,
    isDisabledText: Boolean = false
) {
    val isDark = isSystemInDarkTheme()
    val textColor = when {
        isDisabledText -> Color.Gray
        isDark -> Color.White
        else -> Color.Black
    }
    var modifier = Modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 56.dp)
    onClick?.let {
        modifier = modifier.clickable { it() }
    }
    Row(
        modifier = modifier.padding(vertical = 8.dp, horizontal = 16.dp).semantics(mergeDescendants = true){},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(24.dp)) {
            iconView()
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.SemiBold, color = textColor)
            subtitle?.let {
                Text(text = it,color = textColor)
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        rightView()
    }
}
