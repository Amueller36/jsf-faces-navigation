package de.andre.jsfnavigation;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

public final class FlowTestLaunchSupport {

    private static final int MAX_CACHE_ENTRIES = 384;

    private static final Map<String, CachedInfo>
            CACHE =
                    new LinkedHashMap<String, CachedInfo>(
                            64,
                            0.75f,
                            true) {

                        private static final long serialVersionUID =
                                1L;

                        @Override
                        protected boolean removeEldestEntry(
                                Map.Entry<String, CachedInfo>
                                        eldest) {

                            return size()
                                    > MAX_CACHE_ENTRIES;
                        }
                    };

    private FlowTestLaunchSupport() {
    }

    public static boolean isSafeRunnable(
            IFile file) {

        return info(file).safeUnitCount > 0;
    }

    public static boolean isSafeRunnable(
            IFile file,
            String fullyQualifiedClassName) {

        if (fullyQualifiedClassName == null
                || fullyQualifiedClassName
                        .isEmpty()) {

            return isSafeRunnable(file);
        }

        Integer classification =
                info(file)
                        .classifications
                        .get(
                                fullyQualifiedClassName);

        return classification != null
                && classification.intValue()
                        == FlowTestClassifier.UNIT_TEST;
    }

    public static String blockedReason(
            IFile file,
            String fullyQualifiedClassName) {

        FileInfo info =
                info(file);

        Integer classification =
                fullyQualifiedClassName == null
                        || fullyQualifiedClassName
                                .isEmpty()
                                ? null
                                : info.classifications
                                        .get(
                                                fullyQualifiedClassName);

        if (classification == null) {
            if (info.safeUnitCount > 0) {
                return "";
            }

            if (info.arquillianCount > 0) {
                return "Arquillian integration tests are excluded from Flow play-button launches.";
            }

            if (info.jpaCount > 0) {
                return "JPA/persistence tests are excluded from Flow play-button launches.";
            }

            if (info.integrationCount > 0) {
                return "Integration tests are excluded from Flow play-button launches.";
            }

            return "No safe JUnit unit-test class was found in this file.";
        }

        switch (classification.intValue()) {
            case FlowTestClassifier.ARQUILLIAN_TEST:
                return "Arquillian integration tests are excluded from Flow play-button launches.";

            case FlowTestClassifier.JPA_TEST:
                return "JPA/persistence tests are excluded from Flow play-button launches.";

            case FlowTestClassifier.INTEGRATION_TEST:
                return "Integration tests are excluded from Flow play-button launches.";

            case FlowTestClassifier.NOT_TEST:
                return "This class is not recognized as a JUnit test.";

            default:
                return "";
        }
    }

    public static void clear() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    private static FileInfo info(
            IFile file) {

        if (file == null
                || !file.exists()
                || !"java".equalsIgnoreCase(
                        file.getFileExtension())) {

            return FileInfo.EMPTY;
        }

        String path =
                file.getFullPath()
                        .toPortableString();

        long stamp =
                file.getModificationStamp();

        synchronized (CACHE) {
            CachedInfo cached =
                    CACHE.get(path);

            if (cached != null
                    && cached.modificationStamp
                            == stamp) {

                return cached.info;
            }
        }

        FileInfo computed =
                inspect(file);

        synchronized (CACHE) {
            CACHE.put(
                    path,
                    new CachedInfo(
                            stamp,
                            computed));
        }

        return computed;
    }

    private static FileInfo inspect(
            IFile file) {

        ICompilationUnit unit =
                JavaCore.createCompilationUnitFrom(
                        file);

        if (unit == null
                || !unit.exists()) {

            return FileInfo.EMPTY;
        }

        Map<String, Integer> classifications =
                new LinkedHashMap<String, Integer>();

        int safe = 0;
        int arquillian = 0;
        int jpa = 0;
        int integration = 0;

        try {
            for (IType type :
                    unit.getAllTypes()) {

                if (type.getDeclaringType()
                        != null) {

                    continue;
                }

                int classification =
                        FlowTestClassifier
                                .classify(type);

                classifications.put(
                        type.getFullyQualifiedName(),
                        Integer.valueOf(
                                classification));

                switch (classification) {
                    case FlowTestClassifier.UNIT_TEST:
                        safe++;
                        break;

                    case FlowTestClassifier.ARQUILLIAN_TEST:
                        arquillian++;
                        break;

                    case FlowTestClassifier.JPA_TEST:
                        jpa++;
                        break;

                    case FlowTestClassifier.INTEGRATION_TEST:
                        integration++;
                        break;

                    default:
                        break;
                }
            }

        } catch (JavaModelException e) {
            return FileInfo.EMPTY;
        }

        return new FileInfo(
                classifications,
                safe,
                arquillian,
                jpa,
                integration);
    }

    private static final class CachedInfo {

        final long modificationStamp;
        final FileInfo info;

        CachedInfo(
                long modificationStamp,
                FileInfo info) {

            this.modificationStamp =
                    modificationStamp;
            this.info = info;
        }
    }

    private static final class FileInfo {

        static final FileInfo EMPTY =
                new FileInfo(
                        new LinkedHashMap<String, Integer>(),
                        0,
                        0,
                        0,
                        0);

        final Map<String, Integer> classifications;
        final int safeUnitCount;
        final int arquillianCount;
        final int jpaCount;
        final int integrationCount;

        FileInfo(
                Map<String, Integer> classifications,
                int safeUnitCount,
                int arquillianCount,
                int jpaCount,
                int integrationCount) {

            this.classifications =
                    classifications;
            this.safeUnitCount =
                    safeUnitCount;
            this.arquillianCount =
                    arquillianCount;
            this.jpaCount =
                    jpaCount;
            this.integrationCount =
                    integrationCount;
        }
    }
}
