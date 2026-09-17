package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TestHelperFixtureDependency {

    public static final String ENTITY = "ENTITY";
    public static final String TO = "TO";
    public static final String QUERY_BUILDER = "QUERY_BUILDER";
    public static final String TYPE = "TYPE";

    private final String qualifiedType;
    private final String kind;
    private final List<String> reasons;

    public TestHelperFixtureDependency(
            String qualifiedType,
            String kind,
            List<String> reasons) {

        this.qualifiedType =
                qualifiedType == null
                        ? ""
                        : qualifiedType;

        this.kind =
                kind == null
                        ? TYPE
                        : kind;

        this.reasons =
                Collections.unmodifiableList(
                        new ArrayList<String>(
                                reasons == null
                                        ? Collections.<String>emptyList()
                                        : reasons));
    }

    public String getQualifiedType() {
        return qualifiedType;
    }

    public String getSimpleType() {
        return TestHelperAnalysis.simpleType(
                qualifiedType);
    }

    public String getKind() {
        return kind;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public String getPrimaryReason() {
        return reasons.isEmpty()
                ? ""
                : reasons.get(0);
    }

    public String getLabel() {
        StringBuilder out =
                new StringBuilder();

        out.append('[')
                .append(kind)
                .append("] ")
                .append(getSimpleType());

        if (!getPrimaryReason().isEmpty()) {
            out.append(" — ")
                    .append(getPrimaryReason());
        }

        return out.toString();
    }
}
