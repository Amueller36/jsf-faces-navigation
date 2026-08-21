package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FeatureTestAuditOrder {

    public static final int CONTROLLER_TO_ISP = 0;
    public static final int ISP_TO_CONTROLLER = 1;

    private static final String[] TOP_DOWN =
            new String[] {
                    FlowCategoryClassifier.CONTROLLER,
                    FlowCategoryClassifier.BEAN,
                    FlowCategoryClassifier.DSP,
                    FlowCategoryClassifier.ISP
            };

    private FeatureTestAuditOrder() {
    }

    public static String[] labels() {
        return new String[] {
                "Controller → Bean → DSP → ISP",
                "ISP → DSP → Bean → Controller"
        };
    }

    public static List<String> roles(
            int mode) {

        List<String> result =
                new ArrayList<String>();

        if (mode == ISP_TO_CONTROLLER) {
            for (int i = TOP_DOWN.length - 1;
                    i >= 0;
                    i--) {

                result.add(
                        TOP_DOWN[i]);
            }

        } else {
            for (String role :
                    TOP_DOWN) {

                result.add(
                        role);
            }
        }

        return result;
    }

    public static Map<String, List<FeatureTestClassStatus>> group(
            FeatureTestAuditReport report,
            int mode,
            boolean includeFullyCovered) {

        Map<String, List<FeatureTestClassStatus>> grouped =
                new LinkedHashMap<String, List<FeatureTestClassStatus>>();

        for (String role :
                roles(
                        mode)) {

            grouped.put(
                    role,
                    new ArrayList<FeatureTestClassStatus>());
        }

        if (report == null) {
            return grouped;
        }

        for (FeatureTestClassStatus clazz :
                report.getClasses()) {

            if (clazz == null) {
                continue;
            }

            if (!includeFullyCovered
                    && clazz.getUntestedCount()
                            == 0) {

                continue;
            }

            List<FeatureTestClassStatus> bucket =
                    grouped.get(
                            clazz.getArchitectureRole());

            if (bucket == null) {
                bucket =
                        new ArrayList<FeatureTestClassStatus>();

                grouped.put(
                        clazz.getArchitectureRole(),
                        bucket);
            }

            bucket.add(
                    clazz);
        }

        for (List<FeatureTestClassStatus> bucket :
                grouped.values()) {

            Collections.sort(
                    bucket,
                    CLASS_COMPARATOR);
        }

        return grouped;
    }

    private static final Comparator<FeatureTestClassStatus>
            CLASS_COMPARATOR =
                    new Comparator<FeatureTestClassStatus>() {
                        @Override
                        public int compare(
                                FeatureTestClassStatus left,
                                FeatureTestClassStatus right) {

                            boolean leftMissingTest =
                                    !left.hasTestClass();

                            boolean rightMissingTest =
                                    !right.hasTestClass();

                            if (leftMissingTest
                                    != rightMissingTest) {

                                return leftMissingTest
                                        ? -1
                                        : 1;
                            }

                            int untested =
                                    right.getUntestedCount()
                                    - left.getUntestedCount();

                            if (untested != 0) {
                                return untested;
                            }

                            String leftPackage =
                                    packageName(
                                            left);

                            String rightPackage =
                                    packageName(
                                            right);

                            int packageCompare =
                                    leftPackage
                                            .compareToIgnoreCase(
                                                    rightPackage);

                            if (packageCompare != 0) {
                                return packageCompare;
                            }

                            String leftName =
                                    left.getProductionType() == null
                                            ? ""
                                            : left.getProductionType()
                                                    .getElementName();

                            String rightName =
                                    right.getProductionType() == null
                                            ? ""
                                            : right.getProductionType()
                                                    .getElementName();

                            return leftName
                                    .compareToIgnoreCase(
                                            rightName);
                        }
                    };

    private static String packageName(
            FeatureTestClassStatus clazz) {

        if (clazz == null
                || clazz.getProductionType()
                        == null
                || clazz.getProductionType()
                        .getPackageFragment()
                        == null) {

            return "";
        }

        String name =
                clazz.getProductionType()
                        .getPackageFragment()
                        .getElementName();

        return name == null
                ? ""
                : name;
    }
}
