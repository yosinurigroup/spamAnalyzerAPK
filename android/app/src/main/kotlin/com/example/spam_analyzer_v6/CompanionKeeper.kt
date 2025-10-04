package com.example.spam_analyzer_v6

import android.Manifest
import android.app.Activity
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender   
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object CompanionKeeper {
    private const val TAG = "CompanionKeeper"
    private const val REQ_ASSOC = 9401
    private const val REQ_BT_PERMS = 9402

    fun hasAssociation(ctx: Context): Boolean {
        return try {
            val cdm = ctx.getSystemService(CompanionDeviceManager::class.java)
            if (Build.VERSION.SDK_INT >= 33) {
                cdm.myAssociations.isNotEmpty()
            } else {
                @Suppress("DEPRECATION")
                cdm.associations.isNotEmpty()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "hasAssociation err: ${t.message}")
            false
        }
    }

    fun ensureAssociation(activity: Activity) {
        if (hasAssociation(activity)) {
            Log.i(TAG, "already associated")
            return
        }
        if (!ensureBtPerms(activity)) return

        try {
            val cdm = activity.getSystemService(CompanionDeviceManager::class.java)

            val filter = BluetoothDeviceFilter.Builder()
                .setNamePattern(null) // any nearby device (user chooses)
                .build()

            val req = AssociationRequest.Builder()
                .addDeviceFilter(filter)
                .setSingleDevice(true)
                .apply {
                    if (Build.VERSION.SDK_INT >= 33) {
                        setDeviceProfile(AssociationRequest.DEVICE_PROFILE_WATCH)
                    }
                }
                .build()

            cdm.associate(req, object : CompanionDeviceManager.Callback() {
                override fun onDeviceFound(chooserLauncher: IntentSender) { 
                    try {
                        activity.startIntentSenderForResult(
                            chooserLauncher, REQ_ASSOC, null, 0, 0, 0, null
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "launch chooser failed: ${e.message}", e)
                    }
                }
                override fun onFailure(error: CharSequence?) {
                    Log.e(TAG, "associate failed: $error")
                }
            }, null)
        } catch (t: Throwable) {
            Log.e(TAG, "ensureAssociation crash: ${t.message}", t)
        }
    }

    fun handleActivityResult(reqCode: Int, resCode: Int, data: Intent?): Boolean {
        if (reqCode != REQ_ASSOC) return false
        Log.i(TAG, "association result code=$resCode")
        return true
    }

    private fun ensureBtPerms(activity: Activity): Boolean {
        return if (Build.VERSION.SDK_INT >= 31) {
            val need = arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
            val missing = need.filter {
                ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                ActivityCompat.requestPermissions(activity, missing.toTypedArray(), REQ_BT_PERMS)
                false
            } else true
        } else {
            val p = Manifest.permission.ACCESS_FINE_LOCATION
            if (ContextCompat.checkSelfPermission(activity, p) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, arrayOf(p), REQ_BT_PERMS)
                false
            } else true
        }
    }
}
