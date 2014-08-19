package net.es.lookup.pubsub.amq;

import net.es.lookup.common.Message;
import net.es.lookup.common.QueryNormalizer;
import net.es.lookup.common.exception.internal.PubSubQueryException;
import net.es.lookup.common.exception.internal.PubSubQueueException;
import net.es.lookup.pubsub.QueueManager;
import net.es.lookup.pubsub.QueueServiceMapping;
import org.apache.log4j.Logger;
import org.joda.time.Instant;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
/**
 * This class implements the QueueManager interface. This implementation has a 1 to 1 mapping between query and queue.
 * <p/>
 * Author: sowmya
 * Date: 3/1/13
 * Time: 3:12 PM
 */
public class AMQueueManager implements QueueManager {

    private HashMap<String, AMQueue> queueMap = new HashMap<String, AMQueue>();            /* keeps track of queueid to queue mapping */
    private HashMap<String, List<String>> queryMap = new HashMap<String, List<String>>();   /* keeps track of query to queueid mapping  */
    private HashMap<String, String> queryTimeMap = new HashMap<String, String>();

    private HashMap<String, List<Message>> normalizedQueryMap = new HashMap<String, List<Message>>();   /* FUTURE USE: keeps track of normalized query to original query mapping  */

    private String serviceName;

    private static Logger LOG = Logger.getLogger(AMQueueManager.class);


    public AMQueueManager(String serviceName) {

        this.serviceName = serviceName;
        QueueServiceMapping.addQueueManager(serviceName, this);
    }


    public String getServiceName() {

        return serviceName;
    }


    /**
     * This is the implementation of the getQueues method declared in the QueueManager interface.
     * The method normalizes the query and searches if queue exists for the query. If queue exists,
     * the queue id is returned. Else, a queue is created and the id is returned.
     */
    public List<String> getQueues(Message query) throws PubSubQueryException {

        LOG.info("net.es.lookup.pubsub.amq.AMQueueManager.getQueues: Creating/Retrieving Queues");
        List<String> res = new ArrayList<String>();
        String normalizedQuery = "";
        LOG.info("net.es.lookup.pubsub.amq.AMQueueManager.getQueues: Calling Normalize query - ");
        normalizedQuery = QueryNormalizer.normalize(query);
        LOG.info("net.es.lookup.pubsub.amq.AMQueueManager.getQueues: Normalized query - "+ normalizedQuery);
        if (!normalizedQuery.isEmpty()) {

            if (queryMap.containsKey(normalizedQuery)) {

                res = queryMap.get(normalizedQuery);
                LOG.debug("net.es.lookup.pubsub.amq.AMQueueManager.getQueues: Queue exists. ");

            } else {

                //System.out.println(queryMap.toString());
                AMQueue queue = null;
                try {
                    MessageDigest md = MessageDigest.getInstance("MD5");
            	    byte[] querybyte = normalizedQuery.getBytes();
            	    byte[] queueId = md.digest(querybyte);
                   String qid = queueId.toString();
                    queue = new AMQueue(qid);

                    LOG.debug("net.es.lookup.pubsub.amq.AMQueueManager.getQueues: Created queue with id " + qid);


                    //add queue to queueMap
                    queueMap.put(qid, queue);

                    //add to queryMap
                    res.add(qid);
                    queryMap.put(normalizedQuery, res);
                    Instant now = new Instant();
                    DateTimeFormatter dateTimeFormatter = ISODateTimeFormat.dateTime();
                    String timestamp = dateTimeFormatter.print(now);
                    queryTimeMap.put(normalizedQuery, timestamp);
                    //add to normalized query
                    List<Message> queryList = new ArrayList<Message>();
                    queryList.add(query);
                    normalizedQueryMap.put(normalizedQuery, queryList);
                } catch (PubSubQueueException e) {
                    LOG.error("net.es.lookup.pubsub.amq.AMQueueManager.getQueues: Error creating queue " + e.getMessage());
                }catch(NoSuchAlgorithmException e){
                    LOG.error("net.es.lookup.pubsub.amq.AMQueueManager.getQueues: Error creating queue " + e.getMessage());
                }

            }

        }
        LOG.debug("net.es.lookup.pubsub.amq.AMQueueManager.getQueues: Returning - " + res);
        return res;

    }

    /**
     * This method is the implementation of the hasQueues method declared by QueueManager interface.
     * This method simply returns true if queue exists for a query and false if not .
     */
    public boolean hasQueues(Message query) throws PubSubQueryException, PubSubQueueException {

        String normalizedQuery = QueryNormalizer.normalize(query);
        if (queryMap.containsKey(normalizedQuery)) {
            return true;
        } else {
            return false;
        }
    }


    /**
     * This method is the implementation of the push method declared by QueueManager interface.
     * This method simply checks if queue exists and pushes the message to the queue. If queue does
     * not exist, it throws a PubSubQueueException.
     */
    public void push(String qid, List<Message> messages){

        LOG.info("net.es.lookup.pubsub.amq.AMQueueManager.push: Pushing Message" + messages + " to Queue " + qid);
        AMQueue queue = queueMap.get(qid);

        if (queue != null) {
            try {
                queue.push(messages);
            } catch (PubSubQueueException e) {
                LOG.error("net.es.lookup.pubsub.amq.AMQueueManager.push: Error pushing message to Queue. Queue does not exist");
                cleanUp(qid);
            }
            LOG.debug("net.es.lookup.pubsub.amq.AMQueueManager.push: Pushed Message" + messages + " to Queue " + qid);
        } else {
            LOG.error("net.es.lookup.pubsub.amq.AMQueueManager.push: Error pushing message to Queue. Queue does not exist");
        }

    }

    public List<Message> getAllQueries() {
        LOG.debug("net.es.lookup.pubsub.amq.AMQueueManager.getAllQueries: Retrieving all queries handled by Subscribe");
        List<Message> queryList = new ArrayList<Message>();
        for (String q : queryMap.keySet()) {
            queryList.addAll(normalizedQueryMap.get(q));
        }
        LOG.debug("net.es.lookup.pubsub.amq.AMQueueManager.getAllQueries: Retrieved Queries - "+ queryList.size());
        return queryList;
    }


    public String getQueueCreationTime(Message query) throws PubSubQueryException {
        String nQuery = QueryNormalizer.normalize(query);
        return queryTimeMap.get(nQuery);
    }

    private void cleanUp(String qid){
        String query=null;
        for(String s: queryMap.keySet()){
            List<String> qids = queryMap.get(s);
            for(String q: qids){
                if(q.equals(qid)){
                    query = s;
                }
            }
        }
        if(query != null){
            queryMap.remove(query);
        }

        queueMap.remove(qid);

    }

}
