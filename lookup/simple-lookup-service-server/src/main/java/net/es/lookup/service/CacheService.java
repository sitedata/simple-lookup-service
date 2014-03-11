package net.es.lookup.service;

import net.es.lookup.common.exception.LSClientException;
import net.es.lookup.database.MongoDBMaintenanceJob;
import net.es.lookup.pubsub.client.Cache;
import java.util.List;

import net.es.lookup.pubsub.client.failover.FailureRecovery;
import org.apache.log4j.Logger;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

/**
 * Author: sowmya
 * Date: 3/5/14
 * Time: 6:17 PM
 */
public class CacheService {

    private List<Cache> cacheList;
    private static CacheService instance = null;
    private static Logger LOG = Logger.getLogger(CacheService.class);
    private static boolean initialized = false;
    private Scheduler scheduler;
    private static final int FAILURE_RECOVERY_INTERVAL = 120;

    private CacheService(List<Cache> caches, Scheduler scheduler) throws LSClientException {

        this.cacheList = caches;
        initialized=true;
        this.scheduler = scheduler;
        LOG.debug("net.es.lookup.service.CacheService: Number of caches - "+ cacheList.size());
    }

    public static synchronized CacheService getInstance() {

        return instance;

    }

    public static synchronized CacheService initialize(List<Cache> cacheList, Scheduler scheduler) throws LSClientException {

        if (instance != null) {
            throw new RuntimeException("Attempt to create second instance");
        } else {
            instance = new CacheService(cacheList, scheduler);
        }
        return instance;
    }

    public boolean isInitialized(){
        return initialized;
    }

    public void startService() {
        LOG.debug("net.es.lookup.service.CacheService: starting cache");
        for (Cache cache : cacheList) {
            try {
                FailureRecovery failureRecovery = cache.getFailureRecovery();
                JobDetail job = newJob(FailureRecovery.class)
                        .withIdentity(cache.getName() + "-failure-recovery", "FailureRecovery")
                        .build();

                // Trigger the job to run now, and then every dbpruneInterval seconds
                Trigger trigger = newTrigger().withIdentity(cache.getName() + "-failure-recovery-trigger", "DBMaintenance")
                        .startNow()
                        .withSchedule(simpleSchedule()
                                .withIntervalInSeconds(FAILURE_RECOVERY_INTERVAL)
                                .repeatForever()
                                .withMisfireHandlingInstructionIgnoreMisfires())
                        .build();

                this.scheduler.scheduleJob(job, trigger);
                cache.start();
            } catch (LSClientException e) {
                LOG.error("net.es.lookup.service.CacheService: Error starting cache- " + cache.getName());
            } catch (SchedulerException e) {
                LOG.error("net.es.lookup.service.CacheService: Cannot start failure recovery for: " + cache.getName());
            }
        }
    }

    public void stopService() {

        for (Cache cache : cacheList) {
            try {
                cache.stop();
            } catch (LSClientException e) {
                LOG.error("net.es.lookup.service.CacheService: Error starting cache- " + cache.getName());
            }
        }
    }


}