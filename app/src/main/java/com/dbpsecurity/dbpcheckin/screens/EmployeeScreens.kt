package com.dbpsecurity.dbpcheckin.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dbpsecurity.dbpcheckin.data.SupabaseClient
import com.dbpsecurity.dbpcheckin.models.Group
import com.dbpsecurity.dbpcheckin.models.Profile
import com.dbpsecurity.dbpcheckin.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun EmployeeHomeScreen(navController: NavController) {
    val context = LocalContext.current
    val supabase = SupabaseClient.client
    val scope = rememberCoroutineScope()

    var userProfile by remember { mutableStateOf<Profile?>(null) }
    var groupName by remember { mutableStateOf("Loading...") }
    var isAttendanceWindowOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Toast.makeText(context, "You are not logged in.", Toast.LENGTH_LONG).show()
            navController.navigate("signin") {
                popUpTo(0)
            }
            return@LaunchedEffect
        }

        scope.launch {
            try {
                val profile = supabase.from("profiles").select {
                    filter { eq("id", userId) }
                }.decodeSingle<Profile>()
                userProfile = profile

                if (profile.groupId != null && profile.groupId.isNotBlank()) {
                    val group = supabase.from("groups").select {
                        filter { eq("id", profile.groupId) }
                    }.decodeSingleOrNull<Group>()
                    groupName = group?.name ?: "Unknown Group"
                } else {
                    groupName = "Not Assigned to a Group"
                }
            } catch (e: Exception) {
                groupName = "Error fetching data"
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        // Check attendance window
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        // For testing, let's open the window for a larger time
        // val minute = calendar.get(Calendar.MINUTE)
        // isAttendanceWindowOpen = hour == 10 && minute <= 30
        isAttendanceWindowOpen = true // Keep it open for now for UI development
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Primary, Secondary),
                    startY = 0f,
                    endY = 1500f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // User Icon
            Surface(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape),
                color = White,
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = "User",
                        modifier = Modifier.size(48.dp),
                        tint = Primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Employee Portal",
                style = MaterialTheme.typography.headlineMedium,
                color = White,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = userProfile?.email ?: "Loading email...",
                style = MaterialTheme.typography.bodyMedium,
                color = White.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Group Info Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = White.copy(alpha = 0.9f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Assigned Group", style = MaterialTheme.typography.titleMedium, color = Primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(groupName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Mark Attendance Button
            if (groupName != "Not Assigned to a Group") {
                Button(
                    onClick = {
                        navController.navigate("face_detection")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    enabled = isAttendanceWindowOpen,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        disabledContainerColor = DarkGrey
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Mark Attendance")
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (isAttendanceWindowOpen) "Mark Attendance" else "Window Closed",
                        style = MaterialTheme.typography.titleMedium,
                        color = White
                    )
                }
            } else {
                Text(
                    text = "You cannot mark attendance until an admin assigns you to a group.",
                    color = White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom Navigation Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(100.dp),
                    onClick = { /* Already on Home */ },
                    colors = CardDefaults.cardColors(containerColor = Accent.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Home, contentDescription = "Home", tint = White)
                            Text("Home", color = White)
                        }
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(100.dp),
                    onClick = { navController.navigate("employee_profile") },
                    colors = CardDefaults.cardColors(containerColor = White.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Person, contentDescription = "Profile", tint = White.copy(alpha = 0.7f))
                            Text("Profile", color = White.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmployeeProfileScreen(navController: NavController) {
    UserProfileScreen(navController = navController)
}

