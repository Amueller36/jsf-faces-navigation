package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FlowImpactTreeBuilder {

    private FlowImpactTreeBuilder() {
    }

    public static List<Object> build(
            List<FlowEntry> testEntries) {

        Map<String, SourceBuilder> sources =
                new LinkedHashMap<String, SourceBuilder>();

        List<FlowEntry> other =
                new ArrayList<FlowEntry>();

        if (testEntries != null) {
            for (FlowEntry entry : testEntries) {
                if (entry.getImpactOrigins().isEmpty()) {
                    other.add(entry);
                    continue;
                }

                for (FlowImpactOrigin origin :
                        entry.getImpactOrigins()) {

                    SourceBuilder source =
                            sources.get(
                                    origin.getSourceResourcePath());

                    if (source == null) {
                        source =
                                new SourceBuilder(
                                        origin.getSourceResourcePath());
                        sources.put(
                                origin.getSourceResourcePath(),
                                source);
                    }

                    source.add(
                            entry,
                            origin);
                }
            }
        }

        List<FlowImpactSourceNode> sourceNodes =
                new ArrayList<FlowImpactSourceNode>();

        for (SourceBuilder source :
                sources.values()) {

            sourceNodes.add(
                    source.build());
        }

        Collections.sort(
                sourceNodes,
                new java.util.Comparator<FlowImpactSourceNode>() {
                    @Override
                    public int compare(
                            FlowImpactSourceNode left,
                            FlowImpactSourceNode right) {

                        return left.getSourceResourcePath()
                                .compareToIgnoreCase(
                                        right.getSourceResourcePath());
                    }
                });

        Collections.sort(
                other,
                new java.util.Comparator<FlowEntry>() {
                    @Override
                    public int compare(
                            FlowEntry left,
                            FlowEntry right) {

                        int leftDepth =
                                left.getImpactDepth();
                        int rightDepth =
                                right.getImpactDepth();

                        if (leftDepth > 0
                                && rightDepth <= 0) {
                            return -1;
                        }

                        if (leftDepth <= 0
                                && rightDepth > 0) {
                            return 1;
                        }

                        if (leftDepth > 0
                                && rightDepth > 0
                                && leftDepth != rightDepth) {
                            return leftDepth - rightDepth;
                        }

                        return left.getResourcePath()
                                .compareToIgnoreCase(
                                        right.getResourcePath());
                    }
                });

        List<Object> result =
                new ArrayList<Object>();

        result.addAll(sourceNodes);

        if (!other.isEmpty()) {
            result.add(
                    new FlowOtherTestsNode(
                            other));
        }

        return result;
    }

    private static final class SourceBuilder {

        private final String sourcePath;
        private final Map<String, MethodBuilder> methods =
                new LinkedHashMap<String, MethodBuilder>();

        SourceBuilder(String sourcePath) {
            this.sourcePath = sourcePath;
        }

        void add(
                FlowEntry entry,
                FlowImpactOrigin origin) {

            String identity =
                    origin.getIdentity();

            MethodBuilder method =
                    methods.get(identity);

            if (method == null) {
                method =
                        new MethodBuilder(
                                origin.getMethodHandleIdentifier(),
                                origin.getMethodLabel());
                methods.put(
                        identity,
                        method);
            }

            method.add(
                    entry,
                    origin);
        }

        FlowImpactSourceNode build() {
            FlowImpactSourceNode result =
                    new FlowImpactSourceNode(
                            sourcePath);

            for (MethodBuilder method :
                    methods.values()) {

                result.addMethod(
                        method.build());
            }

            result.sort();
            return result;
        }
    }

    private static final class MethodBuilder {

        private final String handle;
        private final String label;
        private final List<FlowImpactTestNode> tests =
                new ArrayList<FlowImpactTestNode>();

        MethodBuilder(
                String handle,
                String label) {

            this.handle = handle;
            this.label = label;
        }

        void add(
                FlowEntry entry,
                FlowImpactOrigin origin) {

            tests.add(
                    new FlowImpactTestNode(
                            entry,
                            origin));
        }

        FlowImpactMethodNode build() {
            FlowImpactMethodNode result =
                    new FlowImpactMethodNode(
                            handle,
                            label);

            for (FlowImpactTestNode test : tests) {
                result.add(test);
            }

            result.sort();
            return result;
        }
    }
}
