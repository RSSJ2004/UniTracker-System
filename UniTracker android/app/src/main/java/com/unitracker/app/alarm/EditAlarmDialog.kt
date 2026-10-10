package com.unitracker.app.alarm

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EditAlarmDialog(
    taskTitle: String,
    initialMinutes: Int,
    initialEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (newMinutes: Int, newEnabled: Boolean) -> Unit
) {
    val initialDays = initialMinutes / 1440
    val initialHours = (initialMinutes % 1440) / 60
    val initialMins = initialMinutes % 60

    var days by rememberSaveable { mutableIntStateOf(initialDays) }
    var hours by rememberSaveable { mutableIntStateOf(initialHours) }
    var minutes by rememberSaveable { mutableIntStateOf(initialMins) }
    var enabledState by rememberSaveable { mutableStateOf(initialEnabled) }

    val totalMinutes = (days * 1440) + (hours * 60) + minutes

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Alarma", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Tarea: $taskTitle",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text("¿Cuánto tiempo antes?", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(12.dp))

                // Selector de rueda de 3 columnas (Días, Horas, Min)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WheelPicker(
                        range = 0..30,
                        initialValue = days,
                        label = "Días",
                        onSelect = { days = it }
                    )
                    WheelPicker(
                        range = 0..23,
                        initialValue = hours,
                        label = "Horas",
                        onSelect = { hours = it }
                    )
                    WheelPicker(
                        range = 0..59,
                        initialValue = minutes,
                        label = "Min",
                        onSelect = { minutes = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Activa", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = enabledState, onCheckedChange = { enabledState = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(totalMinutes, enabledState) },
                enabled = totalMinutes > 0
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelPicker(
    range: IntRange,
    initialValue: Int,
    label: String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = range.toList()
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = items.indexOf(initialValue).coerceAtLeast(0)
    )
    val snapBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    // Notificar cuando la rueda se detenga en un número
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val centerIndex = listState.firstVisibleItemIndex
            if (centerIndex in items.indices) {
                onSelect(items[centerIndex])
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(70.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall)
        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier.height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            // Fondo indicador de la opción seleccionada al centro
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                shape = MaterialTheme.shapes.small
            ) {}

            LazyColumn(
                state = listState,
                flingBehavior = snapBehavior,
                contentPadding = PaddingValues(vertical = 40.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items.size) { index ->
                    val number = items[index]
                    val isSelected = listState.firstVisibleItemIndex == index

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = String.format("%02d", number),
                            fontSize = if (isSelected) 22.sp else 16.sp,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.graphicsLayer {
                                alpha = if (isSelected) 1f else 0.5f
                            }
                        )
                    }
                }
            }
        }
    }
}