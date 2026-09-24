package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;

public final class JpaQueryReference {

    private final List<String> segments;
    private final List<Integer> segmentOffsets;
    private final int selectedIndex;
    private final int statementStart;
    private final int statementEnd;

    public JpaQueryReference(
            List<String> segments,
            List<Integer> segmentOffsets,
            int selectedIndex,
            int statementStart,
            int statementEnd) {

        this.segments = Collections.unmodifiableList(
                new ArrayList<String>(segments));
        this.segmentOffsets = Collections.unmodifiableList(
                new ArrayList<Integer>(segmentOffsets));
        this.selectedIndex = selectedIndex;
        this.statementStart = statementStart;
        this.statementEnd = statementEnd;
    }

    public List<String> getSegments() {
        return segments;
    }

    public String getAlias() {
        return segments.get(0);
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public String getSelectedSegment() {
        return segments.get(selectedIndex);
    }

    public IRegion getSelectedRegion() {
        return new Region(
                segmentOffsets.get(selectedIndex).intValue(),
                getSelectedSegment().length());
    }

    public int getStatementStart() {
        return statementStart;
    }

    public int getStatementEnd() {
        return statementEnd;
    }
}
