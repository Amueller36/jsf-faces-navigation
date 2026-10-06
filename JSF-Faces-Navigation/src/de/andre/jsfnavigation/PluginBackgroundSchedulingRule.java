package de.andre.jsfnavigation;

import org.eclipse.core.runtime.jobs.ISchedulingRule;

/**
 * Serializes the plug-in's low-priority indexing/search maintenance jobs.
 *
 * Eclipse/RAD already performs substantial JDT/WTP work after a save. Running
 * several independent JSF-navigation jobs in parallel can create Java-model
 * lock contention even though every job is technically off the SWT thread.
 * This private mutex rule keeps our own background jobs from piling onto that
 * critical window. It never locks the workspace root and therefore does not
 * block normal resource edits/builds by rule.
 */
final class PluginBackgroundSchedulingRule implements ISchedulingRule {

    static final PluginBackgroundSchedulingRule INSTANCE =
            new PluginBackgroundSchedulingRule();

    private PluginBackgroundSchedulingRule() {
    }

    @Override
    public boolean contains(ISchedulingRule rule) {
        return rule == this;
    }

    @Override
    public boolean isConflicting(ISchedulingRule rule) {
        return rule == this;
    }
}
