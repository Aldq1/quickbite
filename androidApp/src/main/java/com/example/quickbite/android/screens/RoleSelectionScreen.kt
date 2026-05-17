package com.example.quickbite.android.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quickbite.android.models.UserRole

private val RsBrand      = Color(0xFFE8430A)
private val RsBrandLight = Color(0xFFFF7043)
private val RsTextDark   = Color(0xFF1C1C1E)
private val RsTextMuted  = Color(0xFF8A8A8E)
private val RsWhite      = Color(0xFFFFFFFF)
private val RsSurface    = Color(0xFFF7F7F7)

@Composable
fun RoleSelectionScreen(
    onRoleSelected: (UserRole) -> Unit
) {
    var selected by remember { mutableStateOf<UserRole?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {

        // Gradient header band
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.38f)
                .background(Brush.verticalGradient(listOf(RsBrand, RsBrandLight)))
        )

        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ────────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.28f)
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(RsWhite.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🏨", fontSize = 32.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Tipul contului",
                    color = RsWhite,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Cine ești pe platforma QuickBite?",
                    color = RsWhite.copy(alpha = 0.82f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // ── Role cards ────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(RsWhite)
                    .padding(horizontal = 24.dp, vertical = 28.dp)
            ) {
                Text(
                    text = "Selectează rolul tău",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = RsTextDark
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Această alegere personalizează experiența ta",
                    fontSize = 13.sp,
                    color = RsTextMuted
                )
                Spacer(modifier = Modifier.height(24.dp))

                // 2×2 grid via two Rows
                val roles = UserRole.all
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        RoleCard(
                            role = roles[0],
                            isSelected = selected == roles[0],
                            modifier = Modifier.weight(1f),
                            onClick = { selected = roles[0] }
                        )
                        RoleCard(
                            role = roles[1],
                            isSelected = selected == roles[1],
                            modifier = Modifier.weight(1f),
                            onClick = { selected = roles[1] }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        RoleCard(
                            role = roles[2],
                            isSelected = selected == roles[2],
                            modifier = Modifier.weight(1f),
                            onClick = { selected = roles[2] }
                        )
                        RoleCard(
                            role = roles[3],
                            isSelected = selected == roles[3],
                            modifier = Modifier.weight(1f),
                            onClick = { selected = roles[3] }
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Confirm button
                Button(
                    onClick = { selected?.let { onRoleSelected(it) } },
                    enabled = selected != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RsBrand,
                        disabledContainerColor = RsBrand.copy(alpha = 0.35f)
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    Text(
                        text = if (selected != null) "Continuă ca ${selected!!.label}" else "Selectează un rol",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = RsWhite
                    )
                }
            }
        }
    }
}

@Composable
private fun RoleCard(
    role: UserRole,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) RsBrand else Color(0xFFE0E0E0)
    val bgColor     = if (isSelected) RsBrand.copy(alpha = 0.06f) else RsWhite

    Box(
        modifier = modifier
            .shadow(if (isSelected) 6.dp else 2.dp, RoundedCornerShape(18.dp), clip = false)
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 20.dp, horizontal = 12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) RsBrand.copy(alpha = 0.12f)
                        else Color(0xFFF0F0F0)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = role.emoji, fontSize = 24.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = role.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) RsBrand else RsTextDark,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = role.description,
                fontSize = 11.sp,
                color = RsTextMuted,
                textAlign = TextAlign.Center,
                lineHeight = 15.sp
            )
        }
    }
}