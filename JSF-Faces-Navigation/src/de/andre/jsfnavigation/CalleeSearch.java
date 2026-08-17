package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;

public final class CalleeSearch {

    private CalleeSearch() {
    }

    public static List<NavigationTarget> findDirectCallees(
            final IMethod sourceMethod) {

        final Map<String, NavigationTarget> unique =
                new LinkedHashMap<String, NavigationTarget>();

        final ICompilationUnit unit =
                (ICompilationUnit) sourceMethod
                        .getAncestor(
                                IJavaElement.COMPILATION_UNIT);

        if (unit == null || !unit.exists()) {
            return new ArrayList<NavigationTarget>();
        }

        final int methodStart;
        final int methodEnd;

        try {
            ISourceRange range =
                    sourceMethod.getSourceRange();

            methodStart = range.getOffset();
            methodEnd =
                    range.getOffset()
                    + range.getLength();

        } catch (JavaModelException e) {
            return new ArrayList<NavigationTarget>();
        }

        ASTParser parser =
                ASTParser.newParser(AST.JLS8);

        parser.setSource(unit);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);

        CompilationUnit ast =
                (CompilationUnit) parser.createAST(null);

        ast.accept(
                new ASTVisitor() {
                    @Override
                    public boolean visit(
                            MethodInvocation node) {

                        collect(
                                node,
                                node.resolveMethodBinding(),
                                node.getName()
                                        .getStartPosition(),
                                node.getName()
                                        .getLength());

                        return true;
                    }

                    @Override
                    public boolean visit(
                            SuperMethodInvocation node) {

                        collect(
                                node,
                                node.resolveMethodBinding(),
                                node.getName()
                                        .getStartPosition(),
                                node.getName()
                                        .getLength());

                        return true;
                    }

                    private void collect(
                            ASTNode node,
                            IMethodBinding binding,
                            int nameOffset,
                            int nameLength) {

                        int position =
                                node.getStartPosition();

                        if (position < methodStart
                                || position >= methodEnd) {

                            return;
                        }

                        IMethod method =
                                methodFromBinding(binding);

                        if (method == null) {
                            method =
                                    resolveByCodeSelect(
                                            unit,
                                            nameOffset,
                                            nameLength);
                        }

                        if (method == null
                                || !method.exists()
                                || method.getAncestor(
                                        IJavaElement.COMPILATION_UNIT)
                                        == null) {

                            return;
                        }

                        JavaNavigationTarget target =
                                JavaNavigationTarget
                                        .declaration(method);

                        unique.put(
                                method.getHandleIdentifier(),
                                target);
                    }
                });

        return new ArrayList<NavigationTarget>(
                unique.values());
    }

    private static IMethod methodFromBinding(
            IMethodBinding binding) {

        if (binding == null) {
            return null;
        }

        IJavaElement element =
                binding.getJavaElement();

        return element instanceof IMethod
                ? (IMethod) element
                : null;
    }

    private static IMethod resolveByCodeSelect(
            ICompilationUnit unit,
            int offset,
            int length) {

        try {
            IJavaElement[] elements =
                    unit.codeSelect(offset, length);

            for (IJavaElement element : elements) {
                if (element instanceof IMethod) {
                    return (IMethod) element;
                }
            }

        } catch (JavaModelException e) {
            return null;
        }

        return null;
    }
}
