package uma.hbs;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.SensorEventListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import uma.hbs.db.TableDataSource;
import uma.hbs.model.Action;
import uma.hbs.model.Activity;
import uma.hbs.model.HBSensor;

/**
 * Created by Administrator on 4/02/2016.
 */
public class AnalogWatchFaceService extends CanvasWatchFaceService {
    private static final String TAG = "FaceService";
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
        static final int MSG_UPDATE_TIME = 0;
        Time mTime;
        boolean mLowBitAmbient;
        boolean mMute;
        int set = 1;
        boolean h = false;
        boolean servico = false;

        Bitmap mBackgroundBitmap, mBackgroundWhiteBitmap;
        Bitmap mBackgroundScaledBitmap;

        Paint mHourPaint;
        Paint mMinutePaint;
        Paint mSecondPaint;
        Paint mTickPaint;
        Paint hbsPaint;

        TableDataSource dataSource;

        GoogleApiClient googleClient;
        String typeGlobal = "";
        int sizeHBSGlobal = 0, sizeActionGlobal=0, sizeActivityGlobal=0;

        float[] hbsArray = new float[12];

        boolean menuState = false, sendLock = false;

        long glanceStartTime=0l;

        long lastTapTime = 0l;

        long peekCardNotificationGlobal = 0, notificationTime = 0;

        String drawInterface="HRV"; //  TODO HRV , ACC or CONTROL


        final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_UPDATE_TIME:
                        if (Log.isLoggable(TAG, Log.VERBOSE)) {
                            Log.v(TAG, "updating time");
                        }
                        invalidate();
                        if (shouldTimerBeRunning()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs = INTERACTIVE_UPDATE_RATE_MS
                                    - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                        }
                        break;
                }
            }
        };

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        boolean mRegisteredTimeZoneReceiver = false;



        @Override
        public void onCreate(SurfaceHolder holder) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onCreate");
            }
            super.onCreate(holder);
             /* initialize your watch face */






            /*Intent i = new Intent(getApplicationContext(), HBSService.class);
            i.putExtra("foo", "bar");
            getApplicationContext().startService(i);*/

            //startService(new Intent(getApplicationContext(), HBSService.class));

            mTime = new Time();
            mTime.setToNow();
            long currentTime = mTime.toMillis(false);



            googleClient = new GoogleApiClient.Builder(getApplicationContext())
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();

            googleClient.connect();


            dataSource = new TableDataSource(getApplicationContext());
            dataSource.open();

            dataSource.deleteTableData("hbsensor"); // TODO delete all database rows
            dataSource.deleteTableData("activity");
            dataSource.deleteTableData("action");
            /*
            for(long i=0;i<12;i++){
                insertHBSensorRow(1455721200000L + (300000L*i), 0.5f*(i+1), i);
                Log.d(TAG, "----------------------------------------------------------");
            } */

            //asdad
            listAllRows("hbsensor");
            listAllRows("activities");



            setWatchFaceStyle(new WatchFaceStyle.Builder(AnalogWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setStatusBarGravity(Gravity.CENTER_VERTICAL)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            Resources resources = AnalogWatchFaceService.this.getResources();
            Drawable backgroundDrawable = resources.getDrawable(R.drawable.bg);
            mBackgroundBitmap = ((BitmapDrawable) backgroundDrawable).getBitmap();

            hbsPaint = new Paint();
            hbsPaint.setARGB(255, 255, 255, 255);
            hbsPaint.setStrokeWidth(8.f);
            hbsPaint.setAntiAlias(true);

        }


//---------------------------------------------------------connect
        @Override
        public void onConnected(Bundle connectionHint) {
            //Log.v(TAG3, "OnConnected entered");
            boolean canTransferData = false;
            String HANDHELD_DATA_PATH = "/handheld_data";
            // Create a DataMap object and send it to the data layer
            DataMap dataMap = new DataMap();
            dataMap.putString("type", typeGlobal);
            if (typeGlobal.equals("null")){
                canTransferData = true;
            }


            else if (typeGlobal.equals("dbSend")){
                //get n actions and send to tablet and update
                List<HBSensor> q = dataSource.findAllHBSSet(0l, "100");
                sizeHBSGlobal = q.size();
                if(sizeHBSGlobal != 0) {
                    JSONArray jsonArrayHBS = new JSONArray();
                    for (HBSensor a : q) {
                        //Log.d(TAG3, "Action id: " + a.getId() + " act: " + a.getAction() + " time: " + a.getTime() + " set: " + a.getSet() + " Notification: " + a.getPeekCardNotification() + " AccessOtherApps: " + a.getAccessOtherApps());
                        JSONObject objhbs = new JSONObject();
                        try {
                            objhbs.put("id", a.getId());
                            objhbs.put("time", a.getTime());
                            objhbs.put("sdnn", a.getSdnn());
                            objhbs.put("section", a.getSection());
                            objhbs.put("activetime", a.getActiveTime());
                            Log.d("DATATRANSFER", "JSON OBJ" + objhbs.toString());
                            jsonArrayHBS.put(objhbs);
                        } catch (JSONException e) {
                            e.printStackTrace();
                            //Log.d("DATATRANSFER", "JSON OBJ EXCEPTIONN");
                        }
                    }
                    canTransferData = true;
                    //Log.d("JSON ARRAY", "JSON ARRAY" + jsonArrayActions.toString());
                    dataMap.putString("JsonHBSArray", jsonArrayHBS.toString());
                }
                else{
                    dataMap.putString("JsonHBSArray", "null");
                    //Log.d("JSON", "JsonActionsArray null");
                }


                List<Action> q2 = dataSource.findAllActionsSet(0l, "100");
                sizeActionGlobal = q2.size();
                if(sizeActionGlobal != 0) {
                    JSONArray jsonArrayActions = new JSONArray();
                    for (Action a : q2) {
                        //Log.d(TAG3, "Action id: " + a.getId() + " act: " + a.getAction() + " time: " + a.getTime() + " set: " + a.getSet() + " Notification: " + a.getPeekCardNotification() + " AccessOtherApps: " + a.getAccessOtherApps());
                        JSONObject objAction = new JSONObject();
                        try {
                            objAction.put("time1", a.getTime());
                            objAction.put("time2", a.getTime2());
                            objAction.put("id", a.getId());
                            objAction.put("pCard", a.getPeekCardNotification());
                            objAction.put("otherApps", a.getAccessOtherApps());
                            Log.d("JSON", "JSON OBJ" + objAction.toString());
                            jsonArrayActions.put(objAction);
                        } catch (JSONException e) {
                            e.printStackTrace();
                            //Log.d("JSON", "JSON OBJ EXCEPTIONN");
                        }
                    }
                    canTransferData = true;
                    //Log.d("JSON ARRAY", "JSON ARRAY" + jsonArrayActions.toString());
                    dataMap.putString("JsonActionsArray", jsonArrayActions.toString());
                }
                else{
                    dataMap.putString("JsonActionsArray", "null");
                    //Log.d("JSON", "JsonActionsArray null");
                }

                //get and activities and send to tablet and update
                List<Activity> q3 = dataSource.findAllActivitiesSet(0l, "50");
                sizeActivityGlobal = q3.size();
                if(sizeActivityGlobal != 0) {
                    JSONArray jsonArray = new JSONArray();
                    for (Activity a : q3) {
                        //Log.d(TAG3, "Activity id: " + a.getId2() + " start: " + a.getStart() + " finish: " + a.getFinish() + " steps: " + a.getStep() + " set: " + a.getSet());
                        JSONObject obj = new JSONObject();
                        try {
                            obj.put("steps", a.getStep());
                            obj.put("start", a.getStart());
                            obj.put("finish", a.getFinish());
                            obj.put("id2", a.getId2());
                            //Log.d("JSON", "JSON OBJ" + obj.toString());
                            jsonArray.put(obj);
                        } catch (JSONException e) {
                            e.printStackTrace();
                            //Log.d("JSON", "JSON OBJ EXCEPTION");
                        }
                    }
                    canTransferData = true;
                    //Log.d("JSON ARRAY", "JSON ARRAY" + jsonArray.toString());
                    dataMap.putString("JsonActivitiesArray", jsonArray.toString());
                }else{
                    dataMap.putString("JsonActivitiesArray", "null");
                    //Log.d("JSON", "JsonActivitiesArray null");
                }



            }

            if(canTransferData){
                //Requires a new thread to avoid blocking the UI
                new SendToDataLayerThread(HANDHELD_DATA_PATH, dataMap).start();
            }else{
                Log.d("DATATRANSFER", "CONNECT BUT NO DATA TO SEND");
            }

        }

        // Placeholders for required connection callbacks
        @Override
        public void onConnectionSuspended(int cause) {
            Log.v("DATATRANSFER", "DataMap Connection Suspended because: " + cause);
        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            Log.v("DATATRANSFER", "DataMap Connection failed");
        }

        class SendToDataLayerThread extends Thread {
            String path;
            DataMap dataMap;

            // Constructor for sending data objects to the data layer
            SendToDataLayerThread(String p, DataMap data) {
                path = p;
                dataMap = data;
            }

            public void run() {
                boolean t = false;
                NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(googleClient).await();
                for (Node node : nodes.getNodes()) {
                    // Construct a DataRequest and send over the data layer
                    PutDataMapRequest putDMR = PutDataMapRequest.create(path);
                    putDMR.getDataMap().putAll(dataMap);
                    PutDataRequest request = putDMR.asPutDataRequest();
                    DataApi.DataItemResult result = Wearable.DataApi.putDataItem(googleClient, request).await();
                    if (result.getStatus().isSuccess()) {
                        Log.d("DATATRANSFER", "DataMap: " + dataMap + " sent to: " + node.getDisplayName());
                        t=true;
                    }else {
                        Log.d("DATATRANSFER", "ERROR: failed to send DataMap");
                    }
                }
                if(!t){
                    Log.d("DATATRANSFER", "ERROR: failed to send DataMap");
                }
            }
        }




//------------------------------------------conect close

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            //Close Connection
            dataSource.close();
            if (null != googleClient && googleClient.isConnected()) {
                googleClient.disconnect();
            }
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
             /* get device features (burn-in, low-bit ambient) */

            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onPropertiesChanged: low-bit ambient = " + mLowBitAmbient);
            }
        }

        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Tap Command: " + tapType);
            }

            mTime.setToNow();
            lastTapTime = mTime.toMillis(false);

            switch(tapType) {
                case TAP_TYPE_TOUCH:
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    break;
                case TAP_TYPE_TAP:
                    if (!menuState && x >= 135 && x<=185 && y >= 135 && y<=185){
                        menuState = true; //enter menu
                    }else if (menuState && x >= 135 && x<=185 && y >= 135 && y<=185){
                        menuState = false; //leave menu
                        sendLock = false;
                    }
                    else if (!servico && menuState &&  y<160){
                        startService(new Intent(getApplicationContext(), HBSService.class));
                        servico = true; //turn on service
                    }
                    else if (servico && menuState && y<160){
                        stopService(new Intent(getApplicationContext(), HBSService.class));
                        servico = false; //turn off service
                    }
                    else if (menuState && y>160 && !sendLock){
                        typeGlobal = "null";
                        googleClient.reconnect();
                        typeGlobal = "dbSend";
                        googleClient.reconnect();
                        sendLock = true;
                    }



            }
            //Log.d("TAP", "Tap Command:" + tapType + " mTouchCommandTotal:" + mTouchCommandTotal + " mTouchCancelCommandTotal:" + mTouchCancelCommandTotal + " mTapCommandTotal;" + mTapCommandTotal);
            invalidate();
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            // called each minute
            /* the time changed */
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onTimeTick: ambient = " + isInAmbientMode());
            }
            mTime.setToNow();
            long tempTime = mTime.toMillis(false);

            /*if(mTime.minute >= 53 && set == 1){
                stopService(new Intent(getApplicationContext(), HBSService.class));
                set = 0;
            }*/


            if(isInAmbientMode()){
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                final PowerManager.WakeLock w3 = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "My Tag");
                w3.acquire();
                if (notificationTime == 0 && !getPeekCardPosition().isEmpty()){
                    notificationTime = tempTime;
                }
                w3.release();
            }

            hbsArray = new float[]{200,200,200,200,200,200,200,200,200,200,200,200};
            List<HBSensor> q = dataSource.findLastMinutesOfHBSemsor(tempTime, 60); //past 5min search
            if(q.size() != 0) {
                long id;
                long time;
                float sdnn;
                long section;
                long activeTime;
                for (HBSensor h : q) {
                    id = h.getId();
                    time = h.getTime();
                    sdnn = h.getSdnn();
                    section = h.getSection();
                    activeTime = h.getActiveTime();
                    if(activeTime>=60000*1.5) //if 3 minutes active then let use hbs data //TODO
                        hbsArray[(int)section] = 300;
                    else{
                        if(drawInterface.equals("HRV"))
                            hbsArray[(int)section] = sdnn;
                    }

                    Log.d("ListHBSensorPastXMinutes", "Id: " + id +" Time: " + time + "  - SDNN: " + sdnn + "  - Section: " + section + "  - ActiveTime: " + activeTime);
                }
            }



            // redraw canvas
            invalidate();

        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
             /* the wearable switched between modes */

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onAmbientModeChanged: " + inAmbientMode);
            }
            if (mLowBitAmbient) {
                boolean antiAlias = !inAmbientMode;
                //mHourPaint.setAntiAlias(antiAlias);
                //mMinutePaint.setAntiAlias(antiAlias);
                //mSecondPaint.setAntiAlias(antiAlias);
                //mTickPaint.setAntiAlias(antiAlias);
            }




            mTime.setToNow();
            long tempTime = mTime.toMillis(false);
            long otherAppsAccess = 0;




            if (!getPeekCardPosition().isEmpty()){
                if((tempTime - 300000) < notificationTime){
                    peekCardNotificationGlobal = 2; // notification received in the past 5min
                }else if(notificationTime == 0){
                    notificationTime = tempTime;
                    peekCardNotificationGlobal = 2; // notification received in the past min
                }else{
                    peekCardNotificationGlobal = 1; // notification received before the past 5min
                }
            }else{
                peekCardNotificationGlobal = 0;
                notificationTime = 0;
            }


            if(!isInAmbientMode()){
                glanceStartTime = tempTime;
                lastTapTime = tempTime;
            }else{

                if(tempTime > (lastTapTime + 11000l)){//if passed more than 6 sec since last tap on smartwatch
                    otherAppsAccess = 1;
                    //Log.d("sql", "w1AcquireTime:" + w1AcquireTime + " lastTapTime:" + lastTapTime);
                }

                if(glanceStartTime!=0){
                    if((tempTime-3600000) > glanceStartTime){ // se dif maior que 1h então meter dif = 10 secs
                        glanceStartTime = tempTime - 10000;
                    }
                    insertRowActions(glanceStartTime, tempTime, peekCardNotificationGlobal, otherAppsAccess);
                    glanceStartTime = 0;
                }
            }






            invalidate(); //call onDraw
            // Whether the timer should be running depends on whether we're in ambient mode (as well
            // as whether we're visible), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);
            boolean inMuteMode = (interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE);
            if (mMute != inMuteMode) {
                mMute = inMuteMode;
                //mHourPaint.setAlpha(inMuteMode ? 100 : 255);
                //mMinutePaint.setAlpha(inMuteMode ? 100 : 255);
                //mSecondPaint.setAlpha(inMuteMode ? 80 : 255);
                invalidate();
            }
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            mTime.setToNow();
            long tempTime = mTime.toMillis(false);


            int width = bounds.width();
            int height = bounds.height();

            // Draw the background, scaled to fit.
            if (mBackgroundScaledBitmap == null
                    || mBackgroundScaledBitmap.getWidth() != width
                    || mBackgroundScaledBitmap.getHeight() != height) {
                mBackgroundScaledBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                        width, height, true /* filter */);
            }
            canvas.drawBitmap(mBackgroundScaledBitmap, 0, 0, null);

            float centerX = width / 2f;
            float centerY = height / 2f;



            if(!menuState){
                Paint paint = new Paint();
                paint.setColor(Color.WHITE);
                paint.setTextSize(40);
                String s1;
                if(isInAmbientMode())
                    s1 = String.format("%02d", mTime.hour) + ":" + String.format("%02d", mTime.minute);
                else
                    s1 = String.format("%02d", mTime.hour) + ":" + String.format("%02d", mTime.minute) + ":" + String.format("%02d", mTime.second);
                float w = paint.measureText(s1) / 2;
                canvas.drawText(s1, centerX - w, centerY, paint);

                paint.setTextSize(20);
                String weekDay = "", month="";
                paint.setColor(Color.WHITE);

                if(mTime.weekDay==1)
                    weekDay = "Monday";
                else if(mTime.weekDay==2)
                    weekDay = "Tuesday";
                else if(mTime.weekDay==3)
                    weekDay = "Wednesday";
                else if(mTime.weekDay==4)
                    weekDay = "Thursday";
                else if(mTime.weekDay==5)
                    weekDay = "Friday";
                else if(mTime.weekDay==6)
                    weekDay = "Saturday";
                else if(mTime.weekDay==7)
                    weekDay = "Sunday";

                if(mTime.month==1)
                    month = "January";
                else if(mTime.month==2)
                    month = "February";
                else if(mTime.month==3)
                    month = "March";
                else if(mTime.month==4)
                    month = "April";
                else if(mTime.month==5)
                    month = "May";
                else if(mTime.month==6)
                    month = "June";
                else if(mTime.month==7)
                    month = "July";
                else if(mTime.month==8)
                    month = "August";
                else if(mTime.month==9)
                    month = "September";
                else if(mTime.month==10)
                    month = "October";
                else if(mTime.month==11)
                    month = "November";
                else if(mTime.monthDay==12)
                    month = "month";

                s1 = weekDay + ", " + mTime.monthDay + " " + month;
                w = paint.measureText(s1) / 2;
                canvas.drawText(s1, centerX - w, centerY + 30, paint);



                if(!drawInterface.equals("CONTROL")){
                    //draw hbs interface HRV
                    float externalPoint3 = centerX - 12;
                    float internalPoint3 = centerX - 40;
                    float pX1, pY1, pX2, pY2;

                    for(int i=0; i<120; i++){
                        double z = i%10;
                        if(z != 0){
                            float lMinRot = i / 60f * (float) Math.PI;
                            pX1 = (float) Math.sin(lMinRot) * externalPoint3;
                            pY1 = (float) -Math.cos(lMinRot) * externalPoint3;
                            pX2 = (float) Math.sin(lMinRot) * internalPoint3;
                            pY2 = (float) -Math.cos(lMinRot) * internalPoint3;


                            if(hbsArray[(i/10)] == 200){
                                hbsPaint.setARGB(255, 255, 255, 255); // white (if no data was inserted on that section of time)
                            }
                            else if(hbsArray[(i/10)] == 300){
                                hbsPaint.setARGB(255, 27, 192, 247); // blue (if more than 2minutes inactive on the section of time)
                            }
                            else if(hbsArray[(i/10)] <= 0.5){
                                hbsPaint.setARGB(255, 247, 10, 10); // red
                            }
                            else if(hbsArray[(i/10)] <= 1){
                                hbsPaint.setARGB(255, 247, 109, 10);
                            }
                            else if(hbsArray[(i/10)] <= 1.5){
                                hbsPaint.setARGB(255, 247, 172, 10);
                            }
                            else if(hbsArray[(i/10)] <= 2){
                                hbsPaint.setARGB(255, 247, 211, 10);
                            }
                            else if(hbsArray[(i/10)] <= 2.5){
                                hbsPaint.setARGB(255, 247, 243, 10);
                            }
                            else if(hbsArray[(i/10)] <= 3){
                                hbsPaint.setARGB(255, 211, 247, 10);
                            }
                            else if(hbsArray[(i/10)] <= 3.5){
                                hbsPaint.setARGB(255, 172, 247, 10);
                            }
                            else if(hbsArray[(i/10)] > 3.5){
                                hbsPaint.setARGB(255, 101, 247, 10);
                            }
                            else{
                                hbsPaint.setARGB(255, 10, 247, 65); // green
                            }

                            canvas.drawLine(centerX + pX2, centerY + pY2, centerX + pX1, centerY + pY1, hbsPaint); //grey paint
                        }
                    }
                }


            }else{
                Paint paint = new Paint();
                Paint paint2 = new Paint();
                float w;
                paint.setTextSize(30);
                paint2.setStrokeWidth(2);
                if(!sendLock){
                    paint2.setColor(Color.WHITE);
                    canvas.drawRect(0,centerY, width, height, paint2);
                    paint.setColor(Color.BLACK);

                    String s1 = "Send Data";
                    w = paint.measureText(s1) / 2;
                    canvas.drawText(s1, centerX - w, centerY + centerY/2 + 10, paint);
                }else{
                    paint.setColor(Color.WHITE);
                    String s1 = "Data Sent";
                    w = paint.measureText(s1) / 2;
                    canvas.drawText(s1, centerX - w, centerY + centerY/2 + 10, paint);
                }

                if(servico){
                    paint2.setColor(Color.GREEN);
                    canvas.drawRect(0,0, width, centerY, paint2);
                    paint2.setStyle(Paint.Style.STROKE);
                    paint2.setColor(Color.BLACK);
                    canvas.drawRect(0,0, width, centerY, paint2);

                    paint.setColor(Color.WHITE);
                    String s1 = "HB Sensor ON";
                    w = paint.measureText(s1) / 2;
                    canvas.drawText(s1, centerX - w, centerY/2, paint);
                }
                else{
                    paint2.setColor(Color.RED);
                    canvas.drawRect(0,0, width, centerY, paint2);
                    paint2.setStyle(Paint.Style.STROKE);
                    paint2.setColor(Color.BLACK);
                    canvas.drawRect(0,0, width, centerY, paint2);

                    paint.setColor(Color.WHITE);
                    String s1 = "HB Sensor OFF";
                    w = paint.measureText(s1) / 2;
                    canvas.drawText(s1, centerX - w, centerY/2, paint);
                }


                paint2.setStyle(Paint.Style.FILL);
                paint2.setColor(Color.WHITE);
                canvas.drawCircle(centerX, centerY, 60, paint2);
                paint2.setStyle(Paint.Style.STROKE);
                paint2.setColor(Color.BLACK);
                canvas.drawCircle(centerX, centerY, 60, paint2);

                paint.setColor(Color.BLACK);
                String s1 = "Exit";
                w = paint.measureText(s1) / 2;
                canvas.drawText(s1, centerX - w, centerY + 10, paint);

            }


        }


            @Override
            public void onVisibilityChanged(boolean visible) {
                super.onVisibilityChanged(visible);
            /* the watch face became visible or invisible */

                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "onVisibilityChanged: " + visible);
                }

                if (visible) {
                    registerReceiver();
                    // Update time zone in case it changed while we weren't visible.
                    mTime.clear(TimeZone.getDefault().getID());
                    mTime.setToNow();
                } else {
                    unregisterReceiver();
                }

                // Whether the timer should be running depends on whether we're visible (as well as
                // whether we're in ambient mode), so we may need to start or stop the timer.
                updateTimer();
            }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            AnalogWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            AnalogWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "updateTimer");
            }
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }


        private long insertHBSensorRow(long t, float sdnn, long section){
            HBSensor hb = new HBSensor();
            hb.setTime(t);
            hb.setSdnn(sdnn);
            hb.setSection(section);
            hb.setSet(0l);
            hb = dataSource.createHBSensor(hb);
            Log.d("InsertHBSensor", "HBSensor Row Created with id " + hb.getId() + " Time: " + t + " SDNN: " + sdnn + " SECTION: " + section);
            return hb.getId();
        }

        public long insertRowActions(long t, long t2, long peekCardNotification, long otherAppsAccess){
            Action action = new Action();
            action.setTime(t);
            action.setTime2(t2);
            action.setSet(0L);
            action.setPeekCardNotification(peekCardNotification);
            action.setAccessOtherApps(otherAppsAccess);
            action = dataSource.createAction(action);
            Date d = new Date(t);
            Date d2 = new Date(t2);
            Log.d("InsertAction", "Action Row Created with id " + action.getId() + " Notification: " + peekCardNotification + "OtherAppsAccess: " + otherAppsAccess + " Time1: " + d+ " Time2: " + d2);
            return action.getId();
        }


        public void listAllRows(String tName){
            if(tName.equals("hbsensor")){
                List<HBSensor> q = dataSource.findAllHBSensor();
                if(q.size() != 0) {
                    long id;
                    long time;
                    float sdnn;
                    long section;
                    for (HBSensor h : q) {
                        id = h.getId();
                        time = h.getTime();
                        sdnn = h.getSdnn();
                        section = h.getSection();
                        Log.d("ListHBSensor", "Id: " + id +" Time: " + time + "  - SDNN: " + sdnn + "  - SECTION: " + section);
                    }
                }else
                    Log.d("ListHBSensor", "table empty");
            }
            else if(tName.equals("activities")){
                List<Activity> q = dataSource.findAllActivities();
                if(q.size() != 0) {
                    long steps;
                    long iniTime;
                    long endTime;
                    long set;
                    long id2;
                    for (Activity a : q) {
                        steps = a.getStep();
                        iniTime = a.getStart();
                        endTime = a.getFinish();
                        set = a.getSet();
                        id2 = a.getId2();
                        Date iniD = new Date(iniTime);
                        Date endD = new Date(endTime);
                        Log.d("ListActivities", "Activity: From " + iniD + " to " + endD + " - Walked: " + steps + " steps  - Set= " + set + " id: " + id2);
                    }
                }else
                    Log.d("ListActivities", "table empty");
            }
        }


    }
}
