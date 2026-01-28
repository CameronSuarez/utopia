package com.example.utopia.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.data.models.AppearanceSpec
import com.example.utopia.data.models.AppearanceVariant
import com.example.utopia.data.models.Gender
import kotlin.math.abs

@Composable
fun AgentProfilePanel(
    agent: AgentRuntime,
    allAgents: List<AgentRuntime>,
    onClose: () -> Unit
) {
    val portraitCache = remember { PortraitCache(maxEntries = 128) }
    val scrollState = rememberScrollState()

    BoxWithConstraints {
        val preferredWidth = maxWidth * 0.32f
        val panelWidth = preferredWidth.coerceIn(280.dp, 360.dp)

        Surface(
            color = Color(0xFF1B1B1B),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .padding(top = 16.dp, end = 16.dp, bottom = 16.dp)
                .fillMaxHeight()
                .width(panelWidth)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                HeaderRow(onClose = onClose)
                Spacer(modifier = Modifier.height(12.dp))
                BasicInfoSection(agent, portraitCache)
                Spacer(modifier = Modifier.height(16.dp))
                NeedsSection(agent = agent)
                Spacer(modifier = Modifier.height(16.dp))
                PressuresSection(agent = agent)
                Spacer(modifier = Modifier.height(16.dp))
                RelationshipsSection(agent, allAgents)
            }
        }
    }
}

@Composable
private fun HeaderRow(onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Profile", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onClose) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
    }
}

@Composable
private fun BasicInfoSection(agent: AgentRuntime, portraitCache: PortraitCache) {
    val spec = agent.profile.appearance ?: fallbackAppearanceSpec(agent.profile.gender, agent.shortId)
    val key = PortraitKey(spec, AppearanceVariant.DEFAULT, sizePx = 128) // FORCED DEFAULT variant
    val portrait = portraitCache.getPortrait(
        key = key,
        gender = agent.profile.gender,
        facingLeft = false
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            bitmap = portrait,
            contentDescription = null,
            modifier = Modifier.size(96.dp)
        )
        Spacer(modifier = Modifier.size(12.dp))
        Column {
            Text(agent.name, color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text("Villager", color = Color.LightGray, fontSize = 12.sp) // Hardcoded "Villager"
            Text("Intent: ${agent.currentIntent}", color = Color.Cyan, fontSize = 12.sp)
        }
    }
}

@Composable
private fun NeedsSection(agent: AgentRuntime) {
    Column {
        Text("Needs", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Sleep: ${"%.2f".format(agent.needs.sleep)}", color = Color.LightGray)
        Text("Stability: ${"%.2f".format(agent.needs.stability)}", color = Color.LightGray)
        Text("Social: ${"%.2f".format(agent.needs.social)}", color = Color.LightGray)
        Text("Fun: ${"%.2f".format(agent.needs.`fun`)}", color = Color.LightGray)
        Text("Stimulation: ${"%.2f".format(agent.needs.stimulation)}", color = Color.LightGray)
    }
}

@Composable
private fun PressuresSection(agent: AgentRuntime) {
    Column {
        Text("Pressures", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        if (agent.transientPressures.isEmpty()) {
            Text("None", color = Color.Gray)
        } else {
            agent.transientPressures.forEach { (key, value) ->
                Text("$key: ${"%.2f".format(value)}", color = Color.LightGray)
            }
        }
    }
}

@Composable
private fun RelationshipsSection(agent: AgentRuntime, allAgents: List<AgentRuntime>) {
    Column {
        Text("Relationships", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        
        val relationships = agent.socialMemory.affinity.filter { it.value != 0f }
        
        if (relationships.isEmpty()) {
            Text("No known relationships", color = Color.Gray, fontSize = 14.sp)
        } else {
            relationships.forEach { (otherId, affinity) ->
                val otherAgent = allAgents.find { it.id == otherId }
                val name = otherAgent?.name ?: "Unknown ($otherId)"
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(name, color = Color.LightGray, modifier = Modifier.weight(1f))
                    
                    val affinityColor = when {
                        affinity > 50 -> Color.Green
                        affinity > 10 -> Color(0xFF90EE90)
                        affinity < -50 -> Color.Red
                        affinity < -10 -> Color(0xFFF08080)
                        else -> Color.Gray
                    }
                    
                    Text(
                        text = "%.0f".format(affinity),
                        color = affinityColor,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

private fun fallbackAppearanceSpec(gender: Gender, seed: Int): AppearanceSpec {
    val safeSeed = abs(seed)
    val hairStylePool = if (gender == Gender.MALE) MALE_HAIR_STYLES else FEMALE_HAIR_STYLES
    val widthT = (safeSeed % 100) / 100f
    val heightT = ((safeSeed / 7) % 100) / 100f
    return AppearanceSpec(
        skinToneId = safeSeed % SKIN_TONES.size,
        hairColorId = (safeSeed / 3) % HAIR_COLORS.size,
        tunicColorId = (safeSeed / 5) % TUNIC_COLORS.size,
        hairStyleId = hairStylePool[safeSeed % hairStylePool.size],
        bodyWidthMod = if (gender == Gender.MALE) -0.5f + widthT * 3.5f else -1.5f + widthT * 3.0f,
        bodyHeightMod = if (gender == Gender.MALE) -2f + heightT * 4.0f else -1f + heightT * 4.0f,
        hasBeard = gender == Gender.MALE && (safeSeed % 10) < 3,
        hasHood = (safeSeed % 10) == 0
    )
}
