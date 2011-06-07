package slim3.demo.service;

import java.util.List;

import javax.jdo.PersistenceManager;

import org.slim3.datastore.Datastore;

import slim3.demo.cool.jdo.PMF;
import slim3.demo.cool.model.BarJDO;
import slim3.demo.model.Bar;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;

public class PerformanceService {

    public List<Entity> getBarListUsingLL() {
        DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
        Query q = new Query("Bar");
        PreparedQuery pq = ds.prepare(q);
        List<Entity> list =
            pq.asList(FetchOptions.Builder.withOffset(0).limit(
                Integer.MAX_VALUE));
        list.size();
        return list;
    }

    public List<Bar> getBarListUsingSlim3() {
        return Datastore.query(Bar.class).asList();
    }

    @SuppressWarnings("unchecked")
    public List<BarJDO> getBarListUsingJDO() {
        List<BarJDO> list = null;
        PersistenceManager pm = PMF.get().getPersistenceManager();
        try {
            javax.jdo.Query q = pm.newQuery(BarJDO.class);
            list = (List<BarJDO>) q.execute();
            list = (List<BarJDO>) pm.detachCopyAll(list);
        } finally {
            pm.close();
        }
        return list;
    }
}