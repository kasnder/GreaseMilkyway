package net.kollnig.greasemilkyway;

import android.content.Context;
import android.content.SharedPreferences;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;

import android.preference.PreferenceManager;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class NotificationListener extends NotificationListenerService {

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String pkg = sbn.getPackageName();

        //Prevent capturing notifications from your own app
        if (pkg.equals(getPackageName())) return;

        CharSequence title = sbn.getNotification().extras.getCharSequence("android.title");
        CharSequence text = sbn.getNotification().extras.getCharSequence("android.text");

        if (title == null && text == null) return;

        // Convert package name to app name
        String appName = getAppNameFromPackage(pkg);
        String entry = appName + ": • " + title + ": " + text;
        Log.d("NotificationListener", "Captured: " + entry);

        // Store safely
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("notif_store", Context.MODE_PRIVATE);
        Set<String> original = prefs.getStringSet("collected_notifications", new HashSet<>());
        Set<String> copy = new HashSet<>(original);
        copy.add(entry);
        prefs.edit().putStringSet("collected_notifications", copy).apply();

        Log.d("NotificationListener", "✅ Scheduled summary worker.");

        // Schedule worker
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(HourlySummaryWorker.class)
                .setInitialDelay(1, TimeUnit.MINUTES)
                .build();
        WorkManager.getInstance(getApplicationContext()).enqueue(work);
        cancelNotification(sbn.getKey());
    }

    private String getAppNameFromPackage(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(ai).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName; 
        }
    }

}
