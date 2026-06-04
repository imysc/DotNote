package com.cookandroid.dotnote.domain.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent?.hasError() == true) {
            Log.e("Geofence", "Error: ${geofencingEvent.errorCode}")
            return
        }

        val geofenceTransition = geofencingEvent?.geofenceTransition
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ||
            geofenceTransition == Geofence.GEOFENCE_TRANSITION_DWELL) {
            
            val triggeringGeofences = geofencingEvent.triggeringGeofences
            triggeringGeofences?.forEach { geofence ->
                val memoId = geofence.requestId
                // TODO: Trigger Local Notification to remind user of the memo written at this location
                Log.d("Geofence", "User arrived at location for Memo ID: $memoId")
            }
        }
    }
}
