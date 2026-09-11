package gg.hydroid.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Paleta Hydroid — indigo aguado + teal
private val Aqua = Color(0xFF5EEAD4)
private val Indigo = Color(0xFF8B93FF)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFB4B9FF),
    onPrimary = Color(0xFF1B1D45),
    primaryContainer = Color(0xFF343876),
    onPrimaryContainer = Color(0xFFDFE0FF),
    secondary = Aqua,
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF005048),
    onSecondaryContainer = Color(0xFF9AFFF0),
    tertiary = Color(0xFFF3B0D0),
    onTertiary = Color(0xFF4A1333),
    background = Color(0xFF0F1016),
    onBackground = Color(0xFFE4E3EC),
    surface = Color(0xFF0F1016),
    onSurface = Color(0xFFE4E3EC),
    surfaceVariant = Color(0xFF23242E),
    onSurfaceVariant = Color(0xFFC5C5D2),
    surfaceContainer = Color(0xFF171821),
    surfaceContainerHigh = Color(0xFF1D1E28),
    surfaceContainerHighest = Color(0xFF242532),
    outline = Color(0xFF5A5C6B),
    outlineVariant = Color(0xFF2E3040),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF4A51C4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDFE0FF),
    onPrimaryContainer = Color(0xFF000C63),
    secondary = Color(0xFF0F6F65),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF9CF2E4),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFF8B4A6E),
    onTertiary = Color.White,
    background = Color(0xFFFAF9FF),
    onBackground = Color(0xFF1A1B22),
    surface = Color(0xFFFAF9FF),
    onSurface = Color(0xFF1A1B22),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    surfaceContainer = Color(0xFFF0EFF7),
    surfaceContainerHigh = Color(0xFFEAE9F1),
    surfaceContainerHighest = Color(0xFFE4E3EC),
    outline = Color(0xFF777680),
    outlineVariant = Color(0xFFC7C5D0),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val HydroidShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun HydroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        shapes = HydroidShapes,
        content = content
    )
}
