@file:OptIn(ExperimentalMaterial3Api::class)

package com.expensetracker.sms.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.expensetracker.sms.CatType
import com.expensetracker.sms.Categories
import com.expensetracker.sms.CategoryDef

/** Lists built-in and custom categories; tap to edit, "+" to create. */
@Composable
fun CategoriesScreen(
    onSave: (CategoryDef, String?) -> Unit,
    onDelete: (CategoryDef) -> Unit,
) {
    var editing by remember { mutableStateOf<CategoryDef?>(null) }
    var creatingType by remember { mutableStateOf<CatType?>(null) }
    val all = Categories.allDefs()

    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        listOf(CatType.EXPENSE to "Expense categories", CatType.INCOME to "Income categories").forEach { (type, title) ->
            val list = all.filter { it.type == type }
            item(key = title) {
                Column {
                    SectionHeader(title, "+ New", { creatingType = type })
                    AppCard(padding = PaddingValues(vertical = 2.dp)) {
                        list.forEachIndexed { i, d ->
                            CategoryListRow(d) { editing = d }
                            if (i < list.lastIndex) RowDivider()
                        }
                    }
                }
            }
        }
        item {
            Text(
                "Built-in categories can get a new icon or colour. Custom ones can also be renamed or deleted — their transactions move to Others / Other income.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
    }

    editing?.let { d ->
        CategoryEditorSheet(
            existing = d,
            presetType = d.type,
            onDismiss = { editing = null },
            onSave = { def, old -> onSave(def, old); editing = null },
            onDelete = { onDelete(d); editing = null },
        )
    }
    creatingType?.let { t ->
        CategoryEditorSheet(
            existing = null,
            presetType = t,
            onDismiss = { creatingType = null },
            onSave = { def, old -> onSave(def, old); creatingType = null },
        )
    }
}

@Composable
private fun CategoryListRow(d: CategoryDef, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(d.icon, Color(d.color), 34.dp)
        Spacer(Modifier.width(12.dp))
        Text(d.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (!d.builtin) Pill("Custom", MaterialTheme.colorScheme.primary)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CategoryEditorSheet(
    existing: CategoryDef?,
    presetType: CatType,
    onDismiss: () -> Unit,
    onSave: (CategoryDef, String?) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(Modifier.imePadding()) { CategoryEditorContent(existing, presetType, onSave, onDelete) }
    }
}

@Composable
fun CategoryEditorContent(
    existing: CategoryDef?,
    presetType: CatType,
    onSave: (CategoryDef, String?) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val builtin = existing?.builtin == true
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: presetType) }
    var icon by remember { mutableStateOf(existing?.icon ?: "star") }
    var color by remember { mutableStateOf(existing?.color ?: CategoryColors[6]) }
    var confirmDelete by remember { mutableStateOf(false) }

    val trimmed = name.trim()
    val clash = trimmed.isNotEmpty() && trimmed != existing?.name &&
        (Categories.def(trimmed) != null || trimmed.equals(Categories.TOTAL, true))
    val valid = trimmed.isNotEmpty() && !clash && trimmed.length <= 24

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, Color(color), 48.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(if (existing == null) "New category" else "Edit category", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (type == CatType.INCOME) "Income" else if (type == CatType.EXPENSE) "Expense" else "Excluded from totals",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(24) },
            label = { Text("Name") },
            placeholder = { Text("e.g. Pets, Gym, Freelance") },
            enabled = !builtin,
            singleLine = true,
            isError = clash,
            supportingText = { if (clash) Text("A category with this name already exists") },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        if (!builtin) {
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(type == CatType.EXPENSE, { type = CatType.EXPENSE }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Expense") }
                SegmentedButton(type == CatType.INCOME, { type = CatType.INCOME }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Income") }
            }
        }

        Text("Icon", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CategoryIcons.chunked(7).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { (key, vector) ->
                        val sel = key == icon
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (sel) Color(color).copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainer)
                                .border(BorderStroke(if (sel) 1.5.dp else 0.dp, if (sel) Color(color) else Color.Transparent), RoundedCornerShape(12.dp))
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(vector, null, tint = if (sel) Color(color) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                        }
                    }
                    repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        Text("Colour", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryColors.chunked(8).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { c ->
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(Color(c)).clickable { color = c },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (c == color) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (existing != null && !builtin && onDelete != null) {
                OutlinedButton(
                    onClick = { if (confirmDelete) onDelete() else confirmDelete = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.height(52.dp),
                ) { Text(if (confirmDelete) "Confirm" else "Delete") }
            }
            Button(
                enabled = valid,
                onClick = {
                    val def = CategoryDef(
                        name = if (builtin) existing!!.name else trimmed,
                        type = type,
                        icon = icon,
                        color = color,
                        builtin = builtin,
                        sort = existing?.sort ?: 500,
                    )
                    onSave(def, existing?.name)
                },
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                if (existing == null) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(if (existing == null) "Create category" else "Save")
            }
        }
    }
}
