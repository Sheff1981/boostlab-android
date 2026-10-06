package com.boostlab.app

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.boostlab.app.ui.BoostScreen
import com.boostlab.app.ui.BoostViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: BoostViewModel by viewModels()

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.connectTunnel()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BoostScreen(
                viewModel = viewModel,
                onRequestVpnPermission = ::requestVpnPermission,
            )
        }
    }

    private fun requestVpnPermission() {
        val intent: Intent? = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            viewModel.connectTunnel()
        }
    }
}
