package com.mohamed.expenseguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class TaskBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        ExpenseDbHelper db=new ExpenseDbHelper(context); long now=System.currentTimeMillis();
        for(ExpenseDbHelper.TaskItem task:db.getTasks(false)){
            if(task.dueAt>now)TaskReminderReceiver.schedule(context,task.id,task.dueAt);
            else if(task.repeatMinutes>0)TaskReminderReceiver.schedule(context,task.id,now+60_000L);
        }
    }
}
