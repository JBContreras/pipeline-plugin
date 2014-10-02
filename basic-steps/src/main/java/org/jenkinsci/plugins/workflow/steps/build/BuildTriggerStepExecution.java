package org.jenkinsci.plugins.workflow.steps.build;

import hudson.AbortException;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import javax.inject.Inject;
import jenkins.model.ParameterizedJobMixIn;

/**
 * @author Vivek Pandey
 */
public class BuildTriggerStepExecution extends StepExecution {
    @StepContextParameter
    private transient TaskListener listener;

    @Inject // used only during the start() method, so no need to be persisted
    transient BuildTriggerStep step;

    @SuppressWarnings({"unchecked", "rawtypes"}) // cannot get from ParameterizedJob back to ParameterizedJobMixIn trivially
    @Override
    public boolean start() throws Exception {
        Jenkins jenkins = Jenkins.getInstance();
        String job = step.getValue();
        listener.getLogger().println("Starting building project: " + job);
        final ParameterizedJobMixIn.ParameterizedJob project = jenkins.getItem(job, getContext().get(Job.class), ParameterizedJobMixIn.ParameterizedJob.class);
        if (project == null) {
            throw new AbortException("No parameterized job named " + job + " found");
        }
        new ParameterizedJobMixIn() {
            @Override protected Job asJob() {
                return (Job) project;
            }
        }.scheduleBuild2(project.getQuietPeriod(), new BuildTriggerAction(getContext()));
        return false;
    }

    @Override
    public void stop() {
        Jenkins jenkins = Jenkins.getInstance();

        Queue q = jenkins.getQueue();

        // if the build is still in the queue, abort it.
        // BuildTriggerListener will report the failure, so this method shouldn't call getContext().onFailure()
        for (Queue.Item i : q.getItems()) {
            BuildTriggerAction bta = i.getAction(BuildTriggerAction.class);
            if (bta!=null && bta.getStepContext().equals(getContext())) {
                q.cancel(i);
            }
        }

        // if there's any in-progress build already, abort that.
        // when the build is actually aborted, BuildTriggerListener will take notice and report the failure,
        // so this method shouldn't call getContext().onFailure()
        for (Computer c : jenkins.getComputers()) {
            for (Executor e : c.getExecutors()) {
                if (e.getCurrentExecutable() instanceof Run) {
                    Run<?,?> b = (Run) e.getCurrentExecutable();

                    BuildTriggerAction bta = b.getAction(BuildTriggerAction.class);
                    if (bta!=null && bta.getStepContext().equals(getContext())) {
                        e.interrupt();
                    }
                }
            }
        }
    }
}
