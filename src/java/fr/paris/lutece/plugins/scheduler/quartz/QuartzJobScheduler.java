/*
 * Copyright (c) 2002-2025, City of Paris
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice
 *     and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice
 *     and the following disclaimer in the documentation and/or other materials
 *     provided with the distribution.
 *
 *  3. Neither the name of 'Mairie de Paris' nor 'Lutece' nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * License 1.0
 */
package fr.paris.lutece.plugins.scheduler.quartz;

import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobKey;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.JobDetailImpl;

import fr.paris.lutece.plugins.scheduler.quartz.job.DaemonJob;
import fr.paris.lutece.plugins.scheduler.quartz.service.JobSchedulerService;
import fr.paris.lutece.portal.service.daemon.DaemonEntry;
import fr.paris.lutece.portal.service.daemon.IDaemonScheduler;
import fr.paris.lutece.portal.service.util.AppPropertiesService;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

/**
 * Implementation of the IDaemonScheduler with Quartz scheduler.
 */
@ApplicationScoped
@Alternative
@Priority( 10 )
public class QuartzJobScheduler implements IDaemonScheduler
{
    private static final String TRIGGER_NAME_SUFFIX = "_trigger";
    private static final String CLUSTER_DIS_CONCURRENCY_PREFIX = "quartzscheduler.daemon.";
    private static final String CLUSTER_DIS_CONCURRENCY_SUFFIX = ".disallowedClusterConcurrentExecution";
    private static final String CRON_VALIDATION_TRIGGER_NAME = "dummy_for_validation";

    private Logger _logger = LogManager.getLogger( "lutece.scheduler.quartz" );
    @Inject
    private JobSchedulerService _jobSchedulerService;

    @Override
    public boolean enqueue( DaemonEntry entry, long nDelay, TimeUnit unit )
    {
        _jobSchedulerService.executeJob( new JobKey( entry.getId( ), Constants.DEFAULT_GROUP ) );
        return false;
    }

    @Override
    public void schedule( DaemonEntry entry, long nInitialDelay, TimeUnit unit )
    {
        JobDetailImpl jdi = new JobDetailImpl( );
        jdi.setJobClass( DaemonJob.class );
        jdi.getJobDataMap( ).put( Constants.DAEMON_ENTRY_ID_JOB_MAP_KEY, entry.getId( ) );
        jdi.getJobDataMap( ).put( Constants.DAEMON_CLUSTERED_JOB_MAP_KEY,
                AppPropertiesService.getProperty( CLUSTER_DIS_CONCURRENCY_PREFIX + entry.getId( ) + CLUSTER_DIS_CONCURRENCY_SUFFIX ) );
        jdi.setKey( new JobKey( entry.getId( ), Constants.DEFAULT_GROUP ) );

        // Check cron expression
        if ( null != entry.getCron( ) && !"".equals( entry.getCron( ) ) )
        {
            CronTrigger ct = TriggerBuilder.newTrigger( ).withIdentity( entry.getId( ) + TRIGGER_NAME_SUFFIX, Constants.DEFAULT_GROUP )
                    .withSchedule( CronScheduleBuilder.cronSchedule( entry.getCron( ) ) )
                    .build( );
            _jobSchedulerService.scheduleJob( jdi, ct );
        }
        else
        {
            Trigger t = TriggerBuilder.newTrigger( ).withIdentity( entry.getId( ) + TRIGGER_NAME_SUFFIX, Constants.DEFAULT_GROUP )
                    .withSchedule(
                            SimpleScheduleBuilder.simpleSchedule( ).withIntervalInSeconds( Math.toIntExact( entry.getInterval( ) ) ).repeatForever( ) )
                    .build( );
            _jobSchedulerService.scheduleJob( jdi, t );
        }
    }

    @Override
    public void unSchedule( DaemonEntry daemonEntry )
    {
        _jobSchedulerService.unscheduleJob( daemonEntry.getId( ) );
    }

    @Override
    public void shutdown( )
    {
        // Nothing to do. Managed by the JobSchedulerService.
    }

    @Override
    public boolean isValidCronExpression( String strDaemonCron )
    {
        boolean valid = false;
        if ( null != strDaemonCron && !"".equals( strDaemonCron ) )
        {
            try
            {
                TriggerBuilder.newTrigger( ).withIdentity( CRON_VALIDATION_TRIGGER_NAME, CRON_VALIDATION_TRIGGER_NAME )
                        .withSchedule( CronScheduleBuilder.cronSchedule( strDaemonCron ) )
                        .build( );
                valid = true;
            }
            catch( Exception e )
            {
                _logger.debug( "Invalid cron expression '{}'", strDaemonCron, e );
            }
        }
        return valid;
    }

}
