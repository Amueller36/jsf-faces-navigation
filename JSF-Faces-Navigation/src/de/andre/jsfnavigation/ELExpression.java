package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ELExpression {

    private final List<String> parts;
    private final List<Integer> partOffsets;
    private final List<Boolean> methodInvocations;

    public ELExpression(List<String> parts, List<Integer> partOffsets) {
        this(parts, partOffsets, null);
    }

    public ELExpression(
            List<String> parts,
            List<Integer> partOffsets,
            List<Boolean> methodInvocations) {

        this.parts = Collections.unmodifiableList(new ArrayList<String>(parts));
        this.partOffsets = Collections.unmodifiableList(new ArrayList<Integer>(partOffsets));

        List<Boolean> calls = new ArrayList<Boolean>();
        for (int i = 0; i < this.parts.size(); i++) {
            calls.add(Boolean.valueOf(
                    methodInvocations != null
                    && i < methodInvocations.size()
                    && Boolean.TRUE.equals(methodInvocations.get(i))));
        }

        this.methodInvocations =
                Collections.unmodifiableList(calls);
    }

    public List<String> getParts() {
        return parts;
    }

    public int getPartOffset(int index) {
        return partOffsets.get(index).intValue();
    }

    public boolean isMethodInvocation(int index) {
        return index >= 0
                && index < methodInvocations.size()
                && methodInvocations.get(index).booleanValue();
    }
}
