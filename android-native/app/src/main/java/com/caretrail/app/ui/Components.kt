package com.caretrail.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.caretrail.app.data.Langs
import com.caretrail.app.data.Logic

@Composable
fun CTCard(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    border: Color = CT.CardBorder,
    shape: Shape = RoundedCornerShape(24.dp),
    padding: Int = 16,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var m = modifier.fillMaxWidth().background(color, shape).border(BorderStroke(1.dp, border), shape)
    if (onClick != null) m = m.clickable(role = Role.Button, onClick = onClick)
    Column(m.padding(padding.dp), content = content)
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = CT.Brand, contentColor = Color.White, disabledContainerColor = CT.Brand.copy(alpha = 0.4f), disabledContentColor = Color.White),
    ) { Text(text, style = androidx.compose.material3.MaterialTheme.typography.labelLarge, color = Color.White) }
}

@Composable
fun OutlineButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CT.Brand.copy(alpha = if (enabled) 1f else 0.3f)),
    ) { Text(text, style = androidx.compose.material3.MaterialTheme.typography.labelLarge, color = CT.Brand) }
}

@Composable
fun CTField(
    value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    password: Boolean = false, keyboard: KeyboardType = KeyboardType.Text, singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        modifier = modifier.fillMaxWidth(), singleLine = singleLine,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CT.Brand, unfocusedBorderColor = CT.FieldBorder, focusedLabelColor = CT.Brand),
    )
}

@Composable
fun Note(text: String, modifier: Modifier = Modifier, tone: Tone = Tone.Amber) {
    val (bg, fg, br) = when (tone) {
        Tone.Amber -> Triple(CT.Amber50, CT.Amber900, CT.Amber300)
        Tone.Red -> Triple(CT.Red50, CT.Red800, CT.Red200)
        Tone.Green -> Triple(CT.Green50, CT.Green800, CT.Green300)
        Tone.Blue -> Triple(CT.BrandSoft, CT.Brand, CT.BrandSoft)
    }
    Text(
        text, color = fg, style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        modifier = modifier.fillMaxWidth().background(bg, RoundedCornerShape(12.dp)).border(BorderStroke(1.dp, br), RoundedCornerShape(12.dp)).padding(12.dp),
    )
}

enum class Tone { Amber, Red, Green, Blue }

@Composable
fun Chip(text: String, bg: Color, fg: Color) {
    Text(text, color = fg, style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        modifier = Modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp))
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = androidx.compose.material3.MaterialTheme.typography.titleSmall, modifier = modifier)
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = CT.Muted, modifier = modifier)
}

/** Language dropdown used on login, signup and home. */
@Composable
fun LangPicker(current: String, onPick: (String) -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    val label = Langs.all.firstOrNull { it.code == current }?.label ?: "English"
    androidx.compose.foundation.layout.Box(modifier) {
        val shape = RoundedCornerShape(if (compact) 50 else 12)
        Row(
            Modifier.then(if (compact) Modifier else Modifier.fillMaxWidth())
                .background(Color.White, shape).border(BorderStroke(1.dp, CT.FieldBorder), shape)
                .clickable(role = Role.DropdownList) { open = true }
                .heightIn(min = 44.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            Text("  ▾", color = CT.Muted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Langs.all.forEach { l -> DropdownMenuItem(text = { Text(l.label) }, onClick = { onPick(l.code); open = false }) }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DateField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, okLabel: String = "OK", cancelLabel: String = "Cancel") {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { open = true }, modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, CT.FieldBorder),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = CT.Muted, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            Text(value.ifEmpty { "—" }, color = CT.Ink, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
        }
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = Logic.millisFromIso(value))
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onChange(Logic.isoFromMillis(it)) }; open = false }) { Text(okLabel) } },
            dismissButton = { TextButton(onClick = { open = false }) { Text(cancelLabel) } },
        ) { DatePicker(state = state) }
    }
}
