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
// Removed: import com.example.utopia.data.models.SocialMemoryEntry
import kotlin.math.abs

@Composable
fun AgentProfilePanel(
    agent: AgentRuntime,
    // Removed: allAgents: List<AgentRuntime>,
    // Removed: relationships: Map<Long, Byte>,
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
                
                // REMOVED: All social memory and relationship display logic.
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
        // Removed: personality = agent.personality,
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
            // Removed: Text(agent.personality.name, color = Color.Gray, fontSize = 12.sp)
        }
    }
}

// REMOVED: SocialEntryRow
// REMOVED: SocialEntryUi

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

// REMOVED: relationshipTier
// REMOVED: formatScore
// REMOVED: getRelKey
