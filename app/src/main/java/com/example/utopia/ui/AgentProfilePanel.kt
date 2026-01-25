package com.example.utopia.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.utopia.data.models.AgentRuntime
import com.example.utopia.data.models.AppearanceSpec
import com.example.utopia.data.models.AppearanceVariant
import com.example.utopia.data.models.Gender
import com.example.utopia.data.models.SocialMemoryEntry
import kotlin.math.abs

@Composable
fun AgentProfilePanel(
    agent: AgentRuntime,
    allAgents: List<AgentRuntime>,
    relationships: Map<Long, Byte>,
    onClose: () -> Unit
) {
    val portraitCache = remember { PortraitCache(maxEntries = 128) }
    val agentNameById = remember(allAgents) { allAgents.associate { it.id to it.name } }
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

                val socialEntries = agent.profile.socialMemory
                    .mapNotNull { entry ->
                        val other = allAgents.find { it.id == entry.otherAgentId } ?: return@mapNotNull null
                        val relKey = getRelKey(agent.shortId, other.shortId)
                        val score = relationships[relKey]?.toInt() ?: 0
                        SocialEntryUi(entry, other, score, agentNameById[other.id] ?: other.name)
                    }
                    .sortedWith(
                        compareByDescending<SocialEntryUi> { abs(it.score) }
                            .thenByDescending { it.entry.lastInteractionTick }
                    )

                if (socialEntries.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Social", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        socialEntries.forEach { item ->
                            SocialEntryRow(item, portraitCache)
                        }
                    }
                }
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
    val key = PortraitKey(spec, agent.appearance, sizePx = 128)
    val portrait = portraitCache.getPortrait(
        key = key,
        gender = agent.profile.gender,
        personality = agent.personality,
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
            Text(jobLabel(agent.appearance), color = Color.LightGray, fontSize = 12.sp)
            Text(agent.personality.name, color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SocialEntryRow(item: SocialEntryUi, portraitCache: PortraitCache) {
    val spec = item.other.profile.appearance ?: fallbackAppearanceSpec(item.other.profile.gender, item.other.shortId)
    val key = PortraitKey(spec, item.other.appearance, sizePx = 64)
    val portrait = portraitCache.getPortrait(
        key = key,
        gender = item.other.profile.gender,
        personality = item.other.personality,
        facingLeft = false
    )

    Row(
        modifier = Modifier.heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            bitmap = portrait,
            contentDescription = null,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${relationshipTier(item.score)}   ${formatScore(item.score)}",
                color = Color.LightGray,
                fontSize = 12.sp
            )
        }
    }
}

private data class SocialEntryUi(
    val entry: SocialMemoryEntry,
    val other: AgentRuntime,
    val score: Int,
    val name: String
)

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
        bodyHeightMod = if (gender == Gender.MALE) -2f + heightT * 3.0f else -1f + heightT * 4.0f,
        hasBeard = gender == Gender.MALE && (safeSeed % 10) < 3,
        hasHood = (safeSeed % 10) == 0
    )
}

private fun jobLabel(variant: AppearanceVariant): String = when (variant) {
    AppearanceVariant.DEFAULT -> "Villager"
    AppearanceVariant.STORE_WORKER -> "Store Worker"
    AppearanceVariant.WORKSHOP_WORKER -> "Workshop Worker"
    AppearanceVariant.CASTLE_GUARD -> "Castle Guard"
    AppearanceVariant.TAVERN_KEEPER -> "Tavern Keeper"
}

private fun relationshipTier(score: Int): String = when (score) {
    3 -> "Best Friends"
    2 -> "Friends"
    1 -> "Friendly"
    0 -> "Acquaintances"
    -1 -> "Cold"
    -2 -> "Dislike"
    -3 -> "Hostile"
    else -> if (score > 0) "Friendly" else "Hostile"
}

private fun formatScore(score: Int): String = if (score >= 0) "+$score" else "$score"

private fun getRelKey(a: Int, b: Int): Long {
    val low = minOf(a, b).toLong()
    val high = maxOf(a, b).toLong()
    return (low shl 32) or high
}
