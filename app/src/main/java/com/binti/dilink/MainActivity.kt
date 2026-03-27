package com.binti.dilink

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.binti.dilink.databinding.ActivityMainBinding
import com.binti.dilink.dilink.DiLinkAccessibilityService
import com.binti.dilink.utils.HMSUtils
import com.binti.dilink.utils.ModelManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Main Activity - Setup Wizard & Dashboard
 * 
 * @author Dr. Waleed Mandour
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "binti_prefs"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_TONE_FORMAL = "tone_formal"
        private const val KEY_PROACTIVE_ENABLED = "proactive_enabled"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var modelManager: ModelManager
    
    // Permission states
    private var hasMicPermission = false
    private var hasLocationPermission = false
    private var hasPhonePermission = false
    private var hasOverlayPermission = false
    private var hasAccessibilityPermission = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        checkPermissions()
    }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BintiService.BROADCAST_STATE_CHANGED -> {
                    val state = intent.getStringExtra(BintiService.EXTRA_STATE) ?: "unknown"
                    updateServiceState(state)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        modelManager = ModelManager(this)
        setupUI()
        loadPreferences()
        registerReceivers()
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(stateReceiver) } catch (e: Exception) {}
    }

    private fun setupUI() {
        binding.apply {
            btnStartService.setOnClickListener {
                if (BintiService.isServiceRunning()) stopBintiService()
                else if (checkAllPermissions()) checkModelsAndStart()
            }
            
            btnUserMenu.setOnClickListener { showUserMenu() }

            etUserName.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    sharedPreferences.edit().putString(KEY_USER_NAME, s.toString()).apply()
                }
            })

            toggleTone.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    sharedPreferences.edit().putBoolean(KEY_TONE_FORMAL, checkedId == R.id.btnFormal).apply()
                }
            }

            switchProactive.setOnCheckedChangeListener { _, isChecked ->
                sharedPreferences.edit().putBoolean(KEY_PROACTIVE_ENABLED, isChecked).apply()
            }
            
            // Permission Click Listeners
            layoutMicPermission.btnGrantMic.setOnClickListener { 
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO)) 
            }
            
            layoutLocationPermission.btnGrantMic.setOnClickListener { 
                requestPermissions(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION, 
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )) 
            }
            
            layoutPhonePermission.btnGrantMic.setOnClickListener { 
                requestPermissions(arrayOf(
                    Manifest.permission.CALL_PHONE, 
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.READ_PHONE_STATE
                )) 
            }
            
            layoutOverlayPermission.btnGrantOverlay.setOnClickListener { requestOverlayPermission() }
            layoutAccessibilityPermission.btnGrantAccessibility.setOnClickListener { requestAccessibilityPermission() }
            
            btnDownloadModels.setOnClickListener { showModelDownloadDialog() }
        }
    }

    private fun showUserMenu() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.user_menu_title)
            .setMessage(R.string.user_menu_content)
            .setPositiveButton(R.string.got_it, null)
            .show()
    }

    private fun loadPreferences() {
        binding.apply {
            etUserName.setText(sharedPreferences.getString(KEY_USER_NAME, ""))
            val isFormal = sharedPreferences.getBoolean(KEY_TONE_FORMAL, false)
            toggleTone.check(if (isFormal) R.id.btnFormal else R.id.btnInformal)
            switchProactive.isChecked = sharedPreferences.getBoolean(KEY_PROACTIVE_ENABLED, true)
        }
    }

    private fun checkPermissions() {
        hasMicPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        
        hasLocationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        
        hasPhonePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED &&
                             ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
                             ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

        hasOverlayPermission = Settings.canDrawOverlays(this)
        
        val accessibilityService = packageName + "/" + DiLinkAccessibilityService::class.java.canonicalName
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        hasAccessibilityPermission = enabledServices?.contains(accessibilityService) == true
        
        updatePermissionCards()
    }

    private fun requestPermissions(perms: Array<String>) {
        requestPermissionLauncher.launch(perms)
    }

    private fun requestOverlayPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        startActivity(intent)
    }

    private fun requestAccessibilityPermission() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun updatePermissionCards() {
        binding.apply {
            updateCard(layoutMicPermission.cardMicPermission, layoutMicPermission.btnGrantMic, hasMicPermission)
            
            // Fix text for dynamically reused layout
            layoutLocationPermission.tvMicPermissionTitle.text = getString(R.string.location_permission_title)
            layoutLocationPermission.tvMicPermissionDesc.text = getString(R.string.location_permission_desc)
            updateCard(layoutLocationPermission.cardMicPermission, layoutLocationPermission.btnGrantMic, hasLocationPermission)
            
            layoutPhonePermission.tvMicPermissionTitle.text = getString(R.string.phone_permission_title)
            layoutPhonePermission.tvMicPermissionDesc.text = getString(R.string.phone_permission_desc)
            updateCard(layoutPhonePermission.cardMicPermission, layoutPhonePermission.btnGrantMic, hasPhonePermission)
            
            updateCard(layoutOverlayPermission.cardOverlayPermission, layoutOverlayPermission.btnGrantOverlay, hasOverlayPermission)
            updateCard(layoutAccessibilityPermission.cardAccessibilityPermission, layoutAccessibilityPermission.btnGrantAccessibility, hasAccessibilityPermission)
            
            btnStartService.text = if (BintiService.isServiceRunning()) getString(R.string.stop_service) else getString(R.string.start_service)
        }
    }

    private fun updateCard(card: com.google.android.material.card.MaterialCardView, btn: android.widget.Button, granted: Boolean) {
        if (granted) {
            card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.permission_granted))
            btn.text = getString(R.string.permission_granted)
            btn.isEnabled = false
        } else {
            card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_background))
            btn.text = getString(R.string.grant_permission)
            btn.isEnabled = true
        }
    }

    private fun checkAllPermissions(): Boolean {
        val all = hasMicPermission && hasLocationPermission && hasPhonePermission && hasOverlayPermission && hasAccessibilityPermission
        if (!all) Toast.makeText(this, R.string.permissions_required_message, Toast.LENGTH_LONG).show()
        return all
    }

    private fun checkModelsAndStart() {
        lifecycleScope.launch {
            if (modelManager.checkModelsStatus().allModelsReady) startBintiService()
            else showModelDownloadDialog()
        }
    }

    private fun showModelDownloadDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.download_models_title)
            .setMessage(R.string.download_models_message)
            .setPositiveButton(R.string.download_now) { _, _ -> startModelDownload() }
            .show()
    }

    private fun startModelDownload() {
        lifecycleScope.launch {
            binding.progressBar.visibility = android.view.View.VISIBLE
            modelManager.downloadModels(
                onProgress = { p, f -> runOnUiThread { binding.progressBar.progress = p } },
                onComplete = { runOnUiThread { binding.progressBar.visibility = android.view.View.GONE; startBintiService() } },
                onError = { e -> runOnUiThread { binding.progressBar.visibility = android.view.View.GONE } }
            )
        }
    }

    private fun startBintiService() {
        val intent = Intent(this, BintiService::class.java).apply { action = BintiService.ACTION_START }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        updatePermissionCards()
    }

    private fun stopBintiService() {
        startService(Intent(this, BintiService::class.java).apply { action = BintiService.ACTION_STOP })
        updatePermissionCards()
    }

    private fun updateServiceState(state: String) {
        binding.tvServiceState.text = when (state) {
            "ready" -> getString(R.string.state_ready)
            "listening" -> getString(R.string.state_listening)
            "processing" -> getString(R.string.state_processing)
            else -> getString(R.string.state_ready)
        }
    }

    private fun registerReceivers() {
        val filter = IntentFilter(BintiService.BROADCAST_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(stateReceiver, filter)
    }
    
    private val sharedPreferences by lazy { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
}
