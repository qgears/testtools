package hu.qgears.eclipse.testtools;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.IHandlerService;
import org.osgi.framework.BundleContext;

/**
 * Eclipse startup hook, allowing autostart operations to be performed after
 * the Eclipse workbench is ready.
 * Currently supported autostart operations:
 * <ul>
 * <li>starting arbitrary launch configurations; see the javadocs of 
 * {@link #SYSPROP_NAME_START_LAUNCHCONFIGS} for more details</li>
 * </ul> 
 * 
 * @author chreex
 */
public class StartupHook implements IStartup {

	/**
	 * System property for automatically starting run configurations after the 
	 * Eclipse workbench is ready, by specifying their names with a 
	 * comma-separated list.
	 */
	public static final String SYSPROP_NAME_START_LAUNCHCONFIGS = "startLaunchConfigs";
	/**
	 * System property for automatically stopping the Eclipse instance that
	 * started the specified launch configuration. Note that if the 
	 * {@link #SYSPROP_NAME_START_LAUNCHCONFIGS} system property is not 
	 * specified, this one has no effect. By default or without specifying this
	 * system property, the launcher Eclipse instance will keep running.
	 */
	public static final String SYSPROP_NAME_EXIT_AFTER_FINISH = "exitAfterLaunchCfgFinished";
	
	public static final String SYSPROP_NAME_STARTUP_DELAY_MS = "lauchConfigStartupDelayMs";
	
	public static final String SYSPROP_NAME_EXECUTE_COMMAND_AFTER_LAUNCHCFG =
			"executeCommandAfterLaunchCfg";
	
	private static final String LAUNCHCONFIG_NAME_SEPARATOR = ",";
	
	/**
	 * @see #SYSPROP_NAME_START_LAUNCHCONFIGS
	 */
	private static final String sysPropStartLaunchConfigs =
			System.getProperty(SYSPROP_NAME_START_LAUNCHCONFIGS);
	
	/**
	 * @see #SYSPROP_NAME_EXIT_AFTER_FINISH
	 */
	private static final boolean sysPropExitAfterLaunchConfigFinished =
			Boolean.getBoolean(SYSPROP_NAME_EXIT_AFTER_FINISH);
	
	/**
	 * @see #SYSPROP_NAME_EXECUTE_COMMAND_AFTER_LAUNCHCFG
	 */
	private static final String sysPropExecuteCommandAfterLaunchCfg =
			System.getProperty(SYSPROP_NAME_EXECUTE_COMMAND_AFTER_LAUNCHCFG);
	
	/**
	 * Startup hook, performing the operations described in the javadocs of this
	 * class ({@link StartupHook}).
	 */
	@Override
	public void earlyStartup() {
		if (sysPropStartLaunchConfigs != null && !sysPropStartLaunchConfigs.isEmpty()) {
			autoStartLaunchConfigs();
		}
	}
	
	private void log(final IStatus status) {
		final BundleContext bundleContext = Activator.getContext();
		final ILog log = Platform.getLog(bundleContext.getBundle());
		
		log.log(status);
	}
	
	private void executeCommand(final String commandId) {
		try {
			final IHandlerService handlerService = (IHandlerService) 
					PlatformUI.getWorkbench().getService(IHandlerService.class);
			
			handlerService.executeCommand(commandId, null);
		} catch (final Exception e) {
			log(new Status(IStatus.ERROR, Activator.PLUGIN_ID, 
					"Exception while running Eclipse command '" + commandId + 
					"' after lauch configuration finished", e));
		}
	}
	
	/**
	 * Creates and starts a Job which will shut down the Eclipse instance after 
	 * the launch configuration given by its name, finishes.
	 * @param launchConfigName name of the launch configuration, which will be
	 * monitored for having been terminated
	 * @return a progress monitor which will shut down the Eclipse instance
	 * after the first launch configuration finishes, or null if the
	 * {@link #SYSPROP_NAME_EXIT_AFTER_FINISH} is not specified or its value is
	 * false. 
	 */
	private void registerShutdownMonitor(final String launchConfigName) {
		final Job finishMonitor = new Job("Waiting to finish launch config...") {
			@Override
			protected IStatus run(final IProgressMonitor monitor) {
				final ILaunchManager launchManager = 
						DebugPlugin.getDefault().getLaunchManager();
				boolean terminated = false;
				
				while (!terminated) {
					for (final ILaunch launch: launchManager.getLaunches()) {
						terminated |= (launch.isTerminated() &&
								launch.getLaunchConfiguration().getName().equals(launchConfigName));
						
						if (terminated) {
							Display.getDefault().asyncExec(new Runnable() {
								
								@Override
								public void run() {
									PlatformUI.getWorkbench().close();
								}
							});
							break;
						}
					}
					
					if (!terminated) {
						try {
							Thread.sleep(1000);
						} catch (final InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
				
				if (sysPropExecuteCommandAfterLaunchCfg != null &&
						!sysPropExecuteCommandAfterLaunchCfg.isEmpty()) {
					executeCommand(sysPropExecuteCommandAfterLaunchCfg);
				}
				
				return new Status(Status.OK, Activator.PLUGIN_ID, 
						"Launch config finished: " + launchConfigName);
			}
		};
		
		finishMonitor.setPriority(Job.LONG);
		finishMonitor.schedule();
	}
	
	/**
	 * Starts a launch configuration given by its name.
	 * @param launchConfigToStart the name of the launch configuration - note
	 * that matching will be performed case-sensitively 
	 * @return the launch descriptor or null if no launch configuration was 
	 * found with the given name 
	 * @throws CoreException thrown if exception occurs either when enumerating
	 * the launch configurations; see 
	 * {@link ILaunchManager#getLaunchConfigurations()} for more information
	 */
	private ILaunch startLaunchConfig(final String launchConfigToStart) 
			throws CoreException {
		final ILaunchManager launchManager = 
				DebugPlugin.getDefault().getLaunchManager();
		final ILaunchConfiguration[] eclipseLaunchConfigs = 
				launchManager.getLaunchConfigurations();

		for (final ILaunchConfiguration eclipseLaunchConfig : eclipseLaunchConfigs) {
			final String eclipseLaunchConfigName = 
					eclipseLaunchConfig.getName();
			
			if (launchConfigToStart.equals(eclipseLaunchConfigName)) {
				try {
					final ILaunch launch = eclipseLaunchConfig.launch(
							ILaunchManager.RUN_MODE, null); 
					
					if (sysPropExitAfterLaunchConfigFinished) {
						/*
						 * Registering a Job as shutdown monitor, as neither 
						 * ILaunchManager.addLaunchListener nor
						 * ILaunchConfiguration.launch(..., IProgressMonitor)
						 * made it. 
						 */
						registerShutdownMonitor(launchConfigToStart);
					}
					
					return launch;
				} catch (final Exception e) {
					log(new Status(IStatus.WARNING, Activator.PLUGIN_ID, 
							"Could not autostart launch configuration '" +
							eclipseLaunchConfigName + "'", e));
				}
			}
		}
		
		return null;
	}

	/**
	 * Starts launch configurations if the {@link #SYSPROP_NAME_START_LAUNCHCONFIGS}
	 * system property is specified.
	 * @see #SYSPROP_NAME_START_LAUNCHCONFIGS
	 */
	private void autoStartLaunchConfigs() {
		try {
			Thread.sleep(Long.getLong(SYSPROP_NAME_STARTUP_DELAY_MS, 0l));
		} catch (final InterruptedException e) {
			e.printStackTrace();
		}
		Display.getDefault().asyncExec(new Runnable() {
			
			@Override
			public void run() {
				final String[] launchConfigsToStart = 
						sysPropStartLaunchConfigs.split(LAUNCHCONFIG_NAME_SEPARATOR);
				try {
					for (final String launchConfigToStart : launchConfigsToStart) {
						final ILaunch launch = startLaunchConfig(launchConfigToStart);
						
						if (launch == null) {
							log(new Status(IStatus.WARNING, Activator.PLUGIN_ID, 
									"Launch configuration not found with" +
									"the name '" + launchConfigToStart + "'"));
						} else {
							log(new Status(IStatus.INFO, Activator.PLUGIN_ID,
									"Launch configuration automatically " +
									"started: " + launchConfigToStart));
						}
					}
				} catch (final CoreException coreEx) {
					log(new Status(IStatus.ERROR, Activator.PLUGIN_ID, 
							"Exception while enumerating launch " +
							"configurations; none will be started", coreEx));
				}
			}

		});
	}

}
