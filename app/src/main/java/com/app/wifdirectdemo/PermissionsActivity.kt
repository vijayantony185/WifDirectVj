package com.app.wifdirectdemo

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.NEARBY_WIFI_DEVICES
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat


class PermissionsActivity : AppCompatActivity() {
    val REQUEST_CODE_PERMISSIONS = 101
    var alertDialog: AlertDialog? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_permissions)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun checkAndOpenWifiSettings() {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager

        // Check if Wi-Fi is enabled
        if (wifiManager.isWifiEnabled) {
            // Wi-Fi is enabled
            Toast.makeText(this, "Wi-Fi is already enabled", Toast.LENGTH_SHORT).show()
            checkLocationAndPrompt()
        } else {
            alertWindow("Wi-Fi is Disabled","Wi-Fi is currently disabled. Would you like to open the settings to enable it?",false, Settings.ACTION_WIFI_SETTINGS)
        }
    }

    private fun checkLocationAndPrompt() {
        // Get LocationManager
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Check if GPS or Network provider is enabled
        val isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (isLocationEnabled) {
            // Location is enabled
            Toast.makeText(this, "Location is already enabled", Toast.LENGTH_SHORT).show()
            requestPermissions()
        } else {
            // Location is disabled, prompt user
            alertWindow("Enable Location","Location services are disabled. Do you want to open location settings?",false,Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        }
    }

    fun requestPermissions(){
        if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(ACCESS_FINE_LOCATION, NEARBY_WIFI_DEVICES),
                REQUEST_CODE_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
            } else {
               alertWindow("Permission Denied","This app requires location and nearby device permissions to function properly. Would you like to enable them in settings?",false,Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            }
        }
    }


    private fun alertWindow(title: String, message: String, negativeButtonEnabled: Boolean, action: String){
        if (alertDialog != null && alertDialog?.isShowing == true) {
            // If a dialog is already shown, do nothing
            return
        }
        val alertDialogBuilder = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Yes") { _, _ ->
                // Redirect to Wi-Fi settings
                val intent = Intent(action)
                if (action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS){
                    val uri: Uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                }
                startActivity(intent)
            }
        // Create the dialog
        alertDialog = alertDialogBuilder.create()
        alertDialog?.setOnDismissListener {
            // Reset the dialog reference when it's dismissed
            alertDialog = null
        }
        alertDialog?.show()

        // Access the negative button after showing the dialog
        val negativeButton = alertDialog?.getButton(AlertDialog.BUTTON_NEGATIVE)

        if (negativeButtonEnabled) {
            negativeButton?.text = "No"
            negativeButton?.setOnClickListener {
                // Perform any action for "No"
                Toast.makeText(this, "Dialog dismissed", Toast.LENGTH_SHORT).show()
                alertDialog?.dismiss()
            }
            negativeButton?.visibility = View.VISIBLE
        } else {
            negativeButton?.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        if (alertDialog == null){
            checkAndOpenWifiSettings()
        }
    }
}