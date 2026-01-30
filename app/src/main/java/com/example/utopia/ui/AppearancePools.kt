package com.example.utopia.ui

import androidx.compose.ui.graphics.Color
import com.example.utopia.data.models.AppearanceSpec
import com.example.utopia.data.models.Gender
import kotlin.random.Random

val SKIN_TONES = arrayOf(
    Color(0xFFFFCCBC),
    Color(0xFFE0AC69),
    Color(0xFF8D5524),
    Color(0xFFC68642),
    Color(0xFFF1C27D)
)

val HAIR_COLORS = arrayOf(
    Color(0xFF4B2C20),
    Color(0xFFFBF2D5),
    Color(0xFF0F0F0F),
    Color(0xFFA52A2A),
    Color(0xFFD3D3D3)
)

val TUNIC_COLORS = arrayOf(
    Color(0xFF5D4037),
    Color(0xFF2E7D32),
    Color(0xFF1565C0),
    Color(0xFFC62828),
    Color(0xFF455A64),
    Color(0xFF6A1B9A)
)

val MALE_HAIR_STYLES = intArrayOf(1, 2, 4, 5)
val FEMALE_HAIR_STYLES = intArrayOf(2, 3, 5)

fun generateAppearanceSpec(
    gender: Gender,
    rng: Random
): AppearanceSpec {
    val hairStylePool = if (gender == Gender.MALE) MALE_HAIR_STYLES else FEMALE_HAIR_STYLES
    val hairStyleId = hairStylePool[rng.nextInt(hairStylePool.size)]

    val bodyWidthMod = if (gender == Gender.MALE) {
        -0.5f + rng.nextFloat() * 3.5f
    } else {
        -1.5f + rng.nextFloat() * 3.0f
    }
    val bodyHeightMod = if (gender == Gender.MALE) {
        -2f + rng.nextFloat() * 3.0f
    } else {
        -1f + rng.nextFloat() * 4.0f
    }

    return AppearanceSpec(
        skinToneId = rng.nextInt(SKIN_TONES.size),
        hairColorId = rng.nextInt(HAIR_COLORS.size),
        tunicColorId = rng.nextInt(TUNIC_COLORS.size),
        hairStyleId = hairStyleId,
        bodyWidthMod = bodyWidthMod,
        bodyHeightMod = bodyHeightMod,
        hasBeard = gender == Gender.MALE && rng.nextFloat() < 0.35f,
        hasHood = rng.nextFloat() < 0.05f
    )
}
