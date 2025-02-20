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
package fr.paris.lutece.plugins.scheduler.quartz.job;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.quartz.Job;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.spi.JobFactory;
import org.quartz.spi.TriggerFiredBundle;

import fr.paris.lutece.plugins.scheduler.quartz.Constants;
import fr.paris.lutece.portal.service.daemon.AppDaemonService;
import fr.paris.lutece.portal.service.daemon.Daemon;
import jakarta.enterprise.inject.spi.CDI;

/**
 * Quartz job factory that manages the creation of Quartz jobs through a native Quartz job, a native Quartz job as CDI bean or a Lutece Daemon that will be
 * wrapped in a DaemonJob.
 */
public class LuteceJobFactory implements JobFactory
{

    private Logger _logger = LogManager.getLogger( "lutece.scheduler.quartz" );

    @Override
    public Job newJob( TriggerFiredBundle bundle, Scheduler scheduler ) throws SchedulerException
    {
        try
        {
            return buildJob( getJobInstance( bundle ) );
        }
        catch( Exception e )
        {
            _logger.error( "Job instantiation failed ", e );
            throw new SchedulerException( "Job instantiation failed", e );
        }
    }

    private Object getJobInstance( TriggerFiredBundle bundle ) throws Exception
    {
        String daemonEntryId = (String) bundle.getJobDetail( ).getJobDataMap( ).get( Constants.DAEMON_ENTRY_ID_JOB_MAP_KEY );
        if ( null != daemonEntryId )
        {
            // Load daemon from daemon entry
            return AppDaemonService.getDaemon( daemonEntryId );
        }
        else
        {
            // Load job from a native Quartz job
            Class<?> jobClass = bundle.getJobDetail( ).getJobClass( );

            // Try to load the native Job as a CDI bean
            Object o = CDI.current( ).select( jobClass ).get( );
            if ( null != o )
            {
                return o;
            }

            return jobClass.getDeclaredConstructor( ).newInstance( );
        }
    }

    /**
     * Returns a Quartz Job from a natural Job or a Daemon.
     * 
     * @param oJob
     *            the original instance of the specified job class
     * @return the Quartz Job instance
     * @throws Exception
     *             if the given job could not be wrapped
     */
    private Job buildJob( Object oJob ) throws Exception
    {
        if ( oJob instanceof Job job )
        {
            return job;
        }
        else
        {
            if ( oJob instanceof Daemon daemon )
            {
                return new DaemonJob( daemon );
            }
            else
            {
                throw new IllegalArgumentException(
                        "Job class not executable, only Job or Daemon are supported" );
            }
        }
    }
}
