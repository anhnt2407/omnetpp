/*--------------------------------------------------------------*
  Copyright (C) 2006-2008 OpenSim Ltd.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  'License' for details on this and other legal matters.
*--------------------------------------------------------------*/

package org.omnetpp.launch;


import static org.eclipse.jface.dialogs.MessageDialogWithToggle.ALWAYS;
import static org.eclipse.jface.dialogs.MessageDialogWithToggle.NEVER;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.IJobChangeListener;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.LaunchConfigurationDelegate;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialogWithToggle;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IPerspectiveDescriptor;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.omnetpp.common.CommonPlugin;
import org.omnetpp.common.IConstants;
import org.omnetpp.common.project.ProjectUtils;
import org.omnetpp.common.simulation.AbstractSimulationProcess;
import org.omnetpp.common.simulation.SimulationEditorInput;
import org.omnetpp.common.util.StringUtils;
import org.omnetpp.launch.tabs.OmnetppLaunchUtils;

/**
 * Can launch a single or multiple simulation process. Understands OMNeT++ specific launch attributes.
 * see IOmnetppLaunchConstants.
 *
 * @author rhornig, andras
 */
public class SimulationRunLaunchDelegate extends LaunchConfigurationDelegate {
    public static final String PREF_SWITCH_TO_SIMULATE_PERSPECTIVE = "org.omnetpp.launch.SwitchToSimulatePerspective";  //TODO add a way to clear this preference!

    public static class JobSimulationProcess extends AbstractSimulationProcess {
        private Job job;
        private IJobChangeListener jobChangeListener;

        public JobSimulationProcess(Job job) {
            this.job = job;
            Job.getJobManager().addJobChangeListener(jobChangeListener = new JobChangeAdapter() {
                @Override
                public void done(IJobChangeEvent event) {
                    if (event.getJob() == JobSimulationProcess.this.job) {
                        Job.getJobManager().removeJobChangeListener(jobChangeListener);
                        fireSimulationProcessTerminated();
                    }
                }
            });
        }

        public boolean canCancel() {
            return !isTerminated();
        }

        public void cancel() {
            job.cancel();
        }

        @Override
        public boolean isSuspended() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return job.getState() == Job.NONE;
        }
    };

    public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor) throws CoreException {

        configuration = OmnetppLaunchUtils.createUpdatedLaunchConfig(configuration, mode);
        OmnetppLaunchUtils.replaceConfigurationInLaunch(launch, configuration);

        monitor.beginTask("Launching Simulation", 1);

        int runs[] = OmnetppLaunchUtils.parseRuns(configuration.getAttribute(IOmnetppLaunchConstants.OPP_RUNNUMBER, ""),
                                                OmnetppLaunchUtils.getMaxNumberOfRuns(configuration));
        Assert.isTrue(runs != null && runs.length > 0);

        // show the debug view if option is checked
        if (configuration.getAttribute(IOmnetppLaunchConstants.OPP_SHOWDEBUGVIEW, false)) {
            Display.getDefault().asyncExec(new Runnable() {
                public void run() {
                    IWorkbenchPage workbenchPage = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
                    try {
                        workbenchPage.showView(IDebugUIConstants.ID_DEBUG_VIEW, null, IWorkbenchPage.VIEW_VISIBLE);
                    } catch (PartInitException e) {
                        LaunchPlugin.logError("Cannot initialize the Debug View", e);
                    }
                }
            });
        }

        String envirStr = StringUtils.defaultIfEmpty(configuration.getAttribute(IOmnetppLaunchConstants.OPP_USER_INTERFACE, "").trim(), IOmnetppLaunchConstants.UI_FALLBACKVALUE);
        boolean openSimulationEditor = envirStr.equals(IOmnetppLaunchConstants.UI_IDE);

        final int portNumber = configuration.getAttribute(IOmnetppLaunchConstants.OPP_HTTP_PORT, -1);
        int numProcesses = configuration.getAttribute(IOmnetppLaunchConstants.OPP_NUM_CONCURRENT_PROCESSES, 1);
        boolean reportProgress = StringUtils.contains(configuration.getAttribute(IOmnetppLaunchConstants.ATTR_PROGRAM_ARGUMENTS, ""), "-u Cmdenv");

        // start a single or batched launch job
        Job job;
        if (runs.length == 1)
            job = new SimulationLauncherJob(configuration, launch, runs[0], reportProgress, portNumber);
        else
            job = new BatchedSimulationLauncherJob(configuration, launch, runs, numProcesses);

        job.schedule();

        // open simulation front-end
        if (openSimulationEditor) {
        	final String launchConfigurationName = configuration.getName();
            final Job launcherjob = job;
            Display.getDefault().asyncExec(new Runnable() {
                public void run() {
                    IWorkbenchWindow workbenchWindow = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
                    IWorkbenchPage workbenchPage = workbenchWindow.getActivePage();
                    try {
                        // offer switching to "Simulation" perspective
                        IPerspectiveDescriptor desc = workbenchWindow.getWorkbench().getPerspectiveRegistry().findPerspectiveWithId(IConstants.SIMULATE_PERSPECTIVE_ID);
                        if (desc == null)
                            LaunchPlugin.logError("Perspective " + IConstants.SIMULATE_PERSPECTIVE_ID + "not found", new RuntimeException());

                        if (desc != null && !workbenchPage.getPerspective().equals(desc)) {
                            IPreferenceStore preferences = LaunchPlugin.getDefault().getPreferenceStore();
                            String pref = preferences.getString(PREF_SWITCH_TO_SIMULATE_PERSPECTIVE);
                            boolean switchPerspective = ALWAYS.equals(pref);
                            if (!NEVER.equals(pref) && !ALWAYS.equals(pref)) { // assuming PROMPT on null or "" too
                                int result = MessageDialogWithToggle.openYesNoQuestion(
                                        workbenchWindow.getShell(),
                                        "Switch Perspective",
                                        "Switch to the 'Simulate' perspective?",
                                        "Remember choice and don't ask again", false,
                                        preferences, PREF_SWITCH_TO_SIMULATE_PERSPECTIVE).getReturnCode();
                                switchPerspective = (result == IDialogConstants.YES_ID);
                            }

                            if (switchPerspective) {
                                CommonPlugin.getDefault().originalPerspective = workbenchPage.getPerspective();
                                workbenchPage.setPerspective(desc);
                            }
                        }

                        // open the editor
                        IEditorInput input = new SimulationEditorInput(launchConfigurationName, "localhost", portNumber, new JobSimulationProcess(launcherjob), launchConfigurationName, true);
                        IDE.openEditor(workbenchPage, input, IConstants.SIMULATION_EDITOR_ID);
                    }
                    catch (PartInitException e) {
                        ErrorDialog.openError(workbenchWindow.getShell(), "Error", "Could not open animation window for the running simulation.", e.getStatus());
                        LaunchPlugin.logError(e);
                    }
                }
            });
        }
    }

    @Override
    protected IProject[] getProjectsForProblemSearch(ILaunchConfiguration configuration, String mode) throws CoreException {
        // NOTE: we need to do this twice: here and in launch() which is kind of superfluous
        //       but it is unclear whether those two incoming configurations are the same or not
        configuration = OmnetppLaunchUtils.createUpdatedLaunchConfig(configuration, mode);
        String projectName = configuration.getAttribute(IOmnetppLaunchConstants.ATTR_PROJECT_NAME, "");

        if (StringUtils.isEmpty(projectName))
            return ProjectUtils.getOpenProjects();
        else {
            IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
            return ProjectUtils.getAllReferencedProjects(project, false, true);
        }
    }
}