package cn.banwu.parentpilot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Rebinds the notification listener after an in-place app update. */
public final class PackageUpdatedReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;
        WeChatNotificationListener.reconnectAfterPackageUpdate(context);
        TodoWidgetProvider.refresh(context);
    }
}
