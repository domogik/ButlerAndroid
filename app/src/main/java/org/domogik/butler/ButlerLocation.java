package org.domogik.butler;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

/**
 * Created by fritz on 14/01/17.
 */

public class ButlerLocation {
    private String LOG_TAG = "BUTLER > Location";
    Context context;
    private Object obj;
    private boolean runLocationThread = false;
    private boolean running = false;

    public void init(Context context) {
        this.context = context;
        Log.i(LOG_TAG, "ButlerLocation > init");
        obj = new Object();  // for Location loop
    }

    public void activate() {
        this.runLocationThread = true;
    }

    public void deactivate() {
        this.runLocationThread = false;
        running = false;
        // TODO : find a proper way to stop the thread findLocation from here !
        // TODO : find a proper way to stop the thread findLocation from here !
        // TODO : find a proper way to stop the thread findLocation from here !
        // TODO : find a proper way to stop the thread findLocation from here !
//        try {
            obj.notify();
/*
        }
        catch (InterruptedException e){
            Log.e(LOG_TAG, "Error while stopping the location thread");
            e.printStackTrace();
        }
        */

    }

    public void findLocation(int interval) {
        /* interval : in seconds */
        if (running == true) {
            Log.i(LOG_TAG, "A location thread is already running. Not starting a new one!");
            // TODO :can we do something here to stop the already existing one ?
            //return;

        }
        try {
            running = true;
            boolean gps_enabled = false;
            boolean network_enabled = false;

            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

            synchronized (obj) {
                while (runLocationThread) {
                    try {
                        gps_enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
                        network_enabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

                        Location net_loc = null, gps_loc = null, finalLoc = null;

                        if (gps_enabled)
                            gps_loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                        if (network_enabled)
                            net_loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

                        if (gps_loc != null && net_loc != null) {

                            //smaller the number more accurate result will
                            if (gps_loc.getAccuracy() > net_loc.getAccuracy())
                                finalLoc = net_loc;
                            else
                                finalLoc = gps_loc;

                            // I used this just to get an idea (if both avail, its upto you which you want to take as I've taken location with more accuracy)

                        } else {

                            if (gps_loc != null) {
                                finalLoc = gps_loc;
                            } else if (net_loc != null) {
                                finalLoc = net_loc;
                            }
                        }

                        if (finalLoc != null) {
                            // This one will be catched by the ButlerService
                            Intent i = new Intent("org.domogik.butler.Location");
                            i.putExtra("latitude", finalLoc.getLatitude());
                            i.putExtra("longitude", finalLoc.getLongitude());
                            context.sendBroadcast(i);

                            Log.i(LOG_TAG, "Location is : " + finalLoc);
                            //latitude = location.getLatitude();
                            //longitude = location.getLongitude();
                        }
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "Error while getting the location");
                        e.printStackTrace();
                    }
                    obj.wait(interval * 1000); // 3 000 000 = 5 minutes
                }
            }
        } catch (Exception e) {
            running = false;
            Log.e(LOG_TAG, "Error while setting the location");
            e.printStackTrace();
            Log.i(LOG_TAG, "End of the thread (because of the previous error)!");
            running = false;
        }
        Log.i(LOG_TAG, "End of the thread!");
        running = false;
    }
}
