package org.domogik.butler;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

/**
 * Created by fritz on 28/12/16.
 */

public class ButlerService extends Service {
    private String LOG_TAG = "BUTLER > ButlerService";
    String tag="ButlerService";

    // Receivers
    UserRequestReceiver userRequestReceiver;
    StartListeningUserRequestReceiver startListeningUserRequestReceiver;

    @Override
    public IBinder onBind(Intent intent){
        return null;
    }
    @Override
    public void onCreate(){
        super.onCreate();
        //TODO : DEL mp = MediaPlayer.create(getApplicationContext(), R.raw.song);
        Toast.makeText(getBaseContext(), "Butler service started", Toast.LENGTH_SHORT).show();       // TODO : DEL
        // TODO : start listening here for keyspotting here ?

        // Init the receivers
        userRequestReceiver = new UserRequestReceiver();
        registerReceiver(userRequestReceiver, new IntentFilter("org.domogik.butler.UserRequest"));
        startListeningUserRequestReceiver = new StartListeningUserRequestReceiver();
        registerReceiver(startListeningUserRequestReceiver, new IntentFilter("org.domogik.butler.StartListeningUserRequest"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        // TODO : start listening here for keyspotting here ?
        return START_STICKY;
    }

    @Override
    public void onDestroy(){
        // TODO : stop listening for keyspotting
        unregisterReceiver(userRequestReceiver);
        super.onDestroy();
    }

}




/*** Receivers *************************************************************************/

class StartListeningUserRequestReceiver extends BroadcastReceiver {
    /* Used to catch a request to speak to the Butler
       Can be called from an activity or a keyspotting feature in background
     */
    private String LOG_TAG = "BUTLER > StartListUser"; //shortened :(
    private ButlerGoogleVoice gv;

    @Override
    public void onReceive(Context context, Intent arg) {
        // TODO Auto-generated method stub
        Log.i(LOG_TAG, "StartListeningUserRequestReceiver");
        String text = arg.getAction();
        // Toast.makeText(context, "Start Listening User Request received : " + text, Toast.LENGTH_LONG).show(); // TODO : DEL
        gv = new ButlerGoogleVoice();
        gv.startVoiceRecognition(context);

    }
}


class UserRequestReceiver extends BroadcastReceiver {
    /* When a spoken user request is received and recognized
       This Receiver may be found also on some activities to be displayed
     */
    private String LOG_TAG = "BUTLER > UserRequestRcv";

    @Override
    public void onReceive(Context context, Intent arg) {
        // TODO Auto-generated method stub
        Log.i(LOG_TAG, "UserRequestReceiver");
        String text = arg.getStringExtra("text");
        // Toast.makeText(context, "User request received : " + text, Toast.LENGTH_LONG).show(); // TODO : DEL
    }
}