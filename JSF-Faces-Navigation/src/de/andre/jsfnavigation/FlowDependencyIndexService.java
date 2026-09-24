package de.andre.jsfnavigation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NameQualifiedType;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;

public final class FlowDependencyIndexService {

    /*
     * This is intentionally a visual architecture slice, not an unbounded
     * whole-workspace graph traversal. Seven Java edges are enough to cover
     * Controller -> Bean -> ISP -> Service -> Persistence -> Entity/TO style
     * chains while guarding against accidental graph explosions.
     */
    private static final int MAX_FOCUS_DEPTH = 7;
    private static final int MAX_RELATED_FILES = 150;
    private static final int MAX_CACHE_ENTRIES = 768;

    private final Object cacheLock =
            new Object();

    private final Map<String, CachedDependencies>
            dependencyCache =
                    new LinkedHashMap<String, CachedDependencies>(
                            128,
                            0.75f,
                            true) {

                        private static final long serialVersionUID =
                                1L;

                        @Override
                        protected boolean removeEldestEntry(
                                Map.Entry<String, CachedDependencies>
                                        eldest) {

                            return size()
                                    > MAX_CACHE_ENTRIES;
                        }
                    };

    private final Map<String, CachedDependencies>
            inheritanceCache =
                    new LinkedHashMap<String, CachedDependencies>(
                            128,
                            0.75f,
                            true) {

                        private static final long serialVersionUID =
                                1L;

                        @Override
                        protected boolean removeEldestEntry(
                                Map.Entry<String, CachedDependencies>
                                        eldest) {

                            return size()
                                    > MAX_CACHE_ENTRIES;
                        }
                    };

    public void start() {
        // Modification stamps make explicit resource listeners unnecessary.
    }

    public void stop() {
        synchronized (cacheLock) {
            dependencyCache.clear();
            inheritanceCache.clear();
        }
    }

    public FlowFocusResult focus(
            IFile root,
            List<FlowEntry> entries,
            IProgressMonitor monitor) {

        if (root == null
                || !root.exists()
                || entries == null) {

            return new FlowFocusResult(
                    "",
                    Collections
                            .<String, Integer>
                                    emptyMap());
        }

        final Set<String> allowed =
                new LinkedHashSet<String>();

        final Map<String, IFile> files =
                new LinkedHashMap<String, IFile>();

        for (FlowEntry entry : entries) {
            IFile file =
                    ResourcesPlugin.getWorkspace()
                            .getRoot()
                            .getFile(
                                    new org.eclipse.core.runtime.Path(
                                            entry.getResourcePath()));

            if (file == null
                    || !file.exists()) {

                continue;
            }

            String path =
                    file.getFullPath()
                            .toPortableString();

            allowed.add(path);
            files.put(path, file);
        }

        String rootPath =
                root.getFullPath()
                        .toPortableString();

        Map<String, Integer> distances =
                new LinkedHashMap<String, Integer>();

        if (!allowed.contains(rootPath)) {
            return new FlowFocusResult(
                    rootPath,
                    distances);
        }

        distances.put(
                rootPath,
                Integer.valueOf(0));

        Map<String, Set<String>> subtypeEdges =
                buildSubtypeEdges(
                        allowed,
                        files,
                        monitor);

        ArrayDeque<PathDepth> queue =
                new ArrayDeque<PathDepth>();

        queue.add(
                new PathDepth(
                        rootPath,
                        0));

        while (!queue.isEmpty()
                && distances.size()
                        < MAX_RELATED_FILES) {

            if (monitor != null
                    && monitor.isCanceled()) {

                break;
            }

            PathDepth current =
                    queue.removeFirst();

            if (current.depth
                    >= MAX_FOCUS_DEPTH) {

                continue;
            }

            IFile source =
                    files.get(
                            current.path);

            if (source == null
                    || !"java".equalsIgnoreCase(
                            source.getFileExtension())) {

                continue;
            }

            Set<String> dependencies =
                    new LinkedHashSet<String>(
                            dependenciesOf(
                                    source,
                                    monitor));

            /*
             * Binding recovery can occasionally miss an extends/implements
             * edge in partially compiling legacy projects.  Keep the direct
             * source-model hierarchy edge as a cheap, cached fallback.
             */
            dependencies.addAll(
                    inheritanceParentsOf(
                            source));

            /*
             * Normal dependency walking is intentionally one-way.  Class
             * inheritance is the important exception for the Flow Explorer:
             * when focusing a base controller, its concrete country/variant
             * controllers must also be considered related.  The reverse edge
             * is restricted to extends/implements relationships so we do not
             * turn every ordinary Java dependency into a bidirectional graph.
             */
            Set<String> subtypes =
                    subtypeEdges.get(
                            current.path);

            if (subtypes != null) {
                dependencies.addAll(
                        subtypes);
            }

            for (String dependency :
                    dependencies) {

                if (!allowed.contains(
                        dependency)) {

                    continue;
                }

                int nextDepth =
                        current.depth + 1;

                Integer old =
                        distances.get(
                                dependency);

                if (old != null
                        && old.intValue()
                                <= nextDepth) {

                    continue;
                }

                distances.put(
                        dependency,
                        Integer.valueOf(
                                nextDepth));

                queue.addLast(
                        new PathDepth(
                                dependency,
                                nextDepth));

                if (distances.size()
                        >= MAX_RELATED_FILES) {

                    break;
                }
            }
        }

        /*
         * Pages belong to every related managed-bean type, not only to the
         * exact type that was selected as the focus root.  This is what makes
         * an XHTML using ConcreteController.store() stay related when store()
         * is actually declared in BaseController/CoreController.
         */
        addIndexedJsfPages(
                allowed,
                files,
                distances);

        addRelatedJaxbSchemas(
                allowed,
                files,
                distances);

        return new FlowFocusResult(
                rootPath,
                distances);
    }

    private void addRelatedJaxbSchemas(
            Set<String> allowed,
            Map<String, IFile> files,
            Map<String, Integer> distances) {

        XsdIndexService xsd =
                Activator.getXsdIndexService();

        if (xsd == null
                || distances.size()
                        >= MAX_RELATED_FILES) {

            return;
        }

        List<Map.Entry<String, Integer>> related =
                new ArrayList<Map.Entry<String, Integer>>(
                        distances.entrySet());

        for (Map.Entry<String, Integer> item :
                related) {

            if (distances.size()
                    >= MAX_RELATED_FILES) {

                break;
            }

            IFile java =
                    files.get(
                            item.getKey());

            if (java == null
                    || !"java".equalsIgnoreCase(
                            java.getFileExtension())
                    || !FlowJavaSemantics
                            .isJaxb(
                                    java)) {

                continue;
            }

            ICompilationUnit unit =
                    JavaCore.createCompilationUnitFrom(
                            java);

            if (unit == null
                    || !unit.exists()) {

                continue;
            }

            try {
                for (IType type :
                        unit.getAllTypes()) {

                    String[] names =
                            JaxbTypeResolver
                                    .jaxbNames(
                                            type);

                    for (String name :
                            names) {

                        String namespace =
                                JaxbTypeResolver
                                        .jaxbNamespace(
                                                type,
                                                name);

                        for (XsdDefinition definition :
                                xsd.resolve(
                                        namespace,
                                        name)) {

                            String schemaPath =
                                    definition
                                            .getResourcePath();

                            if (!allowed.contains(
                                    schemaPath)
                                    || distances
                                            .containsKey(
                                                    schemaPath)) {

                                continue;
                            }

                            distances.put(
                                    schemaPath,
                                    Integer.valueOf(
                                            item.getValue()
                                                    .intValue()
                                            + 1));

                            if (distances.size()
                                    >= MAX_RELATED_FILES) {

                                return;
                            }
                        }
                    }
                }

            } catch (JavaModelException e) {
                // Flow schema enrichment is best effort only.
            }
        }
    }

    private void addIndexedJsfPages(
            Set<String> allowed,
            Map<String, IFile> files,
            Map<String, Integer> distances) {

        WebIndexService webIndex =
                Activator.getWebIndexService();

        if (webIndex == null) {
            return;
        }

        List<Map.Entry<String, Integer>> related =
                new ArrayList<Map.Entry<String, Integer>>(
                        distances.entrySet());

        try {
            for (Map.Entry<String, Integer> item :
                    related) {

                if (distances.size()
                        >= MAX_RELATED_FILES) {

                    return;
                }

                IFile java =
                        files.get(
                                item.getKey());

                if (java == null
                        || !java.exists()
                        || !"java".equalsIgnoreCase(
                                java.getFileExtension())) {

                    continue;
                }

                ICompilationUnit unit =
                        JavaCore.createCompilationUnitFrom(
                                java);

                if (unit == null
                        || !unit.exists()) {

                    continue;
                }

                for (IType type :
                        unit.getAllTypes()) {

                    String beanName =
                            BeanIntrospector.beanNameOf(
                                    type);

                    if (beanName == null
                            || beanName.isEmpty()) {

                        continue;
                    }

                    List<BeanUsage> usages =
                            webIndex.findBeanUsages(
                                    beanName,
                                    type.getJavaProject()
                                            .getElementName());

                    for (BeanUsage usage :
                            usages) {

                        if (distances.size()
                                >= MAX_RELATED_FILES) {

                            return;
                        }

                        IFile page =
                                usage.getFile();

                        if (page == null
                                || !page.exists()) {

                            continue;
                        }

                        String path =
                                page.getFullPath()
                                        .toPortableString();

                        if (!allowed.contains(path)) {
                            continue;
                        }

                        int pageDepth =
                                item.getValue()
                                        .intValue()
                                + 1;

                        Integer old =
                                distances.get(path);

                        if (old == null
                                || old.intValue()
                                        > pageDepth) {

                            distances.put(
                                    path,
                                    Integer.valueOf(
                                            pageDepth));
                        }
                    }
                }
            }

        } catch (JavaModelException e) {
            // JSF-page focus enrichment is best effort only.
        }
    }

    private Map<String, Set<String>> buildSubtypeEdges(
            Set<String> allowed,
            Map<String, IFile> files,
            IProgressMonitor monitor) {

        Map<String, Set<String>> result =
                new LinkedHashMap<String, Set<String>>();

        for (Map.Entry<String, IFile> item :
                files.entrySet()) {

            if (monitor != null
                    && monitor.isCanceled()) {

                break;
            }

            IFile child = item.getValue();

            if (child == null
                    || !child.exists()
                    || !"java".equalsIgnoreCase(
                            child.getFileExtension())) {

                continue;
            }

            String childPath =
                    item.getKey();

            for (String parentPath :
                    inheritanceParentsOf(
                            child)) {

                if (!allowed.contains(
                        parentPath)) {

                    continue;
                }

                Set<String> children =
                        result.get(
                                parentPath);

                if (children == null) {
                    children =
                            new LinkedHashSet<String>();

                    result.put(
                            parentPath,
                            children);
                }

                children.add(
                        childPath);
            }
        }

        return result;
    }

    private Set<String> inheritanceParentsOf(
            IFile file) {

        String path =
                file.getFullPath()
                        .toPortableString();

        long stamp =
                file.getModificationStamp();

        synchronized (cacheLock) {
            CachedDependencies cached =
                    inheritanceCache.get(
                            path);

            if (cached != null
                    && cached.modificationStamp
                            == stamp) {

                return cached.dependencies;
            }
        }

        Set<String> computed =
                parseInheritanceParents(
                        file);

        synchronized (cacheLock) {
            inheritanceCache.put(
                    path,
                    new CachedDependencies(
                            stamp,
                            computed));
        }

        return computed;
    }

    private static Set<String> parseInheritanceParents(
            IFile file) {

        ICompilationUnit unit =
                JavaCore.createCompilationUnitFrom(
                        file);

        if (unit == null
                || !unit.exists()) {

            return Collections.emptySet();
        }

        Set<String> result =
                new LinkedHashSet<String>();

        try {
            for (IType type :
                    unit.getAllTypes()) {

                addResolvedParent(
                        type,
                        type.getSuperclassName(),
                        result);

                for (String interfaceName :
                        type.getSuperInterfaceNames()) {

                    addResolvedParent(
                            type,
                            interfaceName,
                            result);
                }
            }

        } catch (JavaModelException e) {
            return Collections.emptySet();
        }

        result.remove(
                file.getFullPath()
                        .toPortableString());

        return Collections.unmodifiableSet(
                result);
    }

    private static void addResolvedParent(
            IType child,
            String parentName,
            Set<String> result)
            throws JavaModelException {

        if (child == null
                || parentName == null
                || parentName.isEmpty()) {

            return;
        }

        String[][] resolved =
                child.resolveType(
                        stripGenericArguments(
                                parentName));

        if (resolved == null
                || resolved.length == 0) {

            return;
        }

        for (String[] candidate :
                resolved) {

            if (candidate == null
                    || candidate.length < 2) {

                continue;
            }

            String packageName =
                    candidate[0];

            String typeName =
                    candidate[1];

            String qualified =
                    packageName == null
                            || packageName.isEmpty()
                                    ? typeName
                                    : packageName
                                            + "."
                                            + typeName;

            IType parent =
                    child.getJavaProject()
                            .findType(
                                    qualified);

            if (parent == null
                    || !parent.exists()) {

                continue;
            }

            IResource resource =
                    parent.getResource();

            if (resource instanceof IFile) {
                result.add(
                        ((IFile) resource)
                                .getFullPath()
                                .toPortableString());
            }
        }
    }

    private static String stripGenericArguments(
            String name) {

        int generic =
                name.indexOf('<');

        return generic >= 0
                ? name.substring(
                        0,
                        generic)
                : name;
    }

    private Set<String> dependenciesOf(
            IFile file,
            IProgressMonitor monitor) {

        String path =
                file.getFullPath()
                        .toPortableString();

        long stamp =
                file.getModificationStamp();

        synchronized (cacheLock) {
            CachedDependencies cached =
                    dependencyCache.get(
                            path);

            if (cached != null
                    && cached.modificationStamp
                            == stamp) {

                return cached.dependencies;
            }
        }

        Set<String> computed =
                parseDependencies(
                        file,
                        monitor);

        synchronized (cacheLock) {
            dependencyCache.put(
                    path,
                    new CachedDependencies(
                            stamp,
                            computed));
        }

        return computed;
    }

    private static Set<String> parseDependencies(
            IFile file,
            IProgressMonitor monitor) {

        final ICompilationUnit unit =
                JavaCore.createCompilationUnitFrom(
                        file);

        if (unit == null
                || !unit.exists()) {

            return Collections.emptySet();
        }

        ASTParser parser =
                ASTParser.newParser(
                        AST.getJLSLatest());

        parser.setSource(unit);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);

        final CompilationUnit ast =
                (CompilationUnit)
                        parser.createAST(
                                monitor);

        final Set<String> result =
                new LinkedHashSet<String>();

        ast.accept(
                new ASTVisitor() {

                    @Override
                    public boolean visit(
                            SimpleType node) {

                        addType(
                                node.resolveBinding(),
                                result);
                        return true;
                    }

                    @Override
                    public boolean visit(
                            QualifiedType node) {

                        addType(
                                node.resolveBinding(),
                                result);
                        return true;
                    }

                    @Override
                    public boolean visit(
                            NameQualifiedType node) {

                        addType(
                                node.resolveBinding(),
                                result);
                        return true;
                    }

                    @Override
                    public boolean visit(
                            TypeLiteral node) {

                        addType(
                                node.resolveTypeBinding(),
                                result);
                        return true;
                    }

                    @Override
                    public boolean visit(
                            TypeDeclaration node) {

                        ITypeBinding binding =
                                node.resolveBinding();

                        if (binding != null) {
                            addType(
                                    binding.getSuperclass(),
                                    result);

                            for (ITypeBinding implemented :
                                    binding.getInterfaces()) {

                                addType(
                                        implemented,
                                        result);
                            }
                        }

                        return true;
                    }

                    @Override
                    public boolean visit(
                            MethodInvocation node) {

                        addMethod(
                                node.resolveMethodBinding(),
                                result);
                        return true;
                    }

                    @Override
                    public boolean visit(
                            SuperMethodInvocation node) {

                        addMethod(
                                node.resolveMethodBinding(),
                                result);
                        return true;
                    }

                    @Override
                    public boolean visit(
                            ClassInstanceCreation node) {

                        addMethod(
                                node.resolveConstructorBinding(),
                                result);

                        addType(
                                node.resolveTypeBinding(),
                                result);

                        return true;
                    }

                });

        result.remove(
                file.getFullPath()
                        .toPortableString());

        return Collections.unmodifiableSet(
                result);
    }

    private static void addMethod(
            IMethodBinding method,
            Set<String> result) {

        if (method == null) {
            return;
        }

        addType(
                method.getDeclaringClass(),
                result);

        addType(
                method.getReturnType(),
                result);

        for (ITypeBinding parameter :
                method.getParameterTypes()) {

            addType(
                    parameter,
                    result);
        }
    }

    private static void addType(
            ITypeBinding binding,
            Set<String> result) {

        if (binding == null) {
            return;
        }

        if (binding.isArray()) {
            addType(
                    binding.getElementType(),
                    result);
            return;
        }

        if (binding.isPrimitive()
                || binding.isNullType()
                || binding.isTypeVariable()
                || binding.isWildcardType()
                || binding.isCapture()) {

            return;
        }

        /*
         * A higher layer may only see List<Order> as the return value of a
         * chained call. Capture generic arguments explicitly so the Entity is
         * still related even when there is no local `Order` declaration.
         */
        for (ITypeBinding argument :
                binding.getTypeArguments()) {

            addType(
                    argument,
                    result);
        }

        ITypeBinding declaration =
                binding.getTypeDeclaration();

        IJavaElement element =
                declaration == null
                        ? null
                        : declaration
                                .getJavaElement();

        if (!(element
                instanceof IType)) {

            return;
        }

        IResource resource =
                element.getResource();

        if (!(resource
                instanceof IFile)) {

            return;
        }

        result.add(
                ((IFile) resource)
                        .getFullPath()
                        .toPortableString());
    }

    private static final class PathDepth {

        final String path;
        final int depth;

        PathDepth(
                String path,
                int depth) {

            this.path = path;
            this.depth = depth;
        }
    }

    private static final class CachedDependencies {

        final long modificationStamp;
        final Set<String> dependencies;

        CachedDependencies(
                long modificationStamp,
                Set<String> dependencies) {

            this.modificationStamp =
                    modificationStamp;
            this.dependencies =
                    dependencies;
        }
    }
}
