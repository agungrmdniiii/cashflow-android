package com.cashflow.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.ui.theme.duit

@Composable
fun ProfileSection(
    userName: String,
    onEditName: () -> Unit,
    modifier: Modifier = Modifier
) {
    val duit = MaterialTheme.duit

    SettingsSectionCard(
        title = "Profil Pengguna",
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onEditName)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(duit.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = duit.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = if (userName.isNotBlank()) userName else "Atur Nama Panggilan",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp
                        ),
                        color = duit.textPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (userName.isNotBlank()) "Ditampilkan pada sapaan beranda" else "Klik untuk menambahkan nama panggilan",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = duit.textSecondary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(duit.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "Ubah Nama",
                    tint = duit.textPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
