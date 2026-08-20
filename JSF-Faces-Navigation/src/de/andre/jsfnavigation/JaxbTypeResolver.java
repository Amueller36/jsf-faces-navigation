package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMemberValuePair;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.TypeNameRequestor;

public final class JaxbTypeResolver {

    private static final int MAX_RESULTS = 10;
    private static final int MAX_CACHE_ENTRIES = 128;
    private static final long CACHE_TTL_MILLIS = 30000L;

    private static final Map<String, CachedTypes>
            CACHE =
                    new LinkedHashMap<String, CachedTypes>(
                            32,
                            0.75f,
                            true) {

                        private static final long serialVersionUID =
                                1L;

                        @Override
                        protected boolean removeEldestEntry(
                                Map.Entry<String, CachedTypes>
                                        eldest) {

                            return size()
                                    > MAX_CACHE_ENTRIES;
                        }
                    };

    private JaxbTypeResolver() {
    }

    public static List<IType> findForDefinition(
            final XsdDefinition definition,
            IProgressMonitor monitor) {

        if (definition == null
                || definition.getName()
                        .isEmpty()) {

            return Collections.emptyList();
        }

        String cacheKey =
                definition.getNamespace()
                + "\u0000"
                + definition.getName();

        synchronized (CACHE) {
            CachedTypes cached =
                    CACHE.get(
                            cacheKey);

            if (cached != null
                    && System.currentTimeMillis()
                            - cached.createdAt
                            <= CACHE_TTL_MILLIS) {

                return new ArrayList<IType>(
                        cached.types);
            }
        }

        final Map<String, ScoredType> unique =
                new LinkedHashMap<String, ScoredType>();

        String[] candidateNames =
                candidateTypeNames(
                        definition.getName());

        SearchEngine engine =
                new SearchEngine();

        for (String candidateName :
                candidateNames) {

            if (monitor != null
                    && monitor.isCanceled()) {

                break;
            }

            TypeNameRequestor requestor =
                    new TypeNameRequestor() {
                        @Override
                        public void acceptType(
                                int modifiers,
                                char[] packageName,
                                char[] simpleTypeName,
                                char[][] enclosingTypeNames,
                                String path) {

                            if (enclosingTypeNames != null
                                    && enclosingTypeNames.length > 0) {

                                return;
                            }

                            IType type =
                                    sourceType(
                                            path,
                                            new String(
                                                    simpleTypeName));

                            if (type == null
                                    || !type.exists()) {

                                return;
                            }

                            int score =
                                    score(
                                            type,
                                            definition);

                            if (score <= 0) {
                                return;
                            }

                            ScoredType previous =
                                    unique.get(
                                            type.getHandleIdentifier());

                            if (previous == null
                                    || score
                                            > previous.score) {

                                unique.put(
                                        type.getHandleIdentifier(),
                                        new ScoredType(
                                                type,
                                                score));
                            }
                        }
                    };

            try {
                engine.searchAllTypeNames(
                        null,
                        SearchPattern.R_EXACT_MATCH,
                        candidateName
                                .toCharArray(),
                        SearchPattern.R_EXACT_MATCH
                                | SearchPattern.R_CASE_SENSITIVE,
                        IJavaSearchConstants.TYPE,
                        SearchEngine
                                .createWorkspaceScope(),
                        requestor,
                        IJavaSearchConstants
                                .WAIT_UNTIL_READY_TO_SEARCH,
                        monitor);

            } catch (JavaModelException e) {
                // Continue with other candidate names.
            }
        }

        List<ScoredType> scored =
                new ArrayList<ScoredType>(
                        unique.values());

        Collections.sort(
                scored,
                new Comparator<ScoredType>() {
                    @Override
                    public int compare(
                            ScoredType left,
                            ScoredType right) {

                        int byScore =
                                right.score
                                - left.score;

                        if (byScore != 0) {
                            return byScore;
                        }

                        return left.type
                                .getFullyQualifiedName()
                                .compareToIgnoreCase(
                                        right.type
                                                .getFullyQualifiedName());
                    }
                });

        List<IType> result =
                new ArrayList<IType>();

        for (ScoredType value :
                scored) {

            result.add(
                    value.type);

            if (result.size()
                    >= MAX_RESULTS) {

                break;
            }
        }

        synchronized (CACHE) {
            CACHE.put(
                    cacheKey,
                    new CachedTypes(
                            System.currentTimeMillis(),
                            result));
        }

        return result;
    }

    public static void clearCache() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    public static String[] jaxbNames(
            IType type) {

        if (type == null
                || !type.exists()) {

            return new String[0];
        }

        List<String> names =
                new ArrayList<String>();

        try {
            for (IAnnotation annotation :
                    type.getAnnotations()) {

                String simple =
                        simpleName(
                                annotation
                                        .getElementName());

                if (!"XmlType".equals(simple)
                        && !"XmlRootElement".equals(
                                simple)) {

                    continue;
                }

                String name =
                        memberString(
                                annotation,
                                "name");

                if (name.isEmpty()
                        || "##default".equals(
                                name)) {

                    name =
                            type.getElementName();
                }

                if (!names.contains(
                        name)) {

                    names.add(
                            name);
                }
            }

        } catch (JavaModelException e) {
            return new String[0];
        }

        return names.toArray(
                new String[names.size()]);
    }

    public static String jaxbNamespace(
            IType type,
            String matchingName) {

        if (type == null
                || !type.exists()) {

            return "";
        }

        try {
            for (IAnnotation annotation :
                    type.getAnnotations()) {

                String simple =
                        simpleName(
                                annotation
                                        .getElementName());

                if (!"XmlType".equals(simple)
                        && !"XmlRootElement".equals(
                                simple)) {

                    continue;
                }

                String name =
                        memberString(
                                annotation,
                                "name");

                if (name.isEmpty()
                        || "##default".equals(
                                name)) {

                    name =
                            type.getElementName();
                }

                if (matchingName != null
                        && !matchingName.isEmpty()
                        && !matchingName.equals(
                                name)) {

                    continue;
                }

                String namespace =
                        memberString(
                                annotation,
                                "namespace");

                return "##default".equals(
                        namespace)
                        ? ""
                        : namespace;
            }

        } catch (JavaModelException e) {
            return "";
        }

        return "";
    }

    private static int score(
            IType type,
            XsdDefinition definition) {

        int score = 0;

        String[] names =
                jaxbNames(
                        type);

        for (String name :
                names) {

            if (definition.getName()
                    .equals(name)) {

                score += 100;
            }
        }

        if (type.getElementName()
                .equals(
                        definition.getName())) {

            score += 60;
        }

        String normalized =
                javaName(
                        definition.getName());

        if (type.getElementName()
                .equals(
                        normalized)) {

            score += 45;
        }

        String namespace =
                jaxbNamespace(
                        type,
                        definition.getName());

        if (!definition.getNamespace()
                .isEmpty()
                && definition.getNamespace()
                        .equals(
                                namespace)) {

            score += 80;
        }

        String project =
                type.getJavaProject() == null
                        ? ""
                        : type.getJavaProject()
                                .getElementName()
                                .toLowerCase();

        if (project.contains(
                "jaxb")) {

            score += 35;
        }

        return score;
    }

    private static String[] candidateTypeNames(
            String xsdName) {

        String java =
                javaName(
                        xsdName);

        if (java.equals(
                xsdName)) {

            return new String[] {
                    xsdName
            };
        }

        return new String[] {
                xsdName,
                java
        };
    }

    private static String javaName(
            String value) {

        if (value == null
                || value.isEmpty()) {

            return "";
        }

        StringBuilder result =
                new StringBuilder();

        boolean upperNext = true;

        for (int i = 0;
                i < value.length();
                i++) {

            char c =
                    value.charAt(i);

            if (!Character.isJavaIdentifierPart(
                    c)
                    || c == '-'
                    || c == '.') {

                upperNext = true;
                continue;
            }

            if (upperNext) {
                result.append(
                        Character.toUpperCase(
                                c));

                upperNext = false;

            } else {
                result.append(
                        c);
            }
        }

        return result.toString();
    }

    private static IType sourceType(
            String path,
            String simpleName) {

        if (path == null
                || path.isEmpty()) {

            return null;
        }

        IResource resource =
                ResourcesPlugin.getWorkspace()
                        .getRoot()
                        .findMember(
                                new org.eclipse.core.runtime.Path(
                                        path));

        if (!(resource
                instanceof IFile)) {

            return null;
        }

        ICompilationUnit unit =
                JavaCore.createCompilationUnitFrom(
                        (IFile)
                                resource);

        if (unit == null
                || !unit.exists()) {

            return null;
        }

        IType type =
                unit.getType(
                        simpleName);

        return type.exists()
                ? type
                : null;
    }

    private static String memberString(
            IAnnotation annotation,
            String memberName)
            throws JavaModelException {

        for (IMemberValuePair pair :
                annotation.getMemberValuePairs()) {

            if (!memberName.equals(
                    pair.getMemberName())) {

                continue;
            }

            Object value =
                    pair.getValue();

            return value instanceof String
                    ? (String)
                            value
                    : "";
        }

        return "";
    }

    private static String simpleName(
            String name) {

        if (name == null) {
            return "";
        }

        int dot =
                name.lastIndexOf('.');

        return dot >= 0
                ? name.substring(
                        dot + 1)
                : name;
    }

    private static final class CachedTypes {

        final long createdAt;
        final List<IType> types;

        CachedTypes(
                long createdAt,
                List<IType> types) {

            this.createdAt = createdAt;
            this.types =
                    new ArrayList<IType>(
                            types);
        }
    }

    private static final class ScoredType {

        final IType type;
        final int score;

        ScoredType(
                IType type,
                int score) {

            this.type = type;
            this.score = score;
        }
    }
}
