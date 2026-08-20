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

    public void start() {
        // Modification stamps make explicit resource listeners unnecessary.
    }

    public void stop() {
        synchronized (cacheLock) {
            dependencyCache.clear();
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

        addIndexedJsfPages(
                root,
                allowed,
                distances);

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
                    dependenciesOf(
                            source,
                            monitor);

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
            IFile root,
            Set<String> allowed,
            Map<String, Integer> distances) {

        if (!"java".equalsIgnoreCase(
                root.getFileExtension())) {

            return;
        }

        ICompilationUnit unit =
                JavaCore.createCompilationUnitFrom(
                        root);

        if (unit == null
                || !unit.exists()) {

            return;
        }

        WebIndexService webIndex =
                Activator.getWebIndexService();

        if (webIndex == null) {
            return;
        }

        try {
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

                    IFile page =
                            usage.getFile();

                    if (page == null
                            || !page.exists()) {

                        continue;
                    }

                    String path =
                            page.getFullPath()
                                    .toPortableString();

                    if (allowed.contains(path)
                            && !distances
                                    .containsKey(path)) {

                        distances.put(
                                path,
                                Integer.valueOf(1));
                    }
                }
            }

        } catch (JavaModelException e) {
            // JSF-page focus enrichment is best effort only.
        }
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
                        AST.JLS8);

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
         * A higher layer may only see List<Antrag> as the return value of a
         * chained call. Capture generic arguments explicitly so the Entity is
         * still related even when there is no local `Antrag` declaration.
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
