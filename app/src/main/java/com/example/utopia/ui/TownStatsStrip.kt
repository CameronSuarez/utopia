package com.example.utopia.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TownStatsStrip(viewModel: GameViewModel, onSocialLedgerToggle: () -> Unit) {
    var showDebug by remember { mutableStateOf(false) }

    Surface(
        color = Color.Black.copy(alpha = 0.6f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.padding(2.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onSocialLedgerToggle() },
                    onLongPress = { showDebug = !showDebug }
                )
            }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Town: ${viewModel.buildingCount} Buildings", color = Color.White, fontSize = 14.sp)
            Text("Pop: ${viewModel.totalPopulation} (${viewModel.employedCount} Work)", color = Color.White, fontSize = 12.sp)
            Text("${viewModel.currentPhaseName} | FPS: ${viewModel.fps}", color = Color.Yellow, fontSize = 10.sp)

            if (showDebug) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(4.dp))

                val social = viewModel.socialTriggersPerCycle
                val bumps = viewModel.bumpsPerCycle
                val totalInt = social + bumps
                val share = if (totalInt > 0) (social * 100 / totalInt) else 0

                Text(
                    text = "Social: $social | Bumps: $bumps | Share: $share%",
                    color = Color.Cyan,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Chase: ${viewModel.friendChasesStarted} | Abort: ${viewModel.friendChasesAborted}",
                    color = Color.Cyan,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )

                if (social < 5) {
                    Text(
                        text = "Blocks -> CD: ${viewModel.blockedByCooldown} PH: ${viewModel.blockedByPhase} ST: ${viewModel.blockedByState}",
                        color = Color.Magenta,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
