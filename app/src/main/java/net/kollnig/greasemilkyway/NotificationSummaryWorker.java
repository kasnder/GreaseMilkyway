package net.kollnig.greasemilkyway;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;



import java.util.Set;

public class NotificationSummaryWorker extends Worker {

    public NotificationSummaryWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d("HourlySummaryWorker", " Worker triggered!");

        SharedPreferences prefs = getApplicationContext().getSharedPreferences("notif_store", Context.MODE_PRIVATE);
        Set<String> stored = prefs.getStringSet("collected_notifications", null);

        if (stored == null || stored.isEmpty()) {
            String message = "No new notifications.";
            Log.d("HourlySummaryWorker", " " + message);
            showFallbackNotification(message);  // still notify user
            return Result.success();
        }

        StringBuilder summary = new StringBuilder();
        for (String notif : stored) {
            summary.append("• ").append(notif).append("\n");
        }

        String finalSummary = summary.toString().trim();
        Log.d("HourlySummaryWorker", " Summary:\n" + finalSummary);

        showFallbackNotification(finalSummary);  

        prefs.edit().remove("collected_notifications").apply();  // 🧹 cleanup
        return Result.success();
    }

    private void showFallbackNotification(String content) {
        String channelId = "hourly_summary_channel";
        Context context = getApplicationContext();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Notification Summary",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Notification Summary")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManagerCompat.from(context).notify(1001, builder.build());
    }
}
