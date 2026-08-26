package com.mohamed.expenseguard;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;

public class TaskReminderReceiver extends BroadcastReceiver {
    public static final String CHANNEL = "task_reminders";
    @Override public void onReceive(Context context, Intent intent) {
        long id=intent.getLongExtra("taskId",-1); if(id<0)return;
        ExpenseDbHelper db=new ExpenseDbHelper(context); ExpenseDbHelper.TaskItem task=db.getTask(id);
        if(task==null||task.completed==1)return;
        NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"تذكيرات المهام",NotificationManager.IMPORTANCE_HIGH));
        Intent open=new Intent(context,TaskActivity.class); open.putExtra("taskId",id);
        PendingIntent pi=PendingIntent.getActivity(context,(int)id,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        String priority=task.priority==3?"مهمة عاجلة":task.priority==2?"مهمة مهمة":"تذكير بمهمة";
        nm.notify((int)id,new NotificationCompat.Builder(context,CHANNEL).setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle(priority+": "+task.title).setContentText(task.notes==null?"":task.notes).setContentIntent(pi).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build());
        if(task.repeatMinutes>0){long next=nextTime(task);db.saveTask(task.id,task.title,task.notes,task.priority,next,task.repeatMinutes,false);schedule(context,task.id,next);}
    }
    private long nextTime(ExpenseDbHelper.TaskItem t){
        java.util.Calendar c=java.util.Calendar.getInstance(); c.setTimeInMillis(Math.max(System.currentTimeMillis(),t.dueAt));
        if(t.repeatMinutes==-1)c.add(java.util.Calendar.MONTH,1);else c.add(java.util.Calendar.MINUTE,(int)t.repeatMinutes); return c.getTimeInMillis();
    }
    public static void schedule(Context context,long taskId,long at){
        if(at<=0)return; AlarmManager am=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent i=new Intent(context,TaskReminderReceiver.class).putExtra("taskId",taskId);
        PendingIntent pi=PendingIntent.getBroadcast(context,(int)taskId,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        try{if(Build.VERSION.SDK_INT>=23)am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);else am.setExact(AlarmManager.RTC_WAKEUP,at,pi);}catch(SecurityException e){if(Build.VERSION.SDK_INT>=23)am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);else am.set(AlarmManager.RTC_WAKEUP,at,pi);}
    }
    public static void cancel(Context context,long taskId){AlarmManager am=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);PendingIntent pi=PendingIntent.getBroadcast(context,(int)taskId,new Intent(context,TaskReminderReceiver.class),PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);if(pi!=null)am.cancel(pi);}
}
