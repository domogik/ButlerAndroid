package org.domogik.butler;

import android.app.Activity;
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
import android.support.v4.content.LocalBroadcastManager;
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

public class ButlerPocketSphinx extends Activity implements RecognitionListener {
    private String LOG_TAG = "BUTLER > ButlerPocketSp";

    String status = "WAITING";

    Context context;

    // Receivers
    StatusReceiver statusReceiver;

    // PocketSphinx
    private static final String KWS_SEARCH = "wakeup";      // TODO : understand why this one is needed....
    private String KEYPHRASE = "there is no keyphrase defined";
    private Float threshold = 1e-5f;    // 1e-60, 1e-40, 1e-20, 1e-10
    private SpeechRecognizer recognizer;
    private String partialResult = "";
    private boolean isRecognizerOK = false;
    private boolean doVoiceWakeUp = false;

    // Config
    SharedPreferences settings;

    // Handler for delayed commands
    Handler handler;

    public void init(Context context) {
        this.context = context;
        Log.i(LOG_TAG, "ButlerPocketSphinx > start");
        // Init the receivers
        statusReceiver = new StatusReceiver(this);
        //LocalBroadcastManager.getInstance(context).registerReceiver(statusReceiver, new IntentFilter("org.domogik.butler.Status"));
        context.registerReceiver(statusReceiver, new IntentFilter("org.domogik.butler.Status"));

        // Init keyspotting
        //keySpottingInit();
        //startKeySpotting();

    }


    @Override
    public void onDestroy() {
        unregisterReceiver(statusReceiver);

        // shut pocketsphinx
        stopKeySpotting();
        if (recognizer != null) {
            recognizer.cancel();
            recognizer.shutdown();
        }

        super.onDestroy();
    }


    /***
     * Keyspotting with PocketSphinx
     */


    public void start() {
        Log.i(LOG_TAG, "ButlerPocketSphinx > start");

        settings = PreferenceManager.getDefaultSharedPreferences(context);
        doVoiceWakeUp = settings.getBoolean("keyspotting_activated", false);
        KEYPHRASE = settings.getString("keyspotting_keyphrase", "");

        if (!doVoiceWakeUp) {
            Log.w(LOG_TAG, "BUTLER > Start : it seems that configuration changed and keyspotting is no more activated... stopping");
            return;
        }

        Assets assets;
        File assetsDir;
        try {
            assets = new Assets(context);
            assetsDir = assets.syncAssets();
        } catch (IOException e) {
            // TODO : handle the error
            Log.e(LOG_TAG, "POCKETSPHINX > Error while loading assets");
            return;
        }

        String lang = Locale.getDefault().getLanguage();        // TODO : get from config
        File acousticModel;
        File dictionnary;
        Log.i(LOG_TAG, "POCKETSPHINX > Lang = " + lang);
        if (lang.equals("fr")) {
            //acousticModel = new File(assetsDir, "fr/fr-ptm");
            //dictionnary = new File(assetsDir, "fr/frenchWords62K.dic");
            acousticModel = new File(assetsDir, "fr/fr-ptm");
            dictionnary = new File(assetsDir, "fr/frenchWords62K.dic");
        }
        else if (lang.equals("en")) {
            acousticModel = new File(assetsDir, "en/en-us-ptm");
            dictionnary = new File(assetsDir, "en/cmudict-en-us.dict");
        }
        else {
            Log.e(LOG_TAG, "POCKETSPHINX > Language not recognised : skip recognizer setup");
            return;
        }

        // build the threshold
        try {
            String s_threshold = settings.getString("dmg_keyspot_threshold", "20");
            s_threshold = "1e-" + s_threshold + "f";
            threshold = Float.parseFloat(s_threshold);
        }
        catch (Exception e) {
            threshold = Float.parseFloat("1e-20f");
        }

        // init the recognizer
        try {
            recognizer = defaultSetup()
                .setAcousticModel(acousticModel)
                .setDictionary(dictionnary)

                // To disable logging of raw audio comment out this call (takes a lot of space on the device)
                //.setRawLogDir(assetsDir)

                // Threshold to tune for keyphrase to balance between false alarms and misses
                // 1e-60, 1e-40, 1e-20, 1e-10
                .setKeywordThreshold(threshold)

                // Use context-independent phonetic search, context-dependent is too slow for mobile
                .setBoolean("-allphone_ci", true)

                .getRecognizer();
        } catch (IOException e) {
            // TODO : handle the error
            Log.e(LOG_TAG, "POCKETSPHINX > Error while doing the setup of the recognizer (PocketSPhinx)");
            return;
        }

        recognizer.addListener(this);

        /** In your application you might not need to add all those searches.
         * They are added here for demonstration. You can leave just one.
         */

        // Create keyword-activation search.
        try {
            recognizer.addKeyphraseSearch(KWS_SEARCH, KEYPHRASE.toLowerCase());
            isRecognizerOK = true;
        }
        catch (RuntimeException e) {
            // We display a message because the choosen keyphrrase is not valid
            Toast.makeText(context, "The keyphrase configured to wake up the butler is not valid.", Toast.LENGTH_LONG).show();
            Log.e(LOG_TAG, "oups");
            isRecognizerOK = false;

        }
        // Start listening :)
        startKeySpotting();
    }


    public void startKeySpotting() {
        // Init keyspotting
        //keySpottingInit();

    if (!isRecognizerOK) {
            Log.w(LOG_TAG, "BUTLER > Start keyspotting : not starting keypotting as the recognizer is not valid!");
            return;
        }
        if (status.equals("WAITING")) {   // a security
            //stopKeySpotting();
            Log.i(LOG_TAG, "BUTLER > Start keyspotting");
            // TODO : change timeout to something bigger ?
            // TODO : KWS_SEARCH = KEYPHRASE ?

            // Just in case....
            // wait for 300ms to be sure microphone is free for use
            if (recognizer != null) {
                handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // We check that the configuration have not been changed (else on the timeout/no match, it will crash
                        doVoiceWakeUp = settings.getBoolean("keyspotting_activated", false);
                        Log.i(LOG_TAG, "doVWU=" + doVoiceWakeUp);
                        Log.i(LOG_TAG, "status=" + status);
                        if (doVoiceWakeUp) {
                            recognizer.startListening(KWS_SEARCH, 100000);
                        }
                        else {
                            Log.w(LOG_TAG, "BUTLER > Start keyspotting : it seems that configuration changed and keyspotting is no more activated... stopping");
                        }
                    }
                }, 300);
                // TODO : replace the delay by a loop of 5 try with a smaller delay
            }
        }
    }



    public void stopKeySpotting() {
        if (!isRecognizerOK) {
            Log.w(LOG_TAG, "BUTLER > Stop keyspotting : not stopping keypotting as the recognizer is not valid!");
            return;
        }
        recognizer.stop();
    }

/*
        if (recognizer != null) {
            Log.i(LOG_TAG, "BUTLER > Stop keyspotting");
            //recognizer.stop();
            //recognizer.cancel();   // TODO : DEL ?? just a test
            stop();
        }
    }

*/
    public void stop() {
        if (!isRecognizerOK) {
            Log.w(LOG_TAG, "BUTLER > Stop : not stopping keypotting as the recognizer is not valid!");
            return;
        }
        Log.i(LOG_TAG, "ButlerPocketSphinx > stop");
        if (recognizer != null) {
            recognizer.stop();
            recognizer.cancel();
            recognizer.shutdown();
        }
    }




    // Pocketsphinx functions ////////////////////////////////////////////////////////

    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        /* In partial result we get quick updates about current hypothesis. In
           keyword spotting mode we can react here, in other modes we need to wait
           for final result in onResult.
         */
        if (hypothesis == null)
            return;
        Log.i(LOG_TAG, "POCKETSPHINX > Function onPartialResult : " + hypothesis.getHypstr());
        partialResult = hypothesis.getHypstr();
        processResult(hypothesis.getHypstr());
    }

    public void processResult(String theResult) {
        String text = theResult.toLowerCase();
        if (text.equals(KEYPHRASE.toLowerCase())) {
            stopKeySpotting();
            // Request the service to start the dialog
            Intent i = new Intent("org.domogik.butler.StartListeningUserRequest");
            context.sendBroadcast(i);

        }
        else {
            // TODO : how to reinit ?
            stopKeySpotting();
            startKeySpotting();
        }
    }

    @Override
    public void onResult(Hypothesis hypothesis) {
        /* This callback is called when we stop the recognizer.
         */
        if (hypothesis == null)
            return;
        Log.i(LOG_TAG, "POCKETSPHINX > Function onResult : " + hypothesis.getHypstr());
        // we process the result only if we have not processed the same thing as partial result
        if (!hypothesis.getHypstr().equals(partialResult)) {
            processResult(hypothesis.getHypstr());
        }

        // In this application, this function is not used as we process the result in onPartialResult function
        /*
        if (hypothesis != null) {
            String text = hypothesis.getHypstr();
        }
        */
    }

    @Override
    public void onBeginningOfSpeech() {
    }

    @Override
    public void onEndOfSpeech() {
        /* We stop recognizer here to get a final result
           This function is called on end of speech
         */
        Log.i(LOG_TAG, "POCKETSPHINX > Function onEndOfSpeech");
        // Restart pocketsphinx listening
        startKeySpotting();
    }

    @Override
    public void onError(Exception error) {
        Log.d(LOG_TAG, "POCKETSPHINX > Function onError");
        Toast.makeText(context, "PocketSphinx error : " + error.getMessage(), Toast.LENGTH_SHORT).show();
        // TODO : relaunch listening ?????
    }

    @Override
    public void onTimeout() {
        Log.d(LOG_TAG, "POCKETSPHINX > Function onTimeout");
        Toast.makeText(context, "Timeout", Toast.LENGTH_SHORT).show();
        // Restart pocketsphinx listening
        startKeySpotting();
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

        private String LOG_TAG = "PocketSphinx";

        @Override
        public void onReceive(Context context, Intent arg) {
            // TODO Auto-generated method stub
            if (doVoiceWakeUp) {
                status = arg.getStringExtra("status");
                if (!status.equals("LISTENING")) {
                    // We don't log for listening to avoid too much spam as each time the voice level change this function is raised
                    Log.i(LOG_TAG, "StatusReceiver. status = " + status);
                }
                // The status will be used only to check that keyspotting can run with no risk (no other action in progress : listening or speaking)
                if (status.equals("WAITING")) {
                    Log.i(LOG_TAG, "PocketSphinx status receiver : start key spotting !");
                    start();
                }
            }
        }
    }



}
