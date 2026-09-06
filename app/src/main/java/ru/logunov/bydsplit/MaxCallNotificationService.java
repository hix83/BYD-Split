package ru.logunov.bydsplit;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.*;

/** Reads call metadata only; never executes answer/reject PendingIntents. */
public final class MaxCallNotificationService extends NotificationListenerService {
    private final Set<String> calls=new HashSet<>();
    @Override public void onListenerConnected() {
        calls.clear();
        StatusBarNotification[] active=getActiveNotifications();
        if(active!=null)for(StatusBarNotification n:active)remember(n);
        publish();
    }
    private void remember(StatusBarNotification sbn) {
        if(!"ru.oneme.app".equals(sbn.getPackageName()))return;
        Notification n=sbn.getNotification();
        int type=n.extras==null?0:n.extras.getInt("android.callType",0);
        boolean live=MaxCallClassifier.isLiveCall(sbn.getPackageName(),n.category,
                (n.flags&Notification.FLAG_ONGOING_EVENT)!=0,n.fullScreenIntent!=null,type);
        if(live) {
            calls.add(sbn.getKey());
        } else {calls.remove(sbn.getKey());}
    }
    @Override public void onNotificationPosted(StatusBarNotification n) {
        if(!"ru.oneme.app".equals(n.getPackageName()))return;
        remember(n);publish();
    }
    @Override public void onNotificationRemoved(StatusBarNotification n) {
        if(calls.remove(n.getKey()))publish();
    }
    private void publish(){MaxCallRouter.setNotificationActive(this,!calls.isEmpty());}
    @Override public void onListenerDisconnected(){calls.clear();MaxCallRouter.setNotificationActive(this,false);}
}
