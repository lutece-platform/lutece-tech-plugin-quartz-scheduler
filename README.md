# Quartz Scheduler plugin

The quartz-scheduler plugin replaces the default daemon executor built within Lutece with a daemon executor based on Quartz (https://github.com/quartz-scheduler/quartz).

## Principles

This plugin puts in place two schedulers:
* A local in memory scheduler.
* A clustered scheduler, with a jdbc store, disabled by default, that can be enable through the property ```quartzscheduler.cluster.enable```. In case this scheduler is disabled, all the daemons will be launched with the local scheduler.

``` properties
quartzscheduler.cluster.enable=true
```

## Configure local scheduler

The local scheduler can be configured by the ```quartz-local.properties``` file. The main configuration within this file is the size of the thread pool.

``` properties
org.quartz.threadPool.threadCount=5
```

## Configure clustered scheduler

The clustered scheduler can be configured by the ```quartz-cluster.properties``` file. The main configuration within this file is the size of the thread pool and the connection to the jdbc store. The connection can be managed either by the Lutece connection pool or through a datasource managed by your application server.

``` properties
# Datasource configuration. Choose one of the following options, either the Lutece connection pool or the Datasource managed by the application server
org.quartz.dataSource.luteceQuartzDataSource.connectionProvider.class=fr.paris.lutece.plugins.scheduler.quartz.utils.LuteceConnectionProvider
#org.quartz.dataSource.luteceQuartzDataSource.jndiURL=jdbc/portal
org.quartz.threadPool.threadCount=5
```

The clustered scheduler uses a jdbc store to guarantee that only one instance will launch the daemon at firing time. To select which daemon must be managed by the clustered scheduler, properties of type ```quartzscheduler.daemon.{daemon_id}.disallowedClusterConcurrentExecution``` must be set to ```true```.

Extract from quartz-scheduler.properties 
``` properties
...
# Daemons cluster concurrency. Add here the daemons that must be executed only on one node 
# of the cluster because they are not local and can't manage concurrency. By default all daemons will be considered as 
# capable to manage concurrency and will be run with the local scheduler.
# The expected property name is quartzscheduler.daemon.{daemon_id}.disallowedClusterConcurrentExecution
## Core daemons
quartzscheduler.daemon.anonymizationDaemon.disallowedClusterConcurrentExecution=true
...
``` 
