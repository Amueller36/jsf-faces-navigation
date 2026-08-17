package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ELExpression {

    private final List<String> parts;
    private final List<Integer> partOffsets;

    public ELExpression(List<String> parts, List<Integer> partOffsets) {
        this.parts = Collections.unmodifiableList(new ArrayList<String>(parts));
        this.partOffsets = Collections.unmodifiableList(new ArrayList<Integer>(partOffsets));
    }

    public List<String> getParts() {
        return parts;
    }

    public int getPartOffset(int index) {
        return partOffsets.get(index).intValue();
    }
}
