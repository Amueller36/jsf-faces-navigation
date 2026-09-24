package de.andre.jsfnavigation;

import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.Signature;

public final class FeatureTestAuditClipboardFormatter {

    private FeatureTestAuditClipboardFormatter() {
    }

    public static String formatGerman(
            FeatureTestAuditReport report,
            boolean includeTestedMethods) {

        return formatGerman(
                report,
                includeTestedMethods,
                FeatureTestAuditOrder
                        .CONTROLLER_TO_ISP);
    }

    public static String formatGerman(
            FeatureTestAuditReport report,
            boolean includeTestedMethods,
            int orderMode) {

        if (report == null) {
            return "";
        }

        StringBuilder out =
                new StringBuilder();

        int testedMethods =
                report.getMethodCount()
                - report.getUntestedMethodCount();

        out.append(
                "Feature-Testübersicht: ")
                .append(
                        report.getFeature())
                .append(
                        "\n\n");

        out.append(
                "Zusammenfassung\n")
                .append(
                        "- Relevante Produktionsklassen: ")
                .append(
                        report.getClasses()
                                .size())
                .append(
                        "\n")
                .append(
                        "- Testbare Methoden: ")
                .append(
                        report.getMethodCount())
                .append(
                        "\n")
                .append(
                        "- Bereits durch Testcode referenziert: ")
                .append(
                        testedMethods)
                .append(
                        "\n")
                .append(
                        "- Noch nicht durch Testcode referenziert: ")
                .append(
                        report.getUntestedMethodCount())
                .append(
                        "\n")
                .append(
                        "- Statische Methoden-Referenzabdeckung: ")
                .append(
                        report.getReferenceCoveragePercent())
                .append(
                        " %\n")
                .append(
                        "- Klassen ohne gefundene Testklasse: ")
                .append(
                        report.getClassesWithoutTests())
                .append(
                        "\n")
                .append(
                        "- Architektur-Reihenfolge: ")
                .append(
                        orderLabel(
                                orderMode))
                .append(
                        "\n");

        if (report.isTruncated()) {
            out.append(
                    "- Hinweis: Der Scan hat ein Sicherheitslimit erreicht; die Übersicht kann unvollständig sein.\n");
        }

        out.append(
                "\nHinweis: Die Abdeckung basiert auf statisch aufgelösten Methodenaufrufen im Testcode und ist keine JaCoCo-Laufzeit-Coverage.\n");

        out.append(
                "\nOffene bzw. relevante Klassen\n");

        boolean anyClass = false;

        Map<String, List<FeatureTestClassStatus>> grouped =
                FeatureTestAuditOrder
                        .group(
                                report,
                                orderMode,
                                includeTestedMethods);

        for (Map.Entry<String, List<FeatureTestClassStatus>>
                entry :
                    grouped.entrySet()) {

            if (entry.getValue()
                    .isEmpty()) {

                continue;
            }

            out.append(
                    "\n=== ")
                    .append(
                            entry.getKey())
                    .append(
                            " ===\n");

            for (FeatureTestClassStatus clazz :
                    entry.getValue()) {

                appendClass(
                        out,
                        clazz,
                        includeTestedMethods);

                anyClass = true;
            }
        }

        if (!anyClass) {
            out.append(
                    "- Keine offenen Methoden gefunden.\n");
        }

        if (!includeTestedMethods) {
            int fullyCovered =
                    fullyCoveredClassCount(
                            report);

            if (fullyCovered > 0) {
                out.append(
                        "\nVollständig referenzierte Klassen: ")
                        .append(
                                fullyCovered)
                        .append(
                                " (Details ausgeblendet)\n");
            }
        }

        return out.toString()
                .trim();
    }

    private static void appendClass(
            StringBuilder out,
            FeatureTestClassStatus clazz,
            boolean includeTestedMethods) {

        String className =
                clazz.getProductionType() == null
                        ? "<unbekannt>"
                        : clazz.getProductionType()
                                .getElementName();

        out.append(
                "\n")
                .append(
                        statusLabel(
                                clazz))
                .append(
                        " ")
                .append(
                        className)
                .append(
                        " [")
                .append(
                        clazz.getArchitectureRole())
                .append(
                        "]\n");

        out.append(
                "- Package: ")
                .append(
                        productionPackage(
                                clazz))
                .append(
                        "\n");

        out.append(
                "- Methoden: ")
                .append(
                        clazz.getTestedCount())
                .append(
                        "/")
                .append(
                        clazz.getMethods()
                                .size())
                .append(
                        " referenziert, ")
                .append(
                        clazz.getUntestedCount())
                .append(
                        " offen (")
                .append(
                        clazz.getReferenceCoveragePercent())
                .append(
                        " %)\n");

        if (clazz.getTests()
                .isEmpty()) {

            out.append(
                    "- Vorhandene Testklassen: keine\n");

        } else {
            out.append(
                    "- Vorhandene Testklassen:\n");

            for (TestTargetCandidate test :
                    clazz.getTests()) {

                if (test == null
                        || test.getType()
                                == null) {

                    continue;
                }

                out.append(
                        "  - ")
                        .append(
                                test.getType()
                                        .getElementName())
                        .append(
                                " [")
                        .append(
                                TestTargetCandidate
                                        .classificationLabel(
                                                test.getClassification()))
                        .append(
                                "]")
                        .append(
                                " — Package: ")
                        .append(
                                testPackage(
                                        test));

                if (test.getType()
                        .getJavaProject()
                        != null) {

                    out.append(
                            " — ")
                            .append(
                                    test.getType()
                                            .getJavaProject()
                                            .getElementName());
                }

                out.append(
                        "\n");
            }
        }

        if (clazz.getUntestedCount()
                > 0) {

            out.append(
                    "- Noch nicht durch Testcode referenzierte Methoden:\n");

            for (FeatureTestMethodStatus method :
                    clazz.getMethods()) {

                if (method == null
                        || method.isTested()) {

                    continue;
                }

                out.append(
                        "  - ")
                        .append(
                                methodSignature(
                                        method.getMethod()))
                        .append(
                                "\n");
            }
        }

        if (includeTestedMethods
                && clazz.getTestedCount() > 0) {

            out.append(
                    "- Bereits referenzierte Methoden:\n");

            for (FeatureTestMethodStatus method :
                    clazz.getMethods()) {

                if (method == null
                        || !method.isTested()) {

                    continue;
                }

                out.append(
                        "  - ")
                        .append(
                                methodSignature(
                                        method.getMethod()));

                List<String> references =
                        method.getTestReferences();

                if (!references.isEmpty()) {
                    out.append(
                            " ← ")
                            .append(
                                    references.get(0));

                    if (references.size() > 1) {
                        out.append(
                                " (+")
                                .append(
                                        references.size() - 1)
                                .append(
                                        " weitere)");
                    }
                }

                out.append(
                        "\n");
            }
        }
    }



    private static String orderLabel(
            int orderMode) {

        return orderMode
                == FeatureTestAuditOrder
                        .ISP_TO_CONTROLLER
                                ? "ISP → DSP → Bean → Controller"
                                : "Controller → Bean → DSP → ISP";
    }

    private static String productionPackage(
            FeatureTestClassStatus clazz) {

        if (clazz == null
                || clazz.getProductionType()
                        == null
                || clazz.getProductionType()
                        .getPackageFragment()
                        == null) {

            return "<Default Package>";
        }

        String packageName =
                clazz.getProductionType()
                        .getPackageFragment()
                        .getElementName();

        return packageName == null
                || packageName.isEmpty()
                        ? "<Default Package>"
                        : packageName;
    }

    private static String testPackage(
            TestTargetCandidate test) {

        if (test == null
                || test.getType()
                        == null
                || test.getType()
                        .getPackageFragment()
                        == null) {

            return "<Default Package>";
        }

        String packageName =
                test.getType()
                        .getPackageFragment()
                        .getElementName();

        return packageName == null
                || packageName.isEmpty()
                        ? "<Default Package>"
                        : packageName;
    }

    private static String statusLabel(
            FeatureTestClassStatus clazz) {

        if (!clazz.hasTestClass()) {
            return "[KEINE TESTKLASSE]";
        }

        if (clazz.getUntestedCount()
                > 0) {

            return "[TEILWEISE]";
        }

        return "[OK]";
    }

    private static String methodSignature(
            IMethod method) {

        if (method == null) {
            return "<unbekannte Methode>";
        }

        StringBuilder out =
                new StringBuilder();

        out.append(
                method.getElementName())
                .append(
                        "(");

        String[] parameters =
                method.getParameterTypes();

        for (int i = 0;
                i < parameters.length;
                i++) {

            if (i > 0) {
                out.append(
                        ", ");
            }

            out.append(
                    Signature.toString(
                            parameters[i]));
        }

        return out.append(
                ")")
                .toString();
    }

    private static int fullyCoveredClassCount(
            FeatureTestAuditReport report) {

        int count = 0;

        for (FeatureTestClassStatus clazz :
                report.getClasses()) {

            if (clazz != null
                    && clazz.getUntestedCount()
                            == 0) {

                count++;
            }
        }

        return count;
    }
}
