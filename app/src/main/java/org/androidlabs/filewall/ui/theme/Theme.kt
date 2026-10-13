package org.androidlabs.filewall.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Immutable
data class CustomColorsPalette(
    val green: Color = Color.Unspecified,
    val red: Color = Color.Unspecified,
    val greenDark: Color = Color.Unspecified,
    val redDark: Color = Color.Unspecified,
    val readColor: Color = Color.Unspecified,
    val createColor: Color = Color.Unspecified,
    val deleteColor: Color = Color.Unspecified,
    val renameColor: Color = Color.Unspecified,
    val copyColor: Color = Color.Unspecified,
    val moveColor: Color = Color.Unspecified,
    val writeColor: Color = Color.Unspecified,
    val readWriteColor: Color = Color.Unspecified,
    val warningColor: Color = Color.Unspecified,
)

val LocalCustomColorsPalette = staticCompositionLocalOf { CustomColorsPalette() }

val OnLightCustomColorsPalette = CustomColorsPalette(
    green = GreenLight,
    red = RedLight,
    greenDark = GreenDarkLight,
    redDark = RedDarkLight,
    readColor = ReadLight,
    createColor = CreateLight,
    deleteColor = DeleteLight,
    renameColor = RenameLight,
    copyColor = CopyLight,
    moveColor = MoveLight,
    writeColor = WriteLight,
    readWriteColor = ReadWriteLight,
    warningColor = WarningLight,
)

val OnDarkCustomColorsPalette = CustomColorsPalette(
    green = GreenDark,
    red = RedDark,
    greenDark = GreenDarkDark,
    redDark = RedDarkDark,
    readColor = ReadDark,
    createColor = CreateDark,
    deleteColor = DeleteDark,
    renameColor = RenameDark,
    copyColor = CopyDark,
    moveColor = MoveDark,
    writeColor = WriteDark,
    readWriteColor = ReadWriteDark,
    warningColor = WarningDark,
)

@Composable
fun FilewallTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val customColorsPalette =
        if (darkTheme) OnDarkCustomColorsPalette
        else OnLightCustomColorsPalette

    CompositionLocalProvider(
        LocalCustomColorsPalette provides customColorsPalette
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}