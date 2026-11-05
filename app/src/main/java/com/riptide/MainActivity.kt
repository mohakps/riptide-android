package com.riptide

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.riptide.service.TransferService
import com.riptide.ui.approval.PeerApprovalSheet
import com.riptide.ui.home.HomeScreen
import com.riptide.ui.home.HomeViewModel
import com.riptide.ui.navigation.Screen
import com.riptide.ui.session.SessionScreen
import com.riptide.ui.session.SessionViewModel
import com.riptide.ui.settings.SettingsScreen
import com.riptide.ui.settings.SettingsViewModel
import com.riptide.ui.theme.RiptideTheme

class MainActivity : ComponentActivity() {
    private val pickFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) (application as RiptideApp).session.sendFile(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as RiptideApp
        val session = app.session
        startService(Intent(this, TransferService::class.java))
        setContent {
            RiptideTheme {
                val nav = rememberNavController()
                val approval by session.approval.collectAsState()
                NavHost(navController = nav, startDestination = Screen.Home.route) {
                    composable(Screen.Home.route) {
                        val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory(session))
                        HomeScreen(
                            viewModel = vm,
                            onNavigateToSession = { nav.navigate(Screen.Session.route) },
                            onNavigateToSettings = { nav.navigate(Screen.Settings.route) }
                        )
                    }
                    composable(Screen.Session.route) {
                        val vm: SessionViewModel = viewModel(factory = SessionViewModel.Factory(session))
                        SessionScreen(
                            viewModel = vm,
                            onPickFileToSend = { pickFile.launch("*/*") },
                            onDisconnect = {
                                session.disconnect()
                                nav.popBackStack(Screen.Home.route, false)
                            }
                        )
                    }
                    composable(Screen.Settings.route) {
                        val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(session))
                        SettingsScreen(viewModel = vm, onBack = { nav.popBackStack() })
                    }
                }
                approval?.let { req ->
                    PeerApprovalSheet(
                        request = req,
                        onAllow = { session.approveConnection(true) },
                        onDeny = { session.approveConnection(false) }
                    )
                }
            }
        }
    }
}
