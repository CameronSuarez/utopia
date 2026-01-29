package com.example.utopia.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.UiComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.utopia.data.models.AgentIntent
import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.data.models.AppearanceSpec
import com.example.utopia.data.models.AppearanceVariant
import com.example.utopia.data.models.Gender
import kotlin.math.abs
import kotlin.math.roundToInt

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
            UIContent(agent, allAgents, portraitCache, scrollState, onClose)
        }
    }
}

@Composable
private fun UIContent(
    agent: AgentRuntime,
    allAgents: List<AgentRuntime>,
    portraitCache: PortraitCache,
    scrollState: androidx.compose.foundation.ScrollState,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        HeaderRow(onClose = onClose)
        UISpacer(12)
        BasicInfoSection(agent, portraitCache)
        UISpacer(16)
        NeedsSection(agent = agent)
        UISpacer(16)
        PressuresSection(agent = agent)
        UISpacer(16)
        RelationshipsSection(agent, allAgents, portraitCache)
    }
}

@UiComposable
@Composable
private fun UISpacer(height: Int) {
    Spacer(modifier = Modifier.height(height.dp))
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
        NeedBar("Sleep", agent.needs.sleep)
        NeedBar("Stability", agent.needs.stability)
        NeedBar("Social", agent.needs.social)
        NeedBar("Fun", agent.needs.`fun`)
        // For stimulation, 0 means "Stimulated" and 100 means "Bored".
        // We invert it so the bar shows "Engagement" (Full = Good).
        NeedBar("Stimulation", 100f - agent.needs.stimulation)
    }
}

@Composable
private fun NeedBar(label: String, value: Float) {
    val progress = (value / 100f).coerceIn(0f, 1f)
    // Interpolate Red -> Yellow -> Green
    // 0.0 = Red (1, 0, 0)
    // 0.5 = Yellow (1, 1, 0)
    // 1.0 = Green (0, 1, 0)
    val color = when {
        progress < 0.5f -> {
            val t = progress * 2f
            Color(red = 1f, green = t, blue = 0f)
        }
        else -> {
            val t = (progress - 0.5f) * 2f
            Color(red = 1f - t, green = 1f, blue = 0f)
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color.LightGray, fontSize = 12.sp)
            Text("${value.roundToInt()}%", color = Color.White, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(Color(0xFF2D2D2D), shape = RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(color, shape = RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
private fun PressuresSection(agent: AgentRuntime) {
    Column {
        Text("Pressures (AI Logic)", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        if (agent.transientPressures.isEmpty()) {
            Text("No active pressures", color = Color.Gray, fontSize = 14.sp)
        } else {
            // Sort by strength so the "Winner" is on top
            val sortedPressures = agent.transientPressures.toList().sortedByDescending { it.second }

            sortedPressures.forEach { (key, value) ->
                val label = when(key) {
                    AgentIntent.SeekSleep -> "Sleep"
                    AgentIntent.SeekFun -> "Fun"
                    AgentIntent.SeekStability -> "Stability"
                    AgentIntent.SeekStimulation -> "Stimulation"
                    else -> key.toString()
                }
                val isWinner = key == agent.currentIntent

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        color = if (isWinner) Color.Cyan else Color.LightGray,
                        modifier = Modifier.weight(1f),
                        fontSize = 14.sp
                    )

                    Text(
                        text = "%.2f".format(value),
                        color = if (isWinner) Color.Cyan else Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )

                    if (isWinner) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("★", color = Color.Cyan, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun RelationshipsSection(
    agent: AgentRuntime,
    allAgents: List<AgentRuntime>,
    portraitCache: PortraitCache
) {
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
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Profile Picture
                    if (otherAgent != null) {
                        val spec = otherAgent.profile.appearance ?: fallbackAppearanceSpec(otherAgent.profile.gender, otherAgent.shortId)
                        val key = PortraitKey(spec, AppearanceVariant.DEFAULT, sizePx = 64)
                        val portrait = portraitCache.getPortrait(
                            key = key,
                            gender = otherAgent.profile.gender,
                            facingLeft = false
                        )
                        Image(
                            bitmap = portrait,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xFF2D2D2D), shape = RoundedCornerShape(4.dp))
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(name, color = Color.White, fontSize = 14.sp)
                            Text(
                                text = "%.0f".format(affinity),
                                color = Color.LightGray,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        AffinityBar(affinity)
                    }
                }
            }
        }
    }
}

@Composable
private fun AffinityBar(affinity: Float) {
    // Affinity range -100 to 100 -> progress 0.0 to 1.0
    val progress = ((affinity + 100f) / 200f).coerceIn(0f, 1f)
    
    // Interpolate Red -> Yellow -> Green
    val color = when {
        progress < 0.5f -> {
            val t = progress * 2f
            Color(red = 1f, green = t, blue = 0f)
        }
        else -> {
            val t = (progress - 0.5f) * 2f
            Color(red = 1f - t, green = 1f, blue = 0f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(Color(0xFF2D2D2D), shape = RoundedCornerShape(3.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .fillMaxHeight()
                .background(color, shape = RoundedCornerShape(3.dp))
        )
    }
}

internal fun fallbackAppearanceSpec(gender: Gender, seed: Int): AppearanceSpec {
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
