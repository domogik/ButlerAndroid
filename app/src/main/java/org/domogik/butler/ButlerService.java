package org.domogik.butler;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.AudioManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;


// pocketsphinx
import static edu.cmu.pocketsphinx.SpeechRecognizerSetup.defaultSetup;
import static org.domogik.butler.R.menu.main;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;


/**
 * Created by fritz on 28/12/16.
 */

public class ButlerService extends Service {
    private String LOG_TAG = "BUTLER > ButlerService";
    String status = "WAITING";

    Context context;

    Intent serviceIntent;

    // Configuration
    SharedPreferences settings;
    SharedPreferences.OnSharedPreferenceChangeListener listener;
    Boolean continuousDialog = true;    // TODO : get from config ? Or let it hardcoded ?
    Boolean isTTSMute = false;          // TODO : get from config ? Or let it hardcoded ?
    Boolean getLocation = true;         // TODO : get from config

    // KeySpotting (PocketSphinx)
    private boolean doVoiceWakeup = false;
    ButlerPocketSphinx pocketSphinx;

    // Receivers
    StatusReceiver statusReceiver;
    UserRequestReceiver userRequestReceiver;
    StartListeningUserRequestReceiver startListeningUserRequestReceiver;
    ResponseReceiver responseReceiver;
    MuteReceiver muteReceiver;
    LocationReceiver locationReceiver;

    // wifi list (with receiver)
    WifiReceiver receiverWifi;
    WifiManager mainWifi;
    private final Handler handler = new Handler();
    ArrayList<String> wifiSSIDList = new ArrayList<String>();

    // Screen wakeup
    Boolean doWakeupScreen = true;   // true to allow screen wakeup on the first voice wake up
    PowerManager.WakeLock wakeLock;

    // Notifications
    public static final String CLOSE_ACTION = "close";
    public static final int NOTIFICATION = 1;
    private final NotificationCompat.Builder mNotificationBuilder = new NotificationCompat.Builder(this);
    private NotificationManager mNotificationManager = null;

    // TTS
    private TextToSpeech tts;  // defined here and init in the onCreate() to avoid some time lost to init the engine when the first response is received

    // Location
    private ButlerLocation location = null;
    Thread locationThread = null;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.context = this;

        // Preferences listener
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        registerPreferenceListener();

        //Toast.makeText(getBaseContext(), "Butler service started", Toast.LENGTH_SHORT).show();      // TODO : DEL
        // TODO : start listening here for keyspotting here ?
        if (!settings.getBoolean("hide_notification", false)) {
            sendNotification("Butler started", "The butler service is started !", true);
        }
        // disabled // setupNotifications();
        // disabled // showNotification();

        // Init the receivers
        statusReceiver = new StatusReceiver(this);
        registerReceiver(statusReceiver, new IntentFilter("org.domogik.butler.Status"));
        userRequestReceiver = new UserRequestReceiver(this);
        registerReceiver(userRequestReceiver, new IntentFilter("org.domogik.butler.UserRequest"));
        startListeningUserRequestReceiver = new StartListeningUserRequestReceiver(this);
        registerReceiver(startListeningUserRequestReceiver, new IntentFilter("org.domogik.butler.StartListeningUserRequest"));
        responseReceiver = new ResponseReceiver(this);
        registerReceiver(responseReceiver, new IntentFilter("org.domogik.butler.Response"));
        muteReceiver = new MuteReceiver(this);
        registerReceiver(muteReceiver, new IntentFilter("org.domogik.butler.MuteAction"));
        locationReceiver = new LocationReceiver(this);
        registerReceiver(locationReceiver, new IntentFilter("org.domogik.butler.Location"));

        // Find the available wifi SSID since the application startup.
        // TODO : for now this is not really used. Later we should display them somewhere in preferences to help the user to choose a SSID
        // see http://stackoverflow.com/a/17167318
        mainWifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        receiverWifi = new WifiReceiver();
        registerReceiver(receiverWifi, new IntentFilter(
                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        if(mainWifi.isWifiEnabled()==false)
        {
            mainWifi.setWifiEnabled(true);
        }
        doInback();


        // Init keyspotting
        doVoiceWakeup = settings.getBoolean("keyspotting_activated", false);
        pocketSphinx = new ButlerPocketSphinx();
        if (doVoiceWakeup) {
            pocketSphinx.init(this);
            pocketSphinx.start();
        }

        // Location
        getLocation = settings.getBoolean("location_activated", false);
        location = new ButlerLocation();
        if (getLocation){
            location.init(this);
        }

    }

    public void doInback()
    {
        handler.postDelayed(new Runnable() {

            @Override
            public void run()
            {
                // TODO Auto-generated method stub
                mainWifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);

                receiverWifi = new WifiReceiver();
                registerReceiver(receiverWifi, new IntentFilter(
                        WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
                mainWifi.startScan();
                doInback();
            }
        }, 60000);

    }




    private void registerPreferenceListener()
    {
        listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {

                if (key.equals("keyspotting_activated")) {
                    doVoiceWakeup = settings.getBoolean("keyspotting_activated", false);
                    Log.i(LOG_TAG, "Preferences : keyspotting_activated changed ! New value = " + doVoiceWakeup);
                    if (doVoiceWakeup) {
                        pocketSphinx.init(context);
                        // As the user can reactivate voice wake up while a listening action, we start pocketSphinx only during a WAITING state (else the user will see an error)
                        if (status == "WAITING") {
                            pocketSphinx.start();
                        }
                    }
                    else {
                        pocketSphinx.stop();
                    }

                }
                else if ((key.equals("keyspotting_lang")) || (key.equals("keyspotting_keyphrase")) || (key.equals("keyspotting_threshold"))) {
                    doVoiceWakeup = settings.getBoolean("keyspotting_activated", false);
                    Log.i(LOG_TAG, "Preferences : (keyspotting_lang || keyspotting_keyphrase || keyspotting_threshold) changed ! => Reloading the pocketSphinx engine");
                    if (doVoiceWakeup) {
                        pocketSphinx.init(context);
                        pocketSphinx.stop();
                        Handler handler = new Handler();
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                pocketSphinx.start();
                            }
                        }, 300);

                    }
                    else {
                        // do nothing
                    }

                }
                else if ((key.equals("location_activated")) || (key.equals("location_interval"))) {
                    getLocation = settings.getBoolean("location_activated", false);
                    if (getLocation) {
                        location.init(context);
                    }
                    else {
                        location.stop();
                    }
                    // TODO: handle the new location usage
                    /*
                    if (locationThread != null) {
                        try {
                            // make the thread stop itself
                            location.deactivate();
                        }
                        catch (Exception exception) {
                            Log.w(LOG_TAG, "Unable to stop the location thread");
                            exception.printStackTrace();
                        }

                    }
                    // Location
                    getLocation = settings.getBoolean("location_activated", false);
                    if (getLocation) {
                        String strLocationInterval = settings.getString("location_interval", "300");  // 5 minutes per default
                        final int locationInterval = Integer.parseInt(strLocationInterval);
                        // Do the job in a thread
                        location.activate();
                        locationThread = new Thread(new Runnable() {
                            public void run() {
                                location.findLocation(locationInterval);
                            }
                        });
                        locationThread.start();

                    }
                    else {
                        locationThread = null;
                    }
                    */
                }
                else if ((key.equals("service_stop"))) {
                    Boolean serviceStop = settings.getBoolean("service_stop", false);
                    if (serviceStop) {
                        //context.stopService(new Intent(context, TestService.class));
                        Log.i(LOG_TAG, "Stop the Butler service");
                        ButlerService.this.stopSelf();
                    }
                }


            }
        };

        settings.registerOnSharedPreferenceChangeListener(listener);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // TODO : start listening here for keyspotting here ?
        Log.i(LOG_TAG, "Butler > onStartCommand");
        serviceIntent = intent;
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(LOG_TAG, "Butler > onDestroy");

        hideNotification();

        unregisterReceiver(statusReceiver);
        unregisterReceiver(userRequestReceiver);
        unregisterReceiver(startListeningUserRequestReceiver);
        unregisterReceiver(responseReceiver);
        unregisterReceiver(muteReceiver);
        unregisterReceiver(locationReceiver);

        stopService(serviceIntent);

        // TODO ????? keep ?
        // this is a test to avoid the service to be killed on Android >= 4.4 (kitkat)
        startService(new Intent(context, ButlerService.class));

        super.onDestroy();
    }










    /*****************************************************************************
     * Helpers to send notifications
     */

    public void sendNotification(String notificationTitle, String notificationText, Boolean permanent) {
        if (permanent == null) {
            permanent = false;
        }
        int notificationId = 001;
        int eventId = 0;
        String EXTRA_EVENT_ID = "BUTLER EVENT";
        // Build intent for notification content
        Intent viewIntent = new Intent(this, FullscreenActivity.class);
        viewIntent.putExtra(EXTRA_EVENT_ID, eventId);
        PendingIntent viewPendingIntent =
                PendingIntent.getActivity(this, 0, viewIntent, 0);

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.status_bar_icon2)
                        .setContentTitle(notificationTitle)
                        .setContentText(notificationText)
                        .setContentIntent(viewPendingIntent)
                        .setOngoing(permanent);

        // Get an instance of the NotificationManager service
        NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(this);

        // Build the notification and issues it with notification manager.
        notificationManager.notify(notificationId, notificationBuilder.build());
    }

    public void hideNotification() {
        // Get an instance of the NotificationManager service
        NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(this);

        // Build the notification and issues it with notification manager.
        // TODO : make notification id as a class item
        notificationManager.cancel(001);

    }
/*
    public void displayServiceNotification(String notificationTitle, String notificationText) {
        int notificationId = 002;
        int eventId = 1;
        String EXTRA_EVENT_ID = "BUTLER SERVICE";

        // Build intent for notification content to go on the application
        Intent viewIntent = new Intent(this, FullscreenActivity.class);
        viewIntent.putExtra(EXTRA_EVENT_ID, eventId);
        PendingIntent viewPendingIntent =
                PendingIntent.getActivity(this, 0, viewIntent, 0);

        // Build intent to stop the service
        PendingIntent pendingCloseIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, FullscreenActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .setAction(CLOSE_ACTION),
                0);

        //NotificationCompat.Builder notificationBuilder =
        //        new NotificationCompat.Builder(this)
        if (mNotificationManager == null) {
            mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        }
        mNotificationBuilder
                        .setSmallIcon(R.drawable.btn_icon)
                        .setContentTitle(notificationTitle)
                        .setContentText(notificationText)
                        .setContentIntent(viewPendingIntent)
                        .setCategory(NotificationCompat.CATEGORY_SERVICE)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                                getString(R.string.action_exit), pendingCloseIntent)
                        .setOngoing(true);

                ;

        if (mNotificationManager != null) {
            mNotificationManager.notify(NOTIFICATION, mNotificationBuilder.build());
        }

        // Get an instance of the NotificationManager service
        NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(this);

        // Build the notification and issues it with notification manager.
        notificationManager.notify(notificationId, notificationBuilder.build());


    }
    */

    /* disabled for now as it need to point to FullScreenActity (not ideal to allow the reuse of the code)

    // TODO : custom layout : http://www.androidtutorialsworld.com/custom-notifications-android-example/
    private void setupNotifications() { //called in onCreate()
        if (mNotificationManager == null) {
            mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        }
        // TODO : why this intent open several windows ?
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, FullscreenActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                0);
        PendingIntent pendingCloseIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, FullscreenActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .setAction(CLOSE_ACTION),
                0);
        mNotificationBuilder
                .setSmallIcon(R.drawable.btn_icon)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentTitle(getText(R.string.app_name))
                .setWhen(System.currentTimeMillis())
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                        getString(R.string.action_exit), pendingCloseIntent)
                .setOngoing(true);
    }

    private void showNotification() {
        mNotificationBuilder
                .setTicker(getText(R.string.service_connected))
                .setContentText(getText(R.string.service_connected));
        if (mNotificationManager != null) {
            mNotificationManager.notify(NOTIFICATION, mNotificationBuilder.build());
        }
    }

         */


    /***
     * Receivers
     *************************************************************************/

    class StatusReceiver extends BroadcastReceiver {
        /* Used to catch a request to speak to the Butler
           Can be called from an activity or a keyspotting feature in background
         */
        private Context context;
        public StatusReceiver(Context context) {
            this.context = context;
        }

        private String LOG_TAG = "BUTLER > StatusReceiver";
        private ButlerGoogleVoice gv;

        @Override
        public void onReceive(Context context, Intent arg) {
            // TODO Auto-generated method stub

            status = arg.getStringExtra("status");
            if (!status.equals("LISTENING")) {
                // We don't log for listening to avoid too much spam as each time the voice level change this function is raised
                Log.i(LOG_TAG, "StatusReceiver");
            }


            if (status.equals("LISTENING")) {
                // Wake up the screen (usefull in case of voice wakeup)
                if (doWakeupScreen) {
                    Log.i(LOG_TAG, "Do wake up screen !");

                    // Scren tuned on
                    forceScreenOn();



                }
            }
            else if (status.equals("LISTENING_DONE")) {
                // For next time, allow again screen wake up
                doWakeupScreen = true;
            }
            else if (status.equals("LISTENING_ERROR")) {
                // we restart to wait for some event (keyspotting, click on button)
                status = "WAITING";
                Log.i(LOG_TAG, "Status set to " + status);
                Intent i = new Intent("org.domogik.butler.Status");
                i.putExtra("status", status);
                context.sendBroadcast(i);

                // For next time, allow again screen wake up
                stopForceScreenOn();
                doWakeupScreen = true;

            }
            else if (status.equals("REQUESTING_THE_BUTLER_DONE")) {
                // Turn screen on when we got the response
                // This is usefull for smartwatches where the screen turn off after a few seconds : if the butler need some time to process or if the network is not speed, it allows to read the response instead of only getting the voice!

                // TODO : DEL
                // not needed in fact since we release the wake lock after the speak process
                //forceScreenOn();
            }
            else if (status.equals("SPEAKING_DONE")) {
                // speaking done, we can let the screen turn off
                stopForceScreenOn();

                // An input dialog has failed (most of the time this is related to somehting not understood by google voice or the speaking of a response is finished.
                if (continuousDialog == true) {
                    // We want the discussion to continue without needed to click or do keyspotting again
                    status = "WANT_LISTENING_AGAIN";
                    Log.i(LOG_TAG, "Status set to " + status);
                    Intent i = new Intent("org.domogik.butler.Status");
                    i.putExtra("status", status);
                    context.sendBroadcast(i);

                    // If the continuous dialog is set, when we finish to speak, we start to do voice recognition once
                    Intent i2 = new Intent("org.domogik.butler.StartListeningUserRequest");
                    sendBroadcast(i2);
                }
                else {
                    // we restart to wait for some event (keyspotting, click on button)
                    status = "WAITING";
                    Log.i(LOG_TAG, "Status set to " + status);
                    Intent i = new Intent("org.domogik.butler.Status");
                    i.putExtra("status", status);
                    context.sendBroadcast(i);


                }
            }


        }

        private void forceScreenOn() {
            // The pin code and similar stuff bypass is allowed thanks to the window.addFlags(...) in the activity
            // TODO : make this used only related to a parameter!

            // TODO : move in a function and also use it when a response is received from the butler
            // TODO : move in a function and also use it when a response is received from the butler
            // TODO : move in a function and also use it when a response is received from the butler
            // TODO : move in a function and also use it when a response is received from the butler
            // TODO : move in a function and also use it when a response is received from the butler
            // TODO : move in a function and also use it when a response is received from the butler
            KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
            final KeyguardManager.KeyguardLock kl = km.newKeyguardLock("MyKeyguardLock");
            kl.disableKeyguard();

            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP
                    | PowerManager.ON_AFTER_RELEASE, "MyWakeLock");
            wakeLock.acquire();


            Intent dialogIntent = new Intent(context, FullscreenActivity.class);
            dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(dialogIntent);
            doWakeupScreen = false;
        }

        private void stopForceScreenOn() {
            wakeLock.release();
        }


    }


    class StartListeningUserRequestReceiver extends BroadcastReceiver {
        /* Used to catch a request to speak to the Butler
           Can be called from an activity or a keyspotting feature in background
         */
        private Context context;
        public StartListeningUserRequestReceiver(Context context) {
            this.context = context;
        }

        private String LOG_TAG = "BUTLER > StartListUser"; //shortened :(
        private ButlerGoogleVoice gv;

        @Override
        public void onReceive(Context context, Intent arg) {
            // TODO Auto-generated method stub
            Log.i(LOG_TAG, "StartListeningUserRequestReceiver");
            if ((status.equals("WAITING")) || (status.equals("WANT_LISTENING_AGAIN"))) {
                // We only allow to start google voice recognition when the service is not already doing something like a voice recognition process or a processing or speaking process

                // first we stop PocketSphinx in case it is listening!
                pocketSphinx.stop();

                // Then we start google voice recognition
                gv = new ButlerGoogleVoice();
                gv.startVoiceRecognition(context);
            }
            else {
                Log.i(LOG_TAG, "Already doing something, not starting the Voice recognition. Current status = '" + status + "'");
            }

        }
    }


    class UserRequestReceiver extends BroadcastReceiver implements ButlerDiscussPostAsyncResponse {
        /* When a spoken user request is received and recognized
           This Receiver may be found also on some activities to be displayed
         */

        private String LOG_TAG = "BUTLER > UserRequestRcv";
        Context context;
        public UserRequestReceiver(Context context) {
            this.context = context;
        }

        @Override
        public void onReceive(Context context, Intent arg) {
            // TODO Auto-generated method stub
            Log.i(LOG_TAG, "UserRequestReceiver");

            // Status
            Intent i = new Intent("org.domogik.butler.Status");
            i.putExtra("status", "REQUESTING_THE_BUTLER");
            context.sendBroadcast(i);

            this.context = context;
            String text = arg.getStringExtra("text");
            // Toast.makeText(context, "User request received : " + text, Toast.LENGTH_LONG).show(); // TODO : DEL

            /*** Call the Butler REST service from Domogik **************************/
            // TODO : configure
            //String restUrl = "https://192.168.1.50:50000/rest/butler/discuss";
            //String restUrl = "https://78.198.200.93:50000/rest/butler/discuss";
            //final String userAuth = "admin";
            //final String passwordAuth = "milo1919";

            // TODO: DEL // SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            String adminUrl = settings.getString("domogik_admin_url", "notconfigured");
            String userAuth = settings.getString("domogik_user", "notconfigured");
            String passwordAuth = settings.getString("domogik_password", "notconfigured");

            String restUrl = adminUrl + "/rest/butler/discuss";
            String user = userAuth;
            String source = "ButlerAndroid - " + user;

            // Build the data to POST
            String postData = "{\"text\" : \"" + text + "\", \"source\" : \"" + source + "\"}";
            Log.i(LOG_TAG, "Data to post to the butler : " + postData);

            // Do the call
            ButlerDiscussPostAsyncTask butlerDiscussPostAsyncTask = new ButlerDiscussPostAsyncTask();
            butlerDiscussPostAsyncTask.delegate = this;
            butlerDiscussPostAsyncTask.execute(restUrl, userAuth, passwordAuth, postData);
        }

        //this override the implemented method from asyncTask
        @Override
        public void processFinish(int httpStatusCode, String response) {
            //Here you will receive the result fired from async class
            //of onPostExecute(result) method.
            Log.i(LOG_TAG, "Data received from the butler : HTTP_CODE='" + httpStatusCode + "', data=" + response);
            if (httpStatusCode == 200) {
                // OK
                // The handler is needed to be able to Toast
                Handler h = new Handler(Looper.getMainLooper());
                h.post(new Runnable() {
                    public void run() {
                        // TODO : find a way to display the httpcode in the error
                        // Toast.makeText(context, "Querying REST : OK", Toast.LENGTH_LONG).show();
                    }
                });
            } else  if ((httpStatusCode == 401) || (httpStatusCode == 403)) {           // TODO : this is not working ?????
                Handler h = new Handler(Looper.getMainLooper());
                h.post(new Runnable() {
                    public void run() {
                        Toast.makeText(context, "Authentication error while querying Domogik over Rest.", Toast.LENGTH_LONG).show();                    }
                });
            }
            else {
                // oups, error !
                // The handler is needed to be able to Toast
                Handler h = new Handler(Looper.getMainLooper());
                h.post(new Runnable() {
                    public void run() {
                        // TODO : find a way to display the httpcode in the error
                        Toast.makeText(context, "Error while querying Domogik over Rest.", Toast.LENGTH_LONG).show();
                    }
                });
            }

            /*** Start processing the data **************************/
            Log.i(LOG_TAG, "Start processing REST response...");

            Intent i = new Intent("org.domogik.butler.Status");
            i.putExtra("status", "REQUESTING_THE_BUTLER_DONE");
            context.sendBroadcast(i);

            Intent i2 = new Intent("org.domogik.butler.Response");
            i2.putExtra("text", response);
            context.sendBroadcast(i2);
        }
    }


    class ResponseReceiver extends BroadcastReceiver implements TextToSpeech.OnInitListener {
        /* When a butler response is received
           This Receiver may be found also on some activities to be displayed
         */

        private String LOG_TAG = "GUI > ResponseRcv";
        Context context;
        public ResponseReceiver(Context context) {
            this.context = context;
        }
        // TTS
        //private TextToSpeech tts;
        private Boolean isTtsReady = false;
        String textToSpeak = "";

        // Bluetooth
        AudioManager audioManager;

        @Override
        public void onReceive(Context context, Intent arg) {
            // TODO Auto-generated method stub
            Log.i(LOG_TAG, "ResponseReceiver");

            // Status
            Intent i = new Intent("org.domogik.butler.Status");
            i.putExtra("status", "SPEAKING");
            context.sendBroadcast(i);


            this.context = context;
            String text = arg.getStringExtra("text");
            this.textToSpeak = text;

            // Speak only if not muted
            isTTSMute = settings.getBoolean("mute", false);
            if (!isTTSMute) {
                // Bluetooth
                // require bluetooth usage (if available)
                //audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
                //audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                //audioManager.startBluetoothSco();
                //audioManager.setBluetoothScoOn(true);

                // Init the TestToSpeech (tts)
                tts = new TextToSpeech(context, this);
                tts.setOnUtteranceProgressListener(listener);
            }
            else {
                // Muted, so end of speaking immediatly
                Intent i2 = new Intent("org.domogik.butler.Status");
                i2.putExtra("status", "SPEAKING_DONE");
                context.sendBroadcast(i2);
            }

        }


        // TTS functions ///////////////////////////////////////////////////////////////

        @Override
        public void onInit(int status) {
        /* TTS init
        */
            Log.i(LOG_TAG, "BUTLER TTS > Function onInit");
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale(Locale.getDefault().getISO3Language(), Locale.getDefault().getISO3Country()));
                isTtsReady = true;
                Log.i(LOG_TAG, "BUTLER TTS > Function onInit > SUCCESS");

                Log.i(LOG_TAG, "BUTLER TTS > Speak : " + this.textToSpeak);
                HashMap<String, String> hash = new HashMap<String, String>();
                hash.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID,
                        String.valueOf(AudioManager.STREAM_VOICE_CALL));
//                      String.valueOf(AudioManager.STREAM_NOTIFICATION));
                /*
                STREAM_ALARM         (for alarms)
                STREAM_DTMF          (for DTMF Tones)
                STREAM_MUSIC         (for music playback)
                STREAM_NOTIFICATION  (for notifications)
                STREAM_RING          (for the phone ring)
                STREAM_SYSTEM        (for system sounds)
                STREAM_VOICE_CALL    (for phone calls)
                 */
                tts.speak(this.textToSpeak, TextToSpeech.QUEUE_ADD, hash);  // TODO : improve

            } else {
                isTtsReady = false;
                Log.e(LOG_TAG, "BUTLER TTS > Unable to set TTS to ready (check your locales)");
                // TODO : TOAST
                // TODO : i18n
                Toast.makeText(this.context, "Butler : error during the TTS init.", Toast.LENGTH_SHORT).show();
            }


        }

        UtteranceProgressListener listener = new UtteranceProgressListener() {

            @Override
            public void onStart(String utteranceId) {
                Log.i(LOG_TAG, "BUTLER TTS > speak start");
                // This actions is already done when we request speaking
                // But, if we don't do it here, when there are 2 continuous tts actions (queue is full),
                // we need to put back the status as IS_SPEAKING

                // TODO / setStatus(IS_SPEAKING);

            }

            @Override
            public void onError(String utteranceId) {
                Log.i(LOG_TAG, "BUTLER TTS > speak error");
                //Toast.makeText(this.context, "Butler : error during the TTS action (speaking).", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDone(String utteranceId) {
                Log.i(LOG_TAG, "BUTLER TTS > speak done");

                // Speaking finished
                Intent i = new Intent("org.domogik.butler.Status");
                i.putExtra("status", "SPEAKING_DONE");
                context.sendBroadcast(i);

                // Stop bluetooth usage
                //audioManager.setMode(AudioManager.MODE_NORMAL);
                //audioManager.stopBluetoothSco();
                //audioManager.setBluetoothScoOn(false);

            }
        };
    }


    class MuteReceiver extends BroadcastReceiver {
        /* Used to catch a request to speak to the Butler
           Can be called from an activity or a keyspotting feature in background
         */
        private Context context;
        public MuteReceiver(Context context) {
            this.context = context;
        }

        private String LOG_TAG = "BUTLER > MuteReceiver"; //shortened :(

        @Override
        public void onReceive(Context context, Intent arg) {
            // TODO Auto-generated method stub
            Log.i(LOG_TAG, "MuteReceiver");
            isTTSMute = !isTTSMute;
            Intent i = new Intent("org.domogik.butler.MuteStatus");
            i.putExtra("mute", isTTSMute);
            context.sendBroadcast(i);

            // Save
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean("mute", isTTSMute);
            editor.commit();


        }
    }


    class LocationReceiver extends BroadcastReceiver implements ButlerLocationPostAsyncResponse {
        /* When a location is sent bu ButlerLocation
         */

        private String LOG_TAG = "BUTLER > LocationRcv";
        Context context;
        public LocationReceiver(Context context) {
            this.context = context;
        }

        @Override
        public void onReceive(Context context, Intent arg) {
            // TODO Auto-generated method stub
            Log.i(LOG_TAG, "LocationReceiver");

            this.context = context;
            String locationName = arg.getStringExtra("locationName");
            String latitude = String.valueOf(arg.getDoubleExtra("latitude", 0));
            String longitude = String.valueOf(arg.getDoubleExtra("longitude", 0));

            String adminUrl = settings.getString("domogik_admin_url", "notconfigured");
            String userAuth = settings.getString("domogik_user", "notconfigured");
            String passwordAuth = settings.getString("domogik_password", "notconfigured");

            String restUrl = adminUrl + "/rest/position/" + userAuth + "/";
            String user = userAuth;
            String source = "ButlerAndroid - " + user;

            // Build the data to POST
            String postLocationName = ",\"location_name\" : \"" + locationName + "\"";
            String postData = "[{\"latitude\" : \"" + latitude + "\", \"longitude\" : \"" + longitude + "\"" + postLocationName + "}]";
            Log.i(LOG_TAG, "Data to post to the butler : " + postData);

            // Do the call
            ButlerLocationPostAsyncTask locationPostAsyncTask = new ButlerLocationPostAsyncTask();
            locationPostAsyncTask.delegate = this;
            locationPostAsyncTask.execute(restUrl, userAuth, passwordAuth, postData);
        }

        //this override the implemented method from asyncTask
        @Override
        public void processFinish(int httpStatusCode, final String response) {

            //Here you will receive the result fired from async class
            //of onPostExecute(result) method.
            Log.i(LOG_TAG, "Data received from the butler : HTTP_CODE='" + httpStatusCode + "', data=" + response);

            /************ not needed here **************
            if (httpStatusCode == 200) {
                // OK
                // The handler is needed to be able to Toast
                Handler h = new Handler(Looper.getMainLooper());
                h.post(new Runnable() {
                    public void run() {
                        // TODO : find a way to display the httpcode in the error
                        // Toast.makeText(context, "Querying REST : OK", Toast.LENGTH_LONG).show();
                    }
                });
            } else  if ((httpStatusCode == 401) || (httpStatusCode == 403)) {           // TODO : this is not working ?????
                Handler h = new Handler(Looper.getMainLooper());
                h.post(new Runnable() {
                    public void run() {
                        //Toast.makeText(context, "Authentication error while querying Domogik over Rest.", Toast.LENGTH_LONG).show();                    }
                });
            }
            else {
                // oups, error !
                // The handler is needed to be able to Toast
                Handler h = new Handler(Looper.getMainLooper());
                h.post(new Runnable() {
                    public void run() {
                        // TODO : find a way to display the httpcode in the error
                        //Toast.makeText(context, "Error while querying Domogik over Rest.", Toast.LENGTH_LONG).show();
                    }
                });
            }
             */

            /*** Start processing the data **************************/
            Log.i(LOG_TAG, "Start processing REST response...");

            // just... nothing to do here ;)
            // or at least display the error :
            if (!response.equals("")) {
                if (!settings.getBoolean("location_errors_hidden", true)) {
                    // The handler is needed to be able to Toast
                    Handler h = new Handler(Looper.getMainLooper());
                    h.post(new Runnable() {
                        public void run() {

                            Toast.makeText(context, response, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }
    }






    class WifiReceiver extends BroadcastReceiver
    {
        public void onReceive(Context c, Intent intent)
        {

            // TODO : use this or not ? for now this is not used. We check only for the connected wifi in the ButlerLocation class
            List<ScanResult> wifiList;
            wifiList = mainWifi.getScanResults();
            for(int i = 0; i < wifiList.size(); i++)
            {
                Log.d(LOG_TAG, "Wifi : " + wifiList.get(i).SSID);
                if (! wifiSSIDList.contains(wifiList.get(i).SSID)) {
                    Log.i(LOG_TAG, "New unkwown Wifi SSID found : " + wifiList.get(i).SSID);
                    wifiSSIDList.add(wifiList.get(i).SSID);
                }
            }


        }
    }

    /***
     * REST related functions
     ******************************************/


    class ButlerDiscussPostAsyncTask extends AsyncTask<String, Void, String> {
        public ButlerDiscussPostAsyncResponse delegate = null;
        private String LOG_TAG = "BUTLER > ButlerDiscuss";

        @Override
        protected String doInBackground(String... data) {

            final String restUrl = data[0];
            final String userAuth = data[1];
            final String passwordAuth = data[2];
            final String postData = data[3];

            HttpURLConnection urlConnection = null;
            // TODO DEL String json = null;
            int statusCode = 999;
            String response = "";


            try {


                URL url = new URL(restUrl);
                HttpURLConnection con = null;

                // Allow all no validated ssl certificates for now
                if (url.getProtocol().toLowerCase().equals("https")) {
                    trustAllHosts();
                    HttpsURLConnection https = (HttpsURLConnection) url.openConnection();
                    https.setHostnameVerifier(DO_NOT_VERIFY);
                    con = https;
                } else {
                    con = (HttpURLConnection) url.openConnection();
                }

                String authString = userAuth + ":" + passwordAuth;
                byte[] authStringB64 = Base64.encode(authString.getBytes(), 0);
                String authorization = "Basic " + new String(authStringB64);
                con.setRequestProperty("Authorization", authorization);

                // TODO DEL HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setDoOutput(true);
                con.setDoInput(true);
                con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                con.setRequestMethod("POST");
                Log.i(LOG_TAG, "REST url to call : " + restUrl);

                // JSONObject cred = new JSONObject();
                // cred.put("name","my_name")

                OutputStream os = con.getOutputStream();
                os.write(postData.getBytes("UTF-8"));
                os.close();

                InputStream inputStream = con.getInputStream();
                String result = InputStreamToString(inputStream);

                statusCode = con.getResponseCode();
                Log.i(LOG_TAG, "REST HTTP Response code : " + statusCode);
                // Authentication forbidden
                if (statusCode == 200) {
                    // all ok, process result
                    JSONObject jsonObject = new JSONObject(result);
                    Log.d(LOG_TAG, "REST HTTP Response content : " + result);
                    response = jsonObject.getString("text");
                    Log.i(LOG_TAG, "Response from the butler is : " + response);
                } else if (statusCode == 401) {
                    response = "Bad login or password configured";    // TODO : i18n
                } else {
                    response = "Error while requesting the butler. Error number is : " + statusCode;    // TODO : i18n
                }


                Log.i(LOG_TAG, "Call to REST finished");

            } catch (Exception e) {
                Log.e(LOG_TAG, "Error while calling REST to query the butler : " + e.toString());
            }


            delegate.processFinish(statusCode, response);
            return null;
        }

        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(String result) {
            //Toast.makeText(getBaseContext(), "Data Sent!", Toast.LENGTH_LONG).show();
        }


        /***
         * functions to allow all SSL certificates
         *************************************/
        final HostnameVerifier DO_NOT_VERIFY = new HostnameVerifier() {
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };

        /**
         * Trust every server - dont check for any certificate
         */
        private void trustAllHosts() {
            // Create a trust manager that does not validate certificate chains
            TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[]{};
                }

                public void checkClientTrusted(X509Certificate[] chain,
                                               String authType) throws CertificateException {
                }

                public void checkServerTrusted(X509Certificate[] chain,
                                               String authType) throws CertificateException {
                }
            }};

            // Install the all-trusting trust manager
            try {
                SSLContext sc = SSLContext.getInstance("TLS");
                sc.init(null, trustAllCerts, new java.security.SecureRandom());
                HttpsURLConnection
                        .setDefaultSSLSocketFactory(sc.getSocketFactory());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }







    class ButlerLocationPostAsyncTask extends AsyncTask<String, Void, String> {
        public ButlerLocationPostAsyncResponse delegate = null;
        private String LOG_TAG = "BUTLER > Location";

        @Override
        protected String doInBackground(String... data) {

            final String restUrl = data[0];
            final String userAuth = data[1];
            final String passwordAuth = data[2];
            final String postData = data[3];

            HttpURLConnection urlConnection = null;
            // TODO DEL String json = null;
            int statusCode = 999;
            String response = "";


            try {


                URL url = new URL(restUrl);
                HttpURLConnection con = null;

                // Allow all no validated ssl certificates for now
                if (url.getProtocol().toLowerCase().equals("https")) {
                    trustAllHosts();
                    HttpsURLConnection https = (HttpsURLConnection) url.openConnection();
                    https.setHostnameVerifier(DO_NOT_VERIFY);
                    con = https;
                } else {
                    con = (HttpURLConnection) url.openConnection();
                }

                String authString = userAuth + ":" + passwordAuth;
                byte[] authStringB64 = Base64.encode(authString.getBytes(), 0);
                String authorization = "Basic " + new String(authStringB64);
                con.setRequestProperty("Authorization", authorization);

                // TODO DEL HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setDoOutput(true);
                con.setDoInput(true);
                con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                con.setRequestMethod("POST");
                Log.i(LOG_TAG, "REST url to call : " + restUrl);

                // JSONObject cred = new JSONObject();
                // cred.put("name","my_name")

                OutputStream os = con.getOutputStream();
                os.write(postData.getBytes("UTF-8"));
                os.close();

                InputStream inputStream = con.getInputStream();
                String result = InputStreamToString(inputStream);

                statusCode = con.getResponseCode();
                Log.i(LOG_TAG, "REST HTTP Response code : " + statusCode);
                // Authentication forbidden
                if (statusCode == 200) {
                    // all ok, process result
                    Log.d(LOG_TAG, "REST HTTP Response content : " + result);
                    JSONObject jsonObject = new JSONObject(result);
                    String responseStatus = jsonObject.getString("status");
                    if (responseStatus.equals("ERROR")) { 
                        //Toast.makeText(context, "Domogik does not accept the location for the person '\" + userAuth + \"'. Reason is : " + jsonObject.getString("error"), Toast.LENGTH_LONG).show();
                        response = "Domogik does not accept the location for the person '\" + userAuth + \"'. Reason is : " + jsonObject.getString("error");
                        Log.w(LOG_TAG, "Domogik does not accept the location for the person '" + userAuth + "'. Reason is : " + jsonObject.getString("error"));
                    }
                } else if (statusCode == 401) {
                    response = "Bad login or password configured";    // TODO : i18n
                } else {
                    response = "Error while requesting the butler. Error number is : " + statusCode;    // TODO : i18n
                }


                Log.i(LOG_TAG, "Call to REST finished");

            } catch (Exception e) {
                Log.e(LOG_TAG, "Error while calling REST to send location : " + e.toString());
            }


            delegate.processFinish(statusCode, response);
            return null;
        }

        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(String result) {
            //Toast.makeText(getBaseContext(), "Data Sent!", Toast.LENGTH_LONG).show();
        }


        /***
         * functions to allow all SSL certificates
         *************************************/
        final HostnameVerifier DO_NOT_VERIFY = new HostnameVerifier() {
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };

        /**
         * Trust every server - dont check for any certificate
         */
        private void trustAllHosts() {
            // Create a trust manager that does not validate certificate chains
            TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[]{};
                }

                public void checkClientTrusted(X509Certificate[] chain,
                                               String authType) throws CertificateException {
                }

                public void checkServerTrusted(X509Certificate[] chain,
                                               String authType) throws CertificateException {
                }
            }};

            // Install the all-trusting trust manager
            try {
                SSLContext sc = SSLContext.getInstance("TLS");
                sc.init(null, trustAllCerts, new java.security.SecureRandom());
                HttpsURLConnection
                        .setDefaultSSLSocketFactory(sc.getSocketFactory());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public String InputStreamToString(InputStream in, int bufSize) {
        // From : http://tutorielandroid.francoiscolin.fr/recupjson.php

        /**
         * @param in      : buffer with the php result
         * @param bufSize : size of the buffer
         * @return : the string corresponding to the buffer
         */
        final StringBuilder out = new StringBuilder();
        final byte[] buffer = new byte[bufSize];
        try {
            for (int ctr; (ctr = in.read(buffer)) != -1; ) {
                out.append(new String(buffer, 0, ctr));
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot convert stream to string", e);
        }
        // On retourne la chaine contenant les donnees de l'InputStream
        return out.toString();
    }

    public String InputStreamToString(InputStream in) {
        // From : http://tutorielandroid.francoiscolin.fr/recupjson.php

        /**
         * @param in : buffer with the php result
         * @return : the string corresponding to the buffer
         */
        // On appelle la methode precedente avec une taille de buffer par defaut
        return InputStreamToString(in, 1024);
    }


}
