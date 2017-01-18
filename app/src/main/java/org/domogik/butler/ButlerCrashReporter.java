package org.domogik.butler;

import android.app.Application;

import com.github.anrwatchdog.ANRWatchDog;

import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;

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
        // The following line triggers the initialization of ACRA
        ACRA.init(this);
        ACRA.getErrorReporter().putCustomData("Release", getResources().getString(R.string.dummy_content));
        new ANRWatchDog().start();


    }
}
