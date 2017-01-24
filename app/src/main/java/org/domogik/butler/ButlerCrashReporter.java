package org.domogik.butler;

import android.app.Application;
import android.os.Environment;

import com.github.anrwatchdog.ANRWatchDog;

import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;

import java.io.File;
import java.io.IOException;

import static org.acra.ReportField.ANDROID_VERSION;
import static org.acra.ReportField.LOGCAT;
import static org.acra.ReportField.PHONE_MODEL;
import static org.acra.ReportField.STACK_TRACE;

/**
 * Created by tiki on 07/10/2016.
 */


@ReportsCrashes(formUri = "http://yourserver.com/yourscript",
        mailTo = "butlerandroid@domogik.org",
        customReportContent = {ANDROID_VERSION, PHONE_MODEL, STACK_TRACE, LOGCAT},
        mode = ReportingInteractionMode.TOAST,
        resToastText = R.string.butler_crash)

public class ButlerCrashReporter extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        /******************************************************************
           Handle the crashes to send a report email with the logs 
        ******************************************************************/
        // The following line triggers the initialization of ACRA
        ACRA.init(this);
        ACRA.getErrorReporter().putCustomData("Release", getResources().getString(R.string.dummy_content));
        new ANRWatchDog().start();

        /******************************************************************
           Log to a file
        ******************************************************************/

        if ( isExternalStorageWritable() ) {

            File appDirectory = new File( Environment.getExternalStorageDirectory() + "/ButlerAndroid" );
            File logDirectory = new File( appDirectory + "/log" );
            File logFile = new File( logDirectory, "logcat" + System.currentTimeMillis() + ".txt" );

            // create app folder
            if ( !appDirectory.exists() ) {
                appDirectory.mkdir();
            }

            // create log folder
            if ( !logDirectory.exists() ) {
                logDirectory.mkdir();
            }

            // clear the previous logcat and then write the new one to the file
            try {
                Process process = Runtime.getRuntime().exec( "logcat -c");
                process = Runtime.getRuntime().exec( "logcat -f " + logFile + " *:I ");
            } catch ( IOException e ) {
                e.printStackTrace();
            }

        } else if ( isExternalStorageReadable() ) {
            // only readable
        } else {
            // not accessible
        }
    }

    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if ( Environment.MEDIA_MOUNTED.equals( state ) ) {
            return true;
        }
        return false;
    }

    /* Checks if external storage is available to at least read */
    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if ( Environment.MEDIA_MOUNTED.equals( state ) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals( state ) ) {
            return true;
        }
        return false;
    }

}
