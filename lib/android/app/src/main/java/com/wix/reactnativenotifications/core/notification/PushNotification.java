package com.wix.reactnativenotifications.core.notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.net.Uri;
import android.media.RingtoneManager;
import android.media.AudioAttributes;

import androidx.core.app.NotificationCompat;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.facebook.react.bridge.ReactContext;
import com.wix.reactnativenotifications.core.AppLaunchHelper;
import com.wix.reactnativenotifications.core.AppLifecycleFacade;
import com.wix.reactnativenotifications.core.AppLifecycleFacade.AppVisibilityListener;
import com.wix.reactnativenotifications.core.AppLifecycleFacadeHolder;
import com.wix.reactnativenotifications.core.InitialNotificationHolder;
import com.wix.reactnativenotifications.core.JsIOHelper;
import com.wix.reactnativenotifications.core.NotificationIntentAdapter;
import com.wix.reactnativenotifications.core.ProxyService;

import static com.wix.reactnativenotifications.Defs.NOTIFICATION_OPENED_EVENT_NAME;
import static com.wix.reactnativenotifications.Defs.NOTIFICATION_RECEIVED_EVENT_NAME;

public class PushNotification implements IPushNotification {

    final protected Context mContext;
    final protected AppLifecycleFacade mAppLifecycleFacade;
    final protected AppLaunchHelper mAppLaunchHelper;
    final protected JsIOHelper mJsIOHelper;
    final protected PushNotificationProps mNotificationProps;
    final protected AppVisibilityListener mAppVisibilityListener = new AppVisibilityListener() {
        @Override
        public void onAppVisible() {
            mAppLifecycleFacade.removeVisibilityListener(this);
            dispatchImmediately();
        }

        @Override
        public void onAppNotVisible() {
        }
    };

    public static IPushNotification get(Context context, Bundle bundle) {
        Context appContext = context.getApplicationContext();
        if (appContext instanceof INotificationsApplication) {
            return ((INotificationsApplication) appContext).getPushNotification(context, bundle, AppLifecycleFacadeHolder.get(), new AppLaunchHelper());
        }
        return new PushNotification(context, bundle, AppLifecycleFacadeHolder.get(), new AppLaunchHelper(), new JsIOHelper());
    }

    protected PushNotification(Context context, Bundle bundle, AppLifecycleFacade appLifecycleFacade, AppLaunchHelper appLaunchHelper, JsIOHelper JsIOHelper) {
        mContext = context;
        mAppLifecycleFacade = appLifecycleFacade;
        mAppLaunchHelper = appLaunchHelper;
        mJsIOHelper = JsIOHelper;
        mNotificationProps = createProps(bundle);
    }

    @Override
    public void onReceived() throws InvalidNotificationException {
        if (!mAppLifecycleFacade.isAppVisible()) {
            postNotification((Notification) null,null);
        }
        notifyReceivedToJS();
    }

    @Override
    public void onOpened() {
        digestNotification();
        clearAllNotifications();
    }

    @Override
    public int onPostRequest(Integer notificationId,String channelID) {
        return postNotification(notificationId, channelID);
    }

    @Override
    public PushNotificationProps asProps() {
        return mNotificationProps.copy();
    }

    protected int postNotification(Integer notificationId, String channelID) {
        final PendingIntent pendingIntent = getCTAPendingIntent();
        final Notification notification = buildNotification(pendingIntent, channelID);
        return postNotification(notification, notificationId);
    }

    protected void digestNotification() {
        if (!mAppLifecycleFacade.isReactInitialized()) {
            setAsInitialNotification();
            launchOrResumeApp();
            return;
        }
        final ReactContext reactContext = mAppLifecycleFacade.getRunningReactContext();
        if (reactContext.getCurrentActivity() == null) {
            setAsInitialNotification();
        }

        if (mAppLifecycleFacade.isAppVisible()) {
            dispatchImmediately();
        } else {
            dispatchUponVisibility();
        }
    }

    protected PushNotificationProps createProps(Bundle bundle) {
        return new PushNotificationProps(bundle);
    }

    protected void setAsInitialNotification() {
        InitialNotificationHolder.getInstance().set(mNotificationProps);
    }

    protected void dispatchImmediately() {
        notifyOpenedToJS();
    }

    protected void dispatchUponVisibility() {
        mAppLifecycleFacade.addVisibilityListener(getIntermediateAppVisibilityListener());

        // Make the app visible so that we'll dispatch the notification opening when visibility changes to 'true' (see
        // above listener registration).
        launchOrResumeApp();
    }

    protected AppVisibilityListener getIntermediateAppVisibilityListener() {
        return mAppVisibilityListener;
    }

    protected PendingIntent getCTAPendingIntent() {
        final Intent cta = new Intent(mContext, ProxyService.class);
        return NotificationIntentAdapter.createPendingNotificationIntent(mContext, cta, mNotificationProps);
    }

    protected Notification buildNotification(PendingIntent intent,String channelID) {
        return getNotificationBuilder(intent,channelID).build();
    }

    protected NotificationCompat.Builder getNotificationBuilder(PendingIntent intent, String channelID) {

        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);

        final NotificationCompat.Builder notification = new NotificationCompat.Builder(mContext)
                .setContentTitle(mNotificationProps.getTitle())
                .setContentText(mNotificationProps.getBody())
                .setContentIntent(intent)
                .setAutoCancel(true);

        setUpIcon(notification);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            boolean isSilent = mNotificationProps.isSilent();

            /* Setup Notification channels */
            setupNotificationChannels();

            /** Setting the notification channel to Notification */
            if (isSilent) {
                 notification.setChannelId("LocalAzanNotiSilent");
            } else if(!soundFileName.isEmpty() && soundFileName !== "default") {
                notification.setChannelId("LocalAzanNotification");
            } else {
               notification.setChannelId("RadioBaksho");
            }

        } else {
            setupNotificationForBeforeOreo(notification);
        }

        return notification;
    }

    private void setupNotificationForBeforeOreo(NotificationCompat.Builder notification) {

        boolean isSilent = mNotificationProps.isSilent();
        boolean shouldVibrate = mNotificationProps.shouldVibrate();

        String soundFileName = mNotificationProps.getSound();

        if (!shouldVibrate) {
            notification.setVibrate(new long[]{0L});
        } else {
            notification.setVibrate(new long[] { 1000, 1000, 1000, 1000, 1000 });
        }

        if (isSilent) {
            notification.setSound(null);
        } else if(!soundFileName.isEmpty() && soundFileName !== "default") {
            Uri soundFileUri = Uri.parse("android.resource://" + mContext.getApplicationContext().getPackageName() + "/raw/" + soundFileName);
            notification.setSound(soundFileUri);
        } else {
          notification.setDefaults(Notification.DEFAULT_SOUND);
        }
    }

    private void setupNotificationChannels(){

        final NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);

        /** Creating RadioBaksho channel */

        NotificationChannel channel = new NotificationChannel("RadioBaksho", "Radio Baksho Channel", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Radio Baksho Channel");
        channel.enableVibration(true);
        channel.setBypassDnd(true);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

        notificationManager.createNotificationChannel(channel);

        /** Creating LocalAzanNotification channel */

        NotificationChannel channel1 = new NotificationChannel("LocalAzanNotification", "Local Custom Azan Notification", NotificationManager.IMPORTANCE_HIGH);
        channel1.setDescription("Local Azan Notification Channel to receive azan related notification with azan sound and vibration");
        channel1.enableVibration(true);
        channel1.setBypassDnd(true);
        channel1.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

        Uri azanSoundUri = Uri.parse("android.resource://" + mContext.getApplicationContext().getPackageName() + "/raw/" + "azan");

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build();

        channel1.setSound(azanSoundUri, audioAttributes);
        notificationManager.createNotificationChannel(channel1);

        /** Creating LocalAzanNotiSilent channel */

        Uri silentSoundUri = Uri.parse("android.resource://" + mContext.getApplicationContext().getPackageName() + "/raw/" + "silent");

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build();

        NotificationChannel channel2 = new NotificationChannel("LocalAzanNotiSilent", "Local Azan Silent Notification ", NotificationManager.IMPORTANCE_HIGH);
        channel2.setDescription("Local Azan Notification Channel to receive azan related notification with no sound and no vibration");
        channel2.setSound(silentSoundUri, audioAttributes);
        channel2.enableVibration(false);
        channel2.setBypassDnd(true);
        channel2.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

        notificationManager.createNotificationChannel(channel2);

    }

    private void setUpIcon(NotificationCompat.Builder notification) {
        int iconResId = getAppResourceId("notification_icon", "drawable");
        if (iconResId != 0) {
            notification.setSmallIcon(iconResId);
        } else {
            notification.setSmallIcon(mContext.getApplicationInfo().icon);
        }
        setUpLargeIcon(notification);
        //setUpIconColor(notification);
    }

    private void setUpLargeIcon(NotificationCompat.Builder notification) {
        int iconResId = getAppResourceId("large", "drawable");
        notification.setLargeIcon(BitmapFactory.decodeResource(mContext.getResources(), iconResId));
        setUpIconColor(notification);
    }

    private void setUpIconColor(NotificationCompat.Builder notification) {
        int colorResID = getAppResourceId("colorAccent", "color");
        if (colorResID != 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int color = mContext.getResources().getColor(colorResID);
            notification.setColor(color);
        }
    }

    protected int postNotification(Notification notification, Integer notificationId) {
        int id = notificationId != null ? notificationId : createNotificationId(notification);
        postNotification(id, notification);
        return id;
    }

    protected void postNotification(int id, Notification notification) {
        final NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(id, notification);
    }

    protected void clearAllNotifications() {
        final NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancelAll();
    }

    protected int createNotificationId(Notification notification) {
        return (int) System.nanoTime();
    }

    private void notifyReceivedToJS() {
        mJsIOHelper.sendEventToJS(NOTIFICATION_RECEIVED_EVENT_NAME, mNotificationProps.asBundle(), mAppLifecycleFacade.getRunningReactContext());
    }

    private void notifyOpenedToJS() {
        Bundle response = new Bundle();
        response.putBundle("notification", mNotificationProps.asBundle());

        mJsIOHelper.sendEventToJS(NOTIFICATION_OPENED_EVENT_NAME, response, mAppLifecycleFacade.getRunningReactContext());
    }

    protected void launchOrResumeApp() {
        final Intent intent = mAppLaunchHelper.getLaunchIntent(mContext);
        mContext.startActivity(intent);
    }

    private int getAppResourceId(String resName, String resType) {
        return mContext.getResources().getIdentifier(resName, resType, mContext.getPackageName());
    }
}
