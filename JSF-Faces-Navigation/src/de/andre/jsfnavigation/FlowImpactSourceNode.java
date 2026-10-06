package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class FlowImpactSourceNode {

    private final String sourceResourcePath;
    private final List<FlowImpactMethodNode> methods =
            new ArrayList<FlowImpactMethodNode>();

    public FlowImpactSourceNode(
            String sourceResourcePath) {

        this.sourceResourcePath = sourceResourcePath;
    }

    void addMethod(FlowImpactMethodNode method) {
        methods.add(method);
    }

    void sort() {
        Collections.sort(
                methods,
                new java.util.Comparator<FlowImpactMethodNode>() {
                    @Override
                    public int compare(
                            FlowImpactMethodNode left,
                            FlowImpactMethodNode right) {

                        return left.getMethodLabel()
                                .compareToIgnoreCase(
                                        right.getMethodLabel());
                    }
                });

        for (FlowImpactMethodNode method : methods) {
            method.sort();
        }
    }

    public String getSourceResourcePath() {
        return sourceResourcePath;
    }

    public List<FlowImpactMethodNode> getMethods() {
        return Collections.unmodifiableList(methods);
    }

    public int getUniqueTestCount() {
        Set<String> paths =
                new HashSet<String>();

        for (FlowImpactMethodNode method : methods) {
            for (FlowImpactTestNode test :
                    method.getTests()) {

                paths.add(
                        test.getEntry()
                                .getResourcePath());
            }
        }

        return paths.size();
    }
}
