package com.ak.assistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.AlarmManager
import android.os.Build
import androidx.core.app.NotificationCompat

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val memory = LocalMemory(context)
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            memory.reminders().filter { it.at > System.currentTimeMillis() }.forEach { reminder ->
                val alarmIntent = Intent(context, ReminderReceiver::class.java).putExtra("id", reminder.id).putExtra("title", reminder.title)
                val pending = PendingIntent.getBroadcast(context, reminder.id, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at, pending)
            }
            return
        }
        val title = intent.getStringExtra("title") ?: "Your reminder"
        val id = intent.getIntExtra("id", (System.currentTimeMillis() % Int.MAX_VALUE).toInt())
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel("akai_reminders", "AK AI reminders", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, "akai_reminders")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("AK AI reminder")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setContentIntent(open).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build()
        manager.notify(id, notification)
        LocalMemory(context).removeReminder(id)
    }
}
