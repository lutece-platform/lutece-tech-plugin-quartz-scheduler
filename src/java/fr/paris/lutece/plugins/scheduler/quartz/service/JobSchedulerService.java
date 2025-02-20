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
package fr.paris.lutece.plugins.scheduler.quartz.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.Trigger;
import org.quartz.impl.StdSchedulerFactory;

import fr.paris.lutece.plugins.scheduler.quartz.Constants;
import fr.paris.lutece.plugins.scheduler.quartz.job.DaemonEntryJobListener;
import fr.paris.lutece.plugins.scheduler.quartz.job.LuteceJobFactory;
import fr.paris.lutece.portal.service.init.WebConfResourceLocator;
import io.github.classgraph.ResourceList;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Destroyed;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import jakarta.servlet.ServletContext;

/**
 * JobSchedulerService. Manages the creation and lifecycle of the two Quartz scheduler (local and clustered) based on the plugin configuration.
 */
@ApplicationScoped
public class JobSchedulerService
{
    private static final String LOCAL_SCHEDULER_PROPERTIES_FILENAME = "**quartz-local.properties";
    private static final String CLUSTERED_SCHEDULER_PROPERTIES_FILENAME = "**quartz-cluster.properties";

    private Logger _logger = LogManager.getLogger( "lutece.scheduler.quartz" );
    @ConfigProperty( name = "quartzscheduler.cluster.enable", defaultValue = "false" )
    @Inject
    private boolean _clusterEnabled;
    private Scheduler _localScheduler;
    private Scheduler _clusteredScheduler;

    JobSchedulerService( )
    {
        // Ctor
    }

    @PostConstruct
    void initJobSchedulerService( )
    {
        try
        {
            Properties localProperties = loadProperties( LOCAL_SCHEDULER_PROPERTIES_FILENAME );
            SchedulerFactory factory = new StdSchedulerFactory( localProperties );
            _localScheduler = factory.getScheduler( );
            _localScheduler.setJobFactory( new LuteceJobFactory( ) );
            _localScheduler.getListenerManager( ).addJobListener( new DaemonEntryJobListener( ) );
            _localScheduler.start( );
            _logger.info( "Lutece local job scheduler started." );
        }
        catch( SchedulerException e )
        {
            _logger.error( "Error starting the Lutece local job scheduler ", e );
        }
        try
        {
            if ( _clusterEnabled )
            {
                Properties clusterProperties = loadProperties( CLUSTERED_SCHEDULER_PROPERTIES_FILENAME );
                SchedulerFactory clusteredFactory = new StdSchedulerFactory( clusterProperties );
                _clusteredScheduler = clusteredFactory.getScheduler( );
                _clusteredScheduler.setJobFactory( new LuteceJobFactory( ) );
                _clusteredScheduler.getListenerManager( ).addJobListener( new DaemonEntryJobListener( ) );
                _clusteredScheduler.start( );
                _logger.info( "Lutece clustered job scheduler started." );
            }
        }
        catch( SchedulerException e )
        {
            _logger.error( "Error starting the Lutece clustered job scheduler ", e );
        }
    }

    private Properties loadProperties( String strSchedulerFileName )
    {
        Properties properties = null;
        ResourceList resourceList = WebConfResourceLocator.getResourcesMatchingWildcard( strSchedulerFileName );
        if ( 1 == resourceList.size( ) )
        {
            try
            {
                InputStream is = Thread.currentThread( ).getContextClassLoader( ).getResourceAsStream( resourceList.get( 0 ).getPath( ) );
                properties = new Properties( );
                properties.load( is );
            }
            catch( IOException e )
            {
                _logger.error( "Error loading scheduler properties file", e );
            }
        }
        return properties;
    }

    /**
     * Returns the unique instance of the {@link JobSchedulerService} service.
     * 
     * <p>
     * This method is deprecated and is provided for backward compatibility only.
     * For new code, use dependency injection with {@code @Inject} to obtain the
     * {@link JobSchedulerService} instance instead.
     * </p>
     * 
     * @return The unique instance of {@link JobSchedulerService}.
     * 
     * @deprecated Use {@code @Inject} to obtain the {@link JobSchedulerService}
     *             instance. This method will be removed in future versions.
     */
    @Deprecated( since = "8.0", forRemoval = true )
    public static JobSchedulerService getInstance( )
    {
        return CDI.current( ).select( JobSchedulerService.class ).get( );
    }

    /**
     * Schedule a job according cron information
     * 
     * @param job
     *            The Job to schedule
     * @param trigger
     *            The Cron trigger
     * @return Date
     */
    public Date scheduleJob( JobDetail job, Trigger trigger )
    {
        Date date = null;
        boolean clustered = Boolean.parseBoolean( (String) job.getJobDataMap( ).get( Constants.DAEMON_CLUSTERED_JOB_MAP_KEY ) );
        
        if ( _clusterEnabled && clustered && _clusteredScheduler != null )
        {
            try
            {
                if ( !_clusteredScheduler.checkExists( job.getKey( ) ) )
                {
                    date = _clusteredScheduler.scheduleJob( job, trigger );
                    _logger.info( "New clustered job scheduled : {}", job.getKey( ).getName( ) );
                }
            }
            catch( SchedulerException e )
            {
                _logger.error( "Error scheduling clustered job {}", job.getKey( ).getName( ), e );
            }
        }
        else
        {
            if ( _localScheduler != null )
            {
                try
                {
                    date = _localScheduler.scheduleJob( job, trigger );
                    _logger.info( "New local job scheduled : {}", job.getKey( ).getName( ) );
                }
                catch( SchedulerException e )
                {
                    _logger.error( "Error scheduling local job {}", job.getKey( ).getName( ), e );
                }
            }
        }
        return date;
    }

    public void unscheduleJob( String jobId )
    {
        try
        {
            if ( _localScheduler != null )
            {
                _localScheduler.deleteJob( new JobKey( jobId, Constants.DEFAULT_GROUP ) );
            }
            if ( _clusteredScheduler != null )
            {
                _clusteredScheduler.deleteJob( new JobKey( jobId, Constants.DEFAULT_GROUP ) );
            }
        }
        catch( SchedulerException e )
        {
            _logger.error( "Error unscheduling job ", e );
        }
    }

    public Date executeJob( JobKey jobKey )
    {
        Date date = null;
        try
        {
            if ( _localScheduler != null )
            {
                JobDetail jobDetail = _localScheduler.getJobDetail( jobKey );
                if ( null != jobDetail )
                {
                    _localScheduler.triggerJob( jobKey );
                    date = new Date();
                }
            }
            if ( _clusteredScheduler != null && null == date )
            {
                JobDetail jobDetail = _clusteredScheduler.getJobDetail( jobKey );
                if ( null != jobDetail )
                {
                    _clusteredScheduler.triggerJob( jobKey );
                    date = new Date();
                }
            }
        }
        catch( SchedulerException e )
        {
            _logger.error( "Error unscheduling job ", e );
        }
        return date;
    }

    void contextDestroyed( @Observes @Priority( value = 2 ) @Destroyed( ApplicationScoped.class ) ServletContext context )
    {
        _logger.info( "JobSchedulerService is shuting down" );

        try
        {
            if ( _localScheduler != null )
            {
                _localScheduler.shutdown( );
                _logger.info( "Lutece local job scheduler stopped." );
            }
            if ( _clusteredScheduler != null )
            {
                _clusteredScheduler.shutdown( );
                _logger.info( "Lutece clustered job scheduler stopped." );
            }
        }
        catch( SchedulerException e )
        {
            _logger.error( "Error shuting down the Lutece job scheduler ", e );
        }
    }

    /**
     * Shutdown the service (Called by the core while the webapp is destroyed)
     * 
     * <p>
     * This method is deprecated and is provided for backward compatibility only.
     * For new code, use dependency injection with {@code @Inject} to obtain the
     * {@link JobSchedulerService} instance instead.
     * </p>
     * 
     * @deprecated Use {@code @Inject} to obtain the {@link JobSchedulerService}
     *             instance. This method will be removed in future versions.
     */
    @Deprecated( since = "8.0", forRemoval = true )
    public static void shutdown( )
    {
        CDI.current( ).select( JobSchedulerService.class ).get( ).contextDestroyed( null );
    }

}
