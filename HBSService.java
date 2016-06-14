package uma.hbs;


import android.app.IntentService;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Vibrator;
import android.text.format.Time;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import uma.hbs.db.TableDataSource;
import uma.hbs.model.Activity;
import uma.hbs.model.HBSensor;

/**
 * Created by Administrator on 4/02/2016.
 */
public class HBSService extends Service implements SensorEventListener{


    private List<Integer> hbsList = new ArrayList<>();
    private List<Integer> hbsListFinal = new ArrayList<>();
    SensorManager mSensorManager;
    Sensor mHeartRateSensor;
    Sensor mStepCountSensor;
    Time mTime;
    long lastSensorSecond, lastSensorSecond2 = -1;
    PowerManager.WakeLock wl;
    int lastMinSection = -1;
    double zFactor = 2.0;

    TableDataSource dataSource;

    int  iniStepCount = 0, endStepCount = 0, lastStepCount = 0, lastKnownSteps = 0, sensorStep = 0, firstSteps = 0;
    long  iniActTime = 0, lastActTime = 0, endActTime = 0, sensorTime = 0;
    long  id2Global = 0, it = 0, StepNumberTeste = 0, sensorTest = 0, myTimeReference = 0, sensorTimeReference = 0;;
    long todayStepsInserted = 0, stepsGlobal= 0, finishGlobal = 0, startGlobal = 0,lastMinStepCount=0;
    boolean firstReadSensor = false, firstTimeSensorAfterReset = false, canInsertRow =false;

    /*public HBSService() {
        // Used to name the worker thread, important only for debugging.
        super("hbs-service");
    }*/

    /*@Override
    protected void onHandleIntent(Intent intent) {*/
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {

        dataSource = new TableDataSource(getApplicationContext());
        dataSource.open();
        mTime = new Time();
        /*Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        long[] vibrationPattern = {0, 500, 50, 300};
        //-1 - don't repeat
        final int indexInPatternToRepeat = -1;
        vibrator.vibrate(vibrationPattern, indexInPatternToRepeat);*/



        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "My Tag");
        wl.acquire();
        getSensors();
    }

    private void getSensors() {
        mSensorManager = ((SensorManager)getSystemService(SENSOR_SERVICE));
        mStepCountSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        mHeartRateSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        mSensorManager.registerListener(this, mHeartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mStepCountSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d("sensor", "onAccuracyChanged - accuracy: " + accuracy);
    }

    public void onSensorChanged(SensorEvent event) {
        mTime.setToNow();
        long currentSecond = mTime.second;
        long currentTime = mTime.toMillis(false);
        if(event.sensor.getType() == Sensor.TYPE_STEP_COUNTER){
            Log.d("sensor", "Sensor step counter");
        }
        if (event.sensor.getType() == Sensor.TYPE_HEART_RATE) {

            if(currentSecond != lastSensorSecond){
                int hbsVal = (int)event.values[0];


                    //see what section is atm
                    int currentMinSection = (int) (mTime.minute/5); //TODO meter 5
                    if(lastMinSection == -1){
                        Log.d("section", "1st TIME IN SENSOR, SECTION - " + lastMinSection);
                        lastMinSection = currentMinSection;
                    }
                    else if(lastMinSection == currentMinSection){
                        Log.d("section", "CURRENT SECTION - " + lastMinSection);
                    }else{

                        //calculate stuff with the list untoched
                        //Log.d("section", "sum1 = " +  getSum(hbsList));
                        double avg = getAverage(hbsList);
                        //Log.d("section", "average1 = " + avg);
                        double stdDev = getStandardDeviation(hbsList);
                        Log.d("section", "deviation1 = " + stdDev);

                        //create new list
                        for (int i = 0; i < hbsList.size(); i++) {
                            if (Math.abs(hbsList.get(i)-avg) < (zFactor * stdDev)){
                                hbsListFinal.add(hbsList.get(i));
                            }
                        }

                        /*for (int i = 0; i<hbsListFinal.size(); i++){
                            Log.i("sectionNEWLIST: ", "Value " +  i + ": " +  hbsListFinal.get(i));
                        }*/

                        double stdDev2 = getStandardDeviation(hbsListFinal);
                        Log.d("section", "deviation2 = " + stdDev2); // TODO change colour based on this value


                        int lastminute = mTime.minute - 5; // had -1
                        if(lastminute < 0)
                            lastminute = 59;
                        long section = (long)(lastminute/5);
                        Log.d("section", "time = " + (currentTime - 300000L) + "deviation = " + (float) stdDev2 + "section = " + section);


                        long sectionEnd = (long)(mTime.minute/5);
                        Calendar cal = Calendar.getInstance();
                        cal.setTimeInMillis(currentTime);
                        cal.set(Calendar.MINUTE, (((int)sectionEnd))*5);
                        cal.set(Calendar.SECOND, 0);
                        cal.set(Calendar.MILLISECOND, 0);
                        sectionEnd = cal.getTimeInMillis();


                        long sectionStart = sectionEnd - (5*60000);


                        long totalTimeActive = 0;
                        totalTimeActive = dataSource.findAllActivitiesSection(sectionStart, sectionEnd, currentTime);//currentTime
                        Log.d("teste", "TOTALTIMEACTIVE - " + totalTimeActive);

                        insertHBSensorRow(currentTime - 300000L, (float) stdDev2, section, totalTimeActive);

                        // clean list and new section start
                        hbsList.clear();
                        lastMinSection = currentMinSection;
                        Log.d("section", "START NEW SECTION - " + lastMinSection);
                    }
                    //Log.d("sensor", "hb sensor: " + (int)event.values[0] + "at" + currentSecond);
                    Log.d("section", "SENSOR VALUE: " + (int)event.values[0] + " AT " + mTime.hour + ":" + mTime.minute + ":" + mTime.second);
                    if(hbsVal >= 60 && hbsVal<=120){ // drop values too small or big /**/
                        hbsList.add(hbsVal);
                    }
                    Log.d("section", "Array list size is: " + hbsList.size());
                lastSensorSecond = currentSecond;
            }
        }
        else if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            myTimeReference = System.currentTimeMillis();
            // set event timestamp to current time in milliseconds
            event.timestamp = myTimeReference +
                    Math.round((event.timestamp - sensorTimeReference) / 1000000.0);
            sensorTime = event.timestamp;

            sensorTime = currentTime;

            StepNumberTeste = (int)event.values[0];
            //Log.d("tick", "steps: " + StepNumberTeste);

            sensorTest++;
            int stepsValue = (int) event.values[0];

            //Log.d("teste3", "stepsValue: " + stepsValue + " lastKnownSteps:" + lastKnownSteps);



            if (firstReadSensor) {// if sensor was initialized already and we have the initial values set
                if(firstSteps != stepsValue){
                    if(lastKnownSteps != stepsValue) {
                        if (iniActTime == 0 && iniStepCount == 0) {
                            it = iniActTime = lastActTime = endActTime = sensorTime;
                            iniStepCount = firstSteps;
                            lastStepCount = endStepCount = sensorStep = lastKnownSteps = stepsValue;
                            canInsertRow = true;
                            //Log.d("teste", "IF - iniT-lastT-endT: " + iniActTime + "/" + lastActTime + "/" + endActTime);
                            //Log.d("teste", "IF - iniS-lastS-endS: " + iniStepCount + "/" + lastStepCount + "/" + endStepCount);
                            firstTimeSensorAfterReset = true;
                            id2Global = insertRowActivity(iniActTime, iniStepCount, endActTime, endStepCount, 1L);
                        } else {//if there was increment on step count and activity was already initialized

                            if(sensorTime > (lastActTime + (long) 59000)){
                                //canInsertRow = false;
                                //firstTimeSensorAfterReset = true;
                                //insert activity row on wear db
                                dataSource.updateActtivity(id2Global, 0L);
                                //Log.d("teste3", "CALL INSERTROWACTIVITY on Draw" + iniActTime + "/" + endActTime + "/ steps: " + endStepCount + "-" + iniStepCount);
                                //change set to 0

                                it = iniActTime = lastActTime = endActTime = sensorTime;
                                iniStepCount = endStepCount;
                                lastStepCount = endStepCount = sensorStep = lastKnownSteps = stepsValue;
                                canInsertRow = true;
                                //Log.d("teste3", "IF - iniT-lastT-endT: " + iniActTime + "/" + lastActTime + "/" + endActTime);
                                //Log.d("teste3", "IF - iniS-lastS-endS: " + iniStepCount + "/" + lastStepCount + "/" + endStepCount);
                                firstTimeSensorAfterReset = true;
                                id2Global = insertRowActivity(iniActTime, iniStepCount, endActTime, endStepCount, 1L);
                            }else{
                                if(currentSecond != lastSensorSecond2){
                                lastActTime = endActTime = sensorTime;
                                lastStepCount = endStepCount = lastKnownSteps = stepsValue;
                                //Log.d("teste", "ELSE - iniT-lastT-endT: " + iniActTime + "/" + lastActTime + "/" + endActTime);
                                //Log.d("teste", "ELSE - iniS-lastS-endS: " + iniStepCount + "/" + lastStepCount + "/" + endStepCount);
                                firstTimeSensorAfterReset = true;
                                dataSource.updateActtivity(id2Global, endStepCount-iniStepCount, iniActTime, endActTime, 1L);
                                }
                            }



                        }
                    }
                }else{//case none activity was started and there was no increment of step count
                    Log.d("teste", "firststeps equal current sensor steps");
                }
            } else {//get initial values when the sensor is initialized
                firstReadSensor = true;
                Log.d("teste", "--------------------------1st Time on Sensor-------------------------------");
                firstSteps = lastKnownSteps = (int) event.values[0];
                lastMinStepCount = todayStepsInserted = StepNumberTeste;
            }
            lastSensorSecond2 = currentSecond;
        }
        else{
            Log.d("sensor", "Unknown sensor type");
        }
    }

    @Override
    public void onDestroy() {
        Log.d("sensor", "destroy");
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onStart(Intent intent, int startid) {
        Log.d("sensor", "start");
    }
    //---------------------------------------------sensors methods close

    //----------------------------------------------math funstions needed
    public double getAverage(List<Integer> lista) {
        double sum = getSum(lista);
        double count = lista.size();
        double avg = sum / count;
        return avg;
    }

    public double getSum(List<Integer> lista) {
        double sum = 0d;
        for (double d : lista) {
            sum += d;
        }
        return sum;
    }

    public double getStandardDeviation(List<Integer> lista) {
        double sum = getSum(lista);
        double avg = getAverage(lista);
        double calc1 = 0;
        double calc2 = 0;
        double count = lista.size();
        double stdDeviation = 0;
        for (int i = 0; i < count; i++) {
            calc1 = lista.get(i) - avg;
            calc1 = Math.pow(calc1, 2);
            calc2 = calc2 + calc1;
        }
        calc2 = calc2 / (count-1);
        stdDeviation = Math.sqrt(calc2);
        return stdDeviation;
    }


    private long insertHBSensorRow(long t, float sdnn, long section, long totalTimeActive){
        HBSensor hb = new HBSensor();
        hb.setTime(t);
        hb.setSdnn(sdnn);
        hb.setSection(section);
        hb.setActiveTime(totalTimeActive);
        hb.setSet(0l);
        hb = dataSource.createHBSensor(hb);
        Log.d("InsertHBSensor2", "HBSensor Row Created with id " + hb.getId() + " Time: " + t + " SDNN: " + sdnn + " SECTION: " + section);
        return hb.getId();
    }

    public long insertRowActivity(long iniT, int iniS, long endT, int endS, long set){
        Activity a = new Activity();
        long s = endS - iniS;
        a.setStep(s);
        a.setStart(iniT);
        a.setFinish(endT);
        a.setSet(set);
        a = dataSource.createActivity(a);
        Date d1 = new Date(iniT);
        Date d2 = new Date(endT);
        Log.d("teste", "Activity Row Created with id " + a.getId2() + " InitialTime: " + d1 + " - EndTime: " + d2 + " - Steps: " + s);
        return a.getId2();
    }

    public void listAllRows(String tName){
        if(tName.equals("hbsensor")){
            List<HBSensor> q = dataSource.findAllHBSensor();
            if(q.size() != 0) {
                long id;
                long time;
                float sdnn;
                long section;
                long totalTimeActive;
                for (HBSensor h : q) {
                    id = h.getId();
                    time = h.getTime();
                    sdnn = h.getSdnn();
                    totalTimeActive = h.getActiveTime();
                    section = h.getSection();
                    Log.d("ListHBSensor", "Id: " + id +" Time: " + time + "  - SDNN: " + sdnn + "  - SDNN: " + totalTimeActive + "  - SECTION: " + section);
                }
            }else
                Log.d("ListHBSensor2", "table empty");
        }
    }





}
