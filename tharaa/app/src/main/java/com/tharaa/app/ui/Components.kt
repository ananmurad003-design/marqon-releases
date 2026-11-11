@file:OptIn(ExperimentalMaterial3Api::class)

package com.tharaa.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tharaa.app.domain.Fund
import com.tharaa.app.domain.Warn

val Gold = Color(0xFFC9A227)
val DeepGreen = Color(0xFF1B4D3E)
val Profit = Color(0xFF2E7D32)
val Loss = Color(0xFFC62828)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B4D3E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBD9),
    onPrimaryContainer = Color(0xFF062A1A),
    secondary = Color(0xFF9A7B12),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6EBC6),
    onSecondaryContainer = Color(0xFF2E2400),
    background = Color(0xFFF1F5F2),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE2EAE5),
    errorContainer = Color(0xFFFFE1DC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD3AE),
    onPrimary = Color(0xFF00391F),
    primaryContainer = Color(0xFF1B5E3F),
    onPrimaryContainer = Color(0xFFCDEBD9),
    secondary = Color(0xFFE6C35C),
    onSecondary = Color(0xFF3B2F00),
    secondaryContainer = Color(0xFF5A4700),
    onSecondaryContainer = Color(0xFFF7E7B0),
    background = Color(0xFF101412),
    surface = Color(0xFF181D1A),
    surfaceVariant = Color(0xFF252B27),
)

@Composable
fun TharaaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}

fun Fund.colorValue() = Color(color)

/** هيكل شاشة موحّد. الشاشات الرئيسية (التبويبات) بدون سهم رجوع. */
@Composable
fun Screen(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    isTab: Boolean = onBack == null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = if (isTab) WindowInsets(0, 0, 0, 0) else WindowInsets.navigationBars,
        content = content,
    )
}

@Composable
fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        suffix = { Text("₪") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
fun NumberField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth(), suffix: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        suffix = if (suffix != null) { { Text(suffix) } } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
fun TextInput(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
fun Progress(value: Float, color: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier.fillMaxWidth()) {
    LinearProgressIndicator(
        progress = { value.coerceIn(0f, 1f) },
        modifier = modifier.height(8.dp).clip(RoundedCornerShape(4.dp)),
        color = color,
        trackColor = color.copy(alpha = 0.18f),
        drawStopIndicator = {},
        gapSize = 0.dp,
    )
}

@Composable
fun InfoCard(
    emoji: String,
    title: String,
    body: String,
    container: Color = MaterialTheme.colorScheme.surface,
    modifier: Modifier = Modifier.fillMaxWidth(),
    action: (@Composable () -> Unit)? = null,
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = container)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Text(emoji, fontSize = 24.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                if (body.isNotBlank()) Text(body, style = MaterialTheme.typography.bodyMedium)
                if (action != null) {
                    Spacer(Modifier.height(6.dp))
                    action()
                }
            }
        }
    }
}

@Composable
fun WarnBox(w: Warn) {
    val bg = when {
        w.positive -> Color(0xFF2E7D32).copy(alpha = 0.12f)
        w.severe -> MaterialTheme.colorScheme.errorContainer
        else -> Color(0xFFF9A825).copy(alpha = 0.16f)
    }
    val icon = when {
        w.positive -> "✅"
        w.severe -> "⛔"
        else -> "🧠"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(10.dp),
    ) {
        Text(icon)
        Spacer(Modifier.width(8.dp))
        Text(w.text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun Pill(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String = "نعم", onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
