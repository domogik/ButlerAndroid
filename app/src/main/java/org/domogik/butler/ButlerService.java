package org.domogik.butler;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
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
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Locale;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;


// pocketsphinx
import static edu.cmu.pocketsphinx.SpeechRecognizerSetup.defaultSetup;
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

    // Configuration
    SharedPreferences settings;
    SharedPreferences.OnSharedPreferenceChangeListener listener;
    Boolean continuousDialog = true;    // TODO : get from config ? Or let it hardcoded ?
    Boolean isTTSMute = false;          // TODO : get from config ? Or let it hardcoded ?

    // KeySpotting (PocketSphinx)
    private boolean doVoiceWakeup = false;
    ButlerPocketSphinx pocketSphinx;

    // Receivers
    StatusReceiver statusReceiver;
    UserRequestReceiver userRequestReceiver;
    StartListeningUserRequestReceiver startListeningUserRequestReceiver;
    ResponseReceiver responseReceiver;
    MuteReceiver muteReceiver;



    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.context = this;

        //Toast.makeText(getBaseContext(), "Butler service started", Toast.LENGTH_SHORT).show();      // TODO : DEL
        // TODO : start listening here for keyspotting here ?
        sendNotification("Butler started", "The butler service is started !");

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

        // Preferences listener
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        registerPreferenceListener();

        // Init keyspotting
        doVoiceWakeup = settings.getBoolean("keyspotting_activated", false);
        pocketSphinx = new ButlerPocketSphinx();
        if (doVoiceWakeup) {
            pocketSphinx.init(this);
            pocketSphinx.start();

        }


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
                        pocketSphinx.start();
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
                        pocketSphinx.start();
                    }
                    else {
                        // do nothing
                    }

                }

            }
        };

        settings.registerOnSharedPreferenceChangeListener(listener);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // TODO : start listening here for keyspotting here ?
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(statusReceiver);
        unregisterReceiver(userRequestReceiver);
        unregisterReceiver(startListeningUserRequestReceiver);
        unregisterReceiver(responseReceiver);
        unregisterReceiver(muteReceiver);

        super.onDestroy();
    }










    /*****************************************************************************
     * Helper to send notifications
     */

    public void sendNotification(String notificationTitle, String notificationText) {
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
                        .setSmallIcon(R.drawable.btn_icon)
                        .setContentTitle(notificationTitle)
                        .setContentText(notificationText)
                        .setContentIntent(viewPendingIntent);

        // Get an instance of the NotificationManager service
        NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(this);

        // Build the notification and issues it with notification manager.
        notificationManager.notify(notificationId, notificationBuilder.build());
    }

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

            if (status.equals("LISTENING_ERROR")) {
                // we restart to wait for some event (keyspotting, click on button)
                status = "WAITING";
                Log.i(LOG_TAG, "Status set to " + status);
                Intent i = new Intent("org.domogik.butler.Status");
                i.putExtra("status", status);
                context.sendBroadcast(i);
            }
            else if (status.equals("SPEAKING_DONE")) {
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
                Log.i(LOG_TAG, "Already doing something, not starting the Voice recognition");
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
            // TODO : json object
            // extract data from json
            // publish response over Intent
            // on GUI : display response
            // on Service : add TTS
            //              play response

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
        private TextToSpeech tts;
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
                        String.valueOf(AudioManager.STREAM_NOTIFICATION));
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

            /*
            Authenticator.setDefault(new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(userAuth, passwordAuth.toCharArray());

                }});
            */

            /*
            BASE64Encoder enc = new sun.misc.BASE64Encoder();
            String userpassword = username + ":" + password;
            String encodedAuthorization = enc.encode( userpassword.getBytes() );
            connection.setRequestProperty("Authorization", "Basic "+
                    encodedAuthorization);
            */

                String authString = userAuth + ":" + passwordAuth;
                byte[] authStringB64 = Base64.encode(authString.getBytes(), 0);
                String authorization = "Basic " + new String(authStringB64);
                con.setRequestProperty("Authorization", authorization);

                // TODO DEL HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setDoOutput(true);
                con.setDoInput(true);
                con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                con.setRequestMethod("POST");

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
                    response = jsonObject.getString("text");
                    Log.i(LOG_TAG, "Response from the butler is : " + response);
                } else if (statusCode == 401) {
                    response = "Bad login or password configured";    // TODO : i18n
                } else {
                    response = "Error while requesting the butler. Error number is : " + statusCode;    // TODO : i18n
                }


                Log.i(LOG_TAG, "Call to REST finished");

            } catch (Exception e) {
                Log.e(LOG_TAG, "Error while calling REST : " + e.toString());
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
