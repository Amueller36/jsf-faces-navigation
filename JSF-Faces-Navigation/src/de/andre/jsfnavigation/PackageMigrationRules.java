package de.andre.jsfnavigation;

public final class PackageMigrationRules {

    private PackageMigrationRules() {
    }

    public static String forNewTestPackage(
            String packageName) {

        return packageName == null
                ? ""
                : packageName;
    }
}
