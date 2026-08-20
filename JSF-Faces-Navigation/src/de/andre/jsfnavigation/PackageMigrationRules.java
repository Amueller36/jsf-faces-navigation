package de.andre.jsfnavigation;

public final class PackageMigrationRules {

    private PackageMigrationRules() {
    }

    public static String forNewTestPackage(
            String packageName) {

        String value =
                packageName == null
                        ? ""
                        : packageName;

        if ("de.zivit".equals(
                value)) {

            return "de.itzbund";
        }

        if (value.startsWith(
                "de.zivit.")) {

            return "de.itzbund"
                    + value.substring(
                            "de.zivit"
                                    .length());
        }

        return value;
    }
}
