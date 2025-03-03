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

import java.util.Date;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;

import fr.paris.lutece.plugins.scheduler.quartz.Constants;
import fr.paris.lutece.plugins.scheduler.quartz.service.DaemonEntryJobService;
import fr.paris.lutece.portal.service.daemon.AppDaemonService;
import fr.paris.lutece.portal.service.daemon.DaemonEntry;
import jakarta.enterprise.inject.spi.CDI;

public class DaemonEntryJobListener implements JobListener
{

    private DaemonEntryJobService _daemonEntryJobService;

    @Override
    public String getName( )
    {
        return DaemonEntryJobListener.class.getName( );
    }

    @Override
    public void jobToBeExecuted( JobExecutionContext context )
    {
        String strDaemonKey = (String) context.getJobDetail( ).getJobDataMap( ).get( Constants.DAEMON_ENTRY_ID_JOB_MAP_KEY );
        DaemonEntry entry = AppDaemonService.getDaemonEntry( strDaemonKey );
        entry.setLastRunDate( new Date( ) );
        entry.setLastRunEndDate( null );
        entry.setInProgress( true );
    }

    @Override
    public void jobExecutionVetoed( JobExecutionContext context )
    {
    }

    @Override
    public void jobWasExecuted( JobExecutionContext context, JobExecutionException jobException )
    {
        String strDaemonKey = (String) context.getJobDetail( ).getJobDataMap( ).get( Constants.DAEMON_ENTRY_ID_JOB_MAP_KEY );
        if ( null != context.getResult( ) && context.getResult( ) instanceof String )
        {
            getDaemonEntryJobService( ).jobExecuted( new JobExecutionResult( strDaemonKey, new Date( ), (String) context.getResult( ) ) );
        }
        AppDaemonService.getDaemonEntry( strDaemonKey ).setInProgress( false );
    }

    private synchronized DaemonEntryJobService getDaemonEntryJobService( )
    {
        if ( null == _daemonEntryJobService )
        {
            _daemonEntryJobService = CDI.current( ).select( DaemonEntryJobService.class ).get( );
        }
        return _daemonEntryJobService;
    }

}
