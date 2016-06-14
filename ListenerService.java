package uma.hbs;

import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.List;

import uma.hbs.db.TableDataSource;
import uma.hbs.model.Action;
import uma.hbs.model.Activity;
import uma.hbs.model.HBSensor;

/**
 * Created by Administrator on 17/02/2016.
 */
public class ListenerService extends WearableListenerService {

    String nodeId;
    TableDataSource dataSource;
    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        nodeId = messageEvent.getSourceNodeId();
        Log.d("REPLY", "nodeid: " + nodeId + " - message: " + messageEvent.getPath());

        dataSource = new TableDataSource(getApplicationContext());
        dataSource.open();

        String[] separated = messageEvent.getPath().split("-");

        int L1 = separated.length;
        Long[] ids = new Long[L1];
        for (int i = 0; i < L1; i++) {
            ids[i] = Long.parseLong(separated[i]);
        }

        List<HBSensor> q = dataSource.findAllHBSSetBellowId(0l, ids[0]);
        int sizeHBSGlobal = q.size();
        if(sizeHBSGlobal != 0) {
            for (HBSensor a : q) {
                dataSource.updateHBS(a.getId());
                Log.d("REPLY", "HBS UPDATED with id:" + a.getId());
            }
        }

        List<Activity> q1 = dataSource.findAllActivitiesSetBellowId(0l, ids[1]);
        int sizeActivityGlobal = q1.size();
        if(sizeActivityGlobal != 0) {
            for (Activity a2 : q1) {
                dataSource.updateActtivity(a2.getId2(), 1l);
                //Log.d("TESTE", "ACTIVITY UPDATED with id:" + a2.getId2());
            }
        }

        List<Action> q2 = dataSource.findAllActionsSetBellowId(0l, ids[2]);
        int sizeActionGlobal = q2.size();
        if(sizeActionGlobal != 0) {
            for (Action a : q2) {
                dataSource.updateAction(a.getId());
                Log.d("REPLY", "ACTION UPDATED with id:" + a.getId());
            }
        }

    }
}
