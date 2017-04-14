package org.domogik.butler;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.util.Log;

/**
 * Created by fritz on 14/01/17.
 */

public class ButlerLocation implements LocationListener {
    private String LOG_TAG = "BUTLER > Location";
    Context context;
    private Object obj;
    private boolean runLocationThread = false;
    private boolean running = false;
    private Long lastGpsLocation = null;
    private int locationInterval;
    private LocationManager lm = null;
    private SharedPreferences settings;


    public void init(Context context) {
        this.context = context;
        Log.i(LOG_TAG, "ButlerLocation > init");
        obj = new Object();  // for Location loop


        lastGpsLocation = System.currentTimeMillis()/1000;

        if ( Build.VERSION.SDK_INT >= 23 &&
                ContextCompat.checkSelfPermission( context, android.Manifest.permission.ACCESS_FINE_LOCATION ) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission( context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return  ;
        }
        lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);


        settings = PreferenceManager.getDefaultSharedPreferences(this.context);
        String strLocationInterval = settings.getString("location_interval", "300");  // 5 minutes per default
        locationInterval = Integer.parseInt(strLocationInterval);

        int timeInMs = 1000 * locationInterval;
        int distanceInMeters = 0;
        //lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, timeInMs, distanceInMeters, this);
        Log.d(LOG_TAG, "Checking which locations are enabled...");
        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.d(LOG_TAG, "Location by GPS enabled!");
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, timeInMs, distanceInMeters, this);
        }
        // this one must be enabled. Else the onStatusChanged can't be called when the location is enabled on the device
        Log.d(LOG_TAG, "Location by network enabled (no choice ;) )!");
        lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, timeInMs, distanceInMeters, this);
    }

    public void stop() {
        Log.d(LOG_TAG, "Stopping location tracking...");
        try
        {
            if ( Build.VERSION.SDK_INT >= 23 &&
                    ContextCompat.checkSelfPermission( context, android.Manifest.permission.ACCESS_FINE_LOCATION ) != PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission( context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return  ;
            }
            lm.removeUpdates(this);
            lm = null;
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    public void onStatusChanged(String provider, int status, Bundle extras) {
        String newStatus = "";
        switch (status) {
            case LocationProvider.OUT_OF_SERVICE:
                newStatus = "OUT_OF_SERVICE";
                break;
            case LocationProvider.TEMPORARILY_UNAVAILABLE:
                newStatus = "TEMPORARILY_UNAVAILABLE";
                break;
            case LocationProvider.AVAILABLE:
                newStatus = "AVAILABLE";
                break;
        }
        Log.d(LOG_TAG, "onStatusChanged. New status=" + newStatus);
    }
    public void onProviderEnabled(String provider) {}
    public void onProviderDisabled(String provider) {}

    public void onLocationChanged(Location location) {
        // Called when a new location is found by the network location provider.
        Log.i(LOG_TAG,"Location changed, " + location.getAccuracy() + " , " + location.getLatitude()+ "," + location.getLongitude() + "  from " + location.getProvider());
        // TODO : how to use only the best value ? as this is called for both GPS and NETWORK


        WifiManager wifiManager = (WifiManager) this.context.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo;

        String locationName = "";
        wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo.getSupplicantState() == SupplicantState.COMPLETED) {
            String ssid = wifiInfo.getSSID();
            ssid = ssid.replace("\"", "");
            Log.i(LOG_TAG, "Location over wifi. SSID = " + ssid);
            // TODO : do the loop
            String aSsid = settings.getString("location_wifi_1_ssid", "");
            Log.d(LOG_TAG, "Comparing '" + ssid + "' to '" + aSsid + "'");
            if (aSsid.equals(ssid)) {
                String foundLocation = settings.getString("location_wifi_1_name", "");
                Log.i(LOG_TAG, "The SSID '" + ssid + "' is related to the location : " + foundLocation);
                locationName = foundLocation;
            }

        }



        Long now = System.currentTimeMillis()/1000;
        // if the last time we see a GPS signal is bigger than twice the interval, we use the network value
        if (location.getProvider().equals("network")) {
            Log.d(LOG_TAG, "No GPS location, using the network one");
            if (now - lastGpsLocation > locationInterval * 2) {   // TODO : try with 1.5 ?
                Intent i = new Intent("org.domogik.butler.Location");
                i.putExtra("locationName", locationName);
                i.putExtra("latitude", location.getLatitude());
                i.putExtra("longitude", location.getLongitude());
                context.sendBroadcast(i);
                Log.i(LOG_TAG, "Location is sent!");

            }
            else {
                Log.i(LOG_TAG, "Network value only and " + (now - lastGpsLocation) + " < " + (locationInterval * 2) + "");
            }
            // else... yes,.. we could lose a value... maybe to improve ? (TODO)
        }
        else {
            Intent i = new Intent("org.domogik.butler.Location");
            i.putExtra("locationName", locationName);
            i.putExtra("latitude", location.getLatitude());
            i.putExtra("longitude", location.getLongitude());
            context.sendBroadcast(i);
            Log.i(LOG_TAG, "Location is sent!");

        }
        lastGpsLocation = now;

    }

}
