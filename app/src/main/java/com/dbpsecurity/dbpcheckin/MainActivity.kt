package com.dbpsecurity.dbpcheckin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dbpsecurity.dbpcheckin.screens.*
import com.dbpsecurity.dbpcheckin.ui.theme.DBPCheckinTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DBPCheckinTheme {
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "splash",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("splash") { SplashScreen(navController) }
                        composable("signin") { SignInScreen(navController) }
                        composable("signup") { SignUpScreen(navController) }
                        composable("admin_home") { AdminHomeScreen(navController) }
                        composable("admin_management") { AdminManagementScreen(navController) }
                        composable("admin_profile") { AdminProfileScreen(navController) }
                        composable("employee_home") { EmployeeHomeScreen(navController) }
                        composable("employee_profile") { EmployeeProfileScreen(navController) }
                        composable("face_detection") { FaceDetectionScreen(navController) }
                    }
                }
            }
        }
    }
}
