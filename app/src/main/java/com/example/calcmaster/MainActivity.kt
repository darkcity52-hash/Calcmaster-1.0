package com.example.calcmaster

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calcmaster.ui.theme.CalcMasterTheme
import androidx.core.content.edit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CalcMasterTheme {
                HeliosSystem(onPanic = { executePurge() })
            }
        }
    }

    private fun executePurge() {
        Toast.makeText(this, "TERMINAL PURGED", Toast.LENGTH_SHORT).show()
        finishAffinity()
    }
}

// Formats a Double result for display: integer if whole, otherwise trimmed decimals.
// Handles large numbers by avoiding Long overflow.
fun formatResult(result: Double): String {
    if (result.isInfinite() || result.isNaN()) return "ERROR"
    return if (result % 1 == 0.0 && result >= Long.MIN_VALUE.toDouble() && result <= Long.MAX_VALUE.toDouble()) {
        result.toLong().toString()
    } else {
        "%.10f".format(result).trimEnd('0').trimEnd('.')
    }
}

@Composable
fun HeliosSystem(onPanic: () -> Unit) {
    var identityActivated by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = identityActivated,
            transitionSpec = {
                slideInVertically { it } + fadeIn() togetherWith slideOutVertically { -it } + fadeOut()
            },
            label = "system_transition"
        ) { activated ->
            if (activated) {
                HeliosTerminal(onDeactivate = { identityActivated = false }, onPanic = onPanic)
            } else {
                CalculatorInterface(
                    onSecretCodeEntered = {
                        identityActivated = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onPanic = onPanic
                )
            }
        }

        Scanlines()
    }
}

@Composable
fun CalculatorInterface(onSecretCodeEntered: () -> Unit, onPanic: () -> Unit) {
    var display by remember { mutableStateOf("0") }
    var operand1 by remember { mutableStateOf<Double?>(null) }
    var operator by remember { mutableStateOf<String?>(null) }
    var shouldResetDisplay by remember { mutableStateOf(false) }
    var secretBuffer by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme

    // Auto-scale font size based on display length
    val displayFontSize = when {
        display.length > 12 -> 32.sp
        display.length > 8  -> 44.sp
        else                -> 64.sp
    }

    // Centralised button press handler — keeps the UI tree clean
    fun handlePress(label: String) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        when (label) {
            "C" -> {
                display = "0"
                operand1 = null
                operator = null
                shouldResetDisplay = false
                secretBuffer = ""
                isError = false
            }
            "⌫" -> {
                if (!isError && display != "0") {
                    display = if (display.length == 1) "0" else display.dropLast(1)
                }
            }
            "." -> {
                if (isError) return
                if (shouldResetDisplay) {
                    display = "0."
                    shouldResetDisplay = false
                } else if (!display.contains(".")) {
                    display += "."
                }
            }
            "=" -> {
                val val2 = display.toDoubleOrNull()
                if (operand1 != null && operator != null && val2 != null) {
                    val result = when (operator) {
                        "+" -> operand1!! + val2
                        "-" -> operand1!! - val2
                        "*" -> operand1!! * val2
                        "/" -> if (val2 != 0.0) operand1!! / val2 else null
                        else -> val2
                    }
                    if (result == null) {
                        display = "DIV/0"
                        isError = true
                    } else {
                        display = formatResult(result)
                        isError = false
                    }
                    operand1 = null
                    operator = null
                    shouldResetDisplay = true
                    secretBuffer = ""
                }
            }
            in listOf("/", "*", "-", "+") -> {
                if (isError) return
                operand1 = display.toDoubleOrNull()
                operator = label
                shouldResetDisplay = true
            }
            else -> {
                // Digit pressed
                if (isError) {
                    display = label
                    isError = false
                    shouldResetDisplay = false
                } else if (display == "0" || shouldResetDisplay) {
                    display = label
                    shouldResetDisplay = false
                } else if (display.length < 15) {
                    display += label
                }
                // Track secret code — reset on operator/result to avoid false triggers
                secretBuffer += label
                if (secretBuffer.endsWith("477")) {
                    onSecretCodeEntered()
                    secretBuffer = ""
                } else if (secretBuffer.length > 5) {
                    secretBuffer = secretBuffer.takeLast(5)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.size >= 2) {
                            val movingDown = event.changes.all { it.position.y - it.previousPosition.y > 20 }
                            if (movingDown) onPanic()
                        }
                    }
                }
            },
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Display panel
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .border(1.dp, colors.primary.copy(alpha = 0.3f), RoundedCornerShape(2.dp)),
            color = Color(0xFF020202),
            shape = RoundedCornerShape(2.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Active operator indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = operator ?: "",
                        color = colors.secondary.copy(alpha = 0.7f),
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                // Main display value
                Box(
                    contentAlignment = Alignment.CenterEnd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Calculator display: $display" }
                ) {
                    Text(
                        text = display,
                        color = if (isError) Color(0xFFFF4444) else colors.primary,
                        fontSize = displayFontSize,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        maxLines = 1
                    )
                }
            }
        }

        // Button grid
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Rows 1–4: standard 4-column grid
            val numericRows = listOf(
                listOf("7", "8", "9", "/"),
                listOf("4", "5", "6", "*"),
                listOf("1", "2", "3", "-"),
                listOf(".", "0", "⌫", "+")
            )
            numericRows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { label ->
                        HeliosButton(
                            label = label,
                            modifier = Modifier.weight(1f),
                            isOperator = label in listOf("/", "*", "-", "+"),
                            isDanger = false,
                            onClick = { handlePress(label) }
                        )
                    }
                }
            }
            // Row 5: C spans 2 cols, = spans 2 cols
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HeliosButton(
                    label = "C",
                    modifier = Modifier.weight(2f),
                    isOperator = false,
                    isDanger = true,
                    onClick = { handlePress("C") }
                )
                HeliosButton(
                    label = "=",
                    modifier = Modifier.weight(2f),
                    isOperator = true,
                    isDanger = false,
                    onClick = { handlePress("=") }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun HeliosTerminal(onDeactivate: () -> Unit, onPanic: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var secureNote by remember {
        mutableStateOf(
            context.getSharedPreferences("helios_vault", android.content.Context.MODE_PRIVATE)
                .getString("note", "") ?: ""
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(24.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.size >= 2) {
                            if (event.changes.all { it.position.y - it.previousPosition.y > 20 }) onPanic()
                        }
                    }
                }
            }
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("HELIOS TERMINAL", color = colors.secondary, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(colors.secondary, RoundedCornerShape(5.dp))
            )
        }

        Text("SECURE ACCESS GRANTED - ID: 47C7", color = colors.primary, fontSize = 12.sp)
        HorizontalDivider(
            color = colors.primary.copy(alpha = 0.3f),
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Text("SECURE VAULT (AUTO-PURGE ENABLED)", color = colors.secondary, fontSize = 14.sp)

        OutlinedTextField(
            value = secureNote,
            onValueChange = { newValue ->
                secureNote = newValue
                context.getSharedPreferences("helios_vault", android.content.Context.MODE_PRIVATE)
                    .edit { putString("note", newValue) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.primary,
                unfocusedTextColor = colors.primary,
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.primary.copy(alpha = 0.5f)
            ),
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = {
                Text("Write encrypted data here...", color = colors.primary.copy(alpha = 0.3f))
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDeactivate()
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Lock terminal and return to calculator" },
                colors = ButtonDefaults.buttonColors(containerColor = colors.secondary),
                shape = RoundedCornerShape(2.dp)
            ) {
                Text("LOCK", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onPanic,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Purge all data and close app" },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB00020)),
                shape = RoundedCornerShape(2.dp)
            ) {
                Text("PURGE", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun HeliosButton(
    label: String,
    isOperator: Boolean,
    modifier: Modifier = Modifier,
    isDanger: Boolean = false,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val contentColor = when {
        isDanger   -> Color(0xFFFF4444)
        isOperator -> colors.secondary
        else       -> colors.primary
    }
    val accessibilityLabel = when (label) {
        "⌫"  -> "Backspace"
        "/"  -> "Divide"
        "*"  -> "Multiply"
        "-"  -> "Subtract"
        "+"  -> "Add"
        "="  -> "Equals"
        "C"  -> "Clear"
        "."  -> "Decimal point"
        else -> label
    }

    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .height(85.dp)
            .semantics { contentDescription = accessibilityLabel },
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
            brush = androidx.compose.ui.graphics.SolidColor(contentColor.copy(alpha = 0.6f))
        )
    ) {
        Text(text = label, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Scanlines() {
    val infiniteTransition = rememberInfiniteTransition(label = "scanline_anim")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val lineSpacing = 6.dp.toPx()
        for (y in 0 until (size.height + lineSpacing).toInt() step lineSpacing.toInt()) {
            drawLine(
                color = Color.Black.copy(alpha = 0.12f),
                start = Offset(0f, y.toFloat() + offsetY),
                end = Offset(size.width, y.toFloat() + offsetY),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
fun HeliosPreview() {
    CalcMasterTheme {
        HeliosSystem(onPanic = {})
    }
}
