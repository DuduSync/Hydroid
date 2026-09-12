package gg.hydroid.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import gg.hydroid.app.data.store.AppStore

// Paleta Hydroid - indigo aguado + teal
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

// preto puro: telas AMOLED economizam bateria de verdade
private val AmoledScheme = darkColorScheme(
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
    background = Color(0xFF000000),
    onBackground = Color(0xFFE4E3EC),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFE4E3EC),
    surfaceVariant = Color(0xFF16171E),
    onSurfaceVariant = Color(0xFFC5C5D2),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF050507),
    surfaceContainer = Color(0xFF0A0A0E),
    surfaceContainerHigh = Color(0xFF101015),
    surfaceContainerHighest = Color(0xFF16161C),
    outline = Color(0xFF4A4C59),
    outlineVariant = Color(0xFF22232C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

// vidro: superficies translucidas sobre um fundo com bolhas coloridas (GlassBackground)
private val GlassScheme = darkColorScheme(
    primary = Color(0xFF7DF3DE),
    onPrimary = Color(0xFF00201A),
    primaryContainer = Color(0x3322C7B0),
    onPrimaryContainer = Color(0xFFD6FFF7),
    secondary = Color(0xFF9BA6FF),
    onSecondary = Color(0xFF10143A),
    secondaryContainer = Color(0x333B44C9),
    onSecondaryContainer = Color(0xFFE0E3FF),
    tertiary = Color(0xFFF3B0D0),
    onTertiary = Color(0xFF4A1333),
    background = Color.Transparent,
    onBackground = Color(0xFFEAF0F6),
    surface = Color.Transparent,
    onSurface = Color(0xFFEAF0F6),
    surfaceVariant = Color(0xFF1E2028).copy(alpha = 0.80f),
    onSurfaceVariant = Color(0xFFC9D2DC),
    surfaceContainerLowest = Color(0xFF101219).copy(alpha = 0.55f),
    surfaceContainerLow = Color(0xFF13151C).copy(alpha = 0.62f),
    surfaceContainer = Color(0xFF161920).copy(alpha = 0.72f),
    surfaceContainerHigh = Color(0xFF181B23).copy(alpha = 0.90f),
    surfaceContainerHighest = Color(0xFF1C1F27).copy(alpha = 0.95f),
    outline = Color.White.copy(alpha = 0.28f),
    outlineVariant = Color.White.copy(alpha = 0.14f),
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
fun HydroidTheme(content: @Composable () -> Unit) {
    val themePref by AppStore.theme.collectAsState()
    val scheme = when (themePref) {
        "light" -> LightScheme
        "dark" -> DarkScheme
        "amoled" -> AmoledScheme
        "glass" -> GlassScheme
        else -> if (isSystemInDarkTheme()) DarkScheme else LightScheme
    }
    MaterialTheme(
        colorScheme = scheme,
        shapes = HydroidShapes,
        content = content
    )
}

// no tema glass, superficie elevada mostra a sombra atraves do vidro (retangulos secos)
@Composable
fun glassAwareElevation(normal: androidx.compose.ui.unit.Dp = 1.dp): androidx.compose.ui.unit.Dp {
    val themePref by AppStore.theme.collectAsState()
    return if (themePref == "glass") 0.dp else normal
}

// fundo do tema glass: gradiente escuro + bolhas coloridas desfocadas
@Composable
fun GlassBackground() {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0B0F16), Color(0xFF0A0A13), Color(0xFF06060A))
                )
            )
    ) {
        Canvas(Modifier.fillMaxSize().blur(70.dp)) {
            drawCircle(
                Color(0xFF17B8A6).copy(alpha = 0.45f),
                radius = size.minDimension * 0.55f,
                center = Offset(size.width * 0.12f, size.height * 0.16f)
            )
            drawCircle(
                Color(0xFF4C5BFF).copy(alpha = 0.40f),
                radius = size.minDimension * 0.50f,
                center = Offset(size.width * 0.95f, size.height * 0.32f)
            )
            drawCircle(
                Color(0xFF8B5CF6).copy(alpha = 0.35f),
                radius = size.minDimension * 0.60f,
                center = Offset(size.width * 0.45f, size.height * 0.90f)
            )
        }
    }
}
