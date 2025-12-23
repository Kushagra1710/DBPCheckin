package com.dbpsecurity.dbpcheckin.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.dbpsecurity.dbpcheckin.data.SupabaseClient
import com.dbpsecurity.dbpcheckin.models.Profile
import com.dbpsecurity.dbpcheckin.ui.theme.Primary
import com.dbpsecurity.dbpcheckin.ui.theme.Secondary
import com.dbpsecurity.dbpcheckin.ui.theme.White
import com.google.firebase.auth.FirebaseAuth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SplashScreen(navController: NavController) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser == null) {
                    withContext(Dispatchers.Main) {
                        navController.navigate("signin") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                } else {
                    val profile = SupabaseClient.client.from("profiles").select {
                        filter { eq("id", currentUser.uid) }
                    }.decodeSingleOrNull<Profile>()

                    withContext(Dispatchers.Main) {
                        if (profile != null) {
                            val destination = if (profile.role == "admin") "admin_home" else "employee_home"
                            navController.navigate(destination) {
                                popUpTo("splash") { inclusive = true }
                            }
                        } else {
                            // Profile missing, go to signin
                            navController.navigate("signin") {
                                popUpTo("splash") { inclusive = true }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // If profile doesn't exist or any other error, go to signin
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Session expired or error: ${e.message}", Toast.LENGTH_SHORT).show()
                    FirebaseAuth.getInstance().signOut() // Clean up session
                    navController.navigate("signin") {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Primary, Secondary)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = White)
    }
}
