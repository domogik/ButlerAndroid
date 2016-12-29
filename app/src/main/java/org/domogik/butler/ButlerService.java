package org.domogik.butler;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;

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


class UserRequestReceiver extends BroadcastReceiver  implements ButlerDiscussPostAsyncResponse {
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

        /*** Call the Butler REST service from Domogik **************************/
        // TODO : configure
        String restUrl = "https://192.168.1.50:50000/rest/butler/discuss";
        final String userAuth = "admin";
        final String passwordAuth = "milo1919";
        String user = "Fred";
        String source = "ButlerAndroid - " + user;

        // Build the data to POST
        String postData = "{\"text\" : \"" + text + "\", \"source\" : \"" + source + "\"}";
        Log.i(LOG_TAG, "Data to post to the butler : " + postData);

        // Build authenticator
        //Authenticator.setDefault(new Authenticator() {
        //    protected PasswordAuthentication getPasswordAuthentication() {
        //        return new PasswordAuthentication(userAuth, passwordAuth.toCharArray());
        //    }
        //});

        // Do the call
        ButlerDiscussPostAsyncTask butlerDiscussPostAsyncTask = new ButlerDiscussPostAsyncTask();
        butlerDiscussPostAsyncTask.delegate = this;
        butlerDiscussPostAsyncTask.execute();
    }

    //this override the implemented method from asyncTask
    @Override
    public void processFinish(String output){
        //Here you will receive the result fired from async class
        //of onPostExecute(result) method.
        Log.i(LOG_TAG, "Data received from the butler : " + output);
    }
}


/*** REST related functions ******************************************/


class ButlerDiscussPostAsyncTask extends AsyncTask<String, Void, String> {
    public ButlerDiscussPostAsyncResponse delegate = null;

    @Override
    protected String doInBackground(String... urls) {
        delegate.processFinish("hello");
        return null;
    }
    // onPostExecute displays the results of the AsyncTask.
    @Override
    protected void onPostExecute(String result) {
        //Toast.makeText(getBaseContext(), "Data Sent!", Toast.LENGTH_LONG).show();
    }
}