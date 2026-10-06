package de.andre.jsfnavigation;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;

public final class GenerateTestHelperHandler
        extends AbstractHandler {

    @Override
    public Object execute(
            ExecutionEvent event)
            throws ExecutionException {

        IMethod method =
                selectedMethod(
                        event);

        if (method == null
                || !method.exists()) {

            WebSphereStatusLine.show(
                    "Place the caret in a Java method (or select a method in Outline/Package Explorer) before generating a test helper.");

            return null;
        }

        TestHelperGeneratorLauncher
                .open(
                        method);

        return null;
    }

    private static IMethod selectedMethod(
            ExecutionEvent event) {

        ISelection selection =
                HandlerUtil
                        .getCurrentSelection(
                                event);

        if (selection
                instanceof IStructuredSelection) {

            Object first =
                    ((IStructuredSelection)
                            selection)
                            .getFirstElement();

            if (first instanceof IMethod) {
                return (IMethod)
                        first;
            }

            if (first
                    instanceof IJavaElement) {

                IJavaElement method =
                        ((IJavaElement) first)
                                .getAncestor(
                                        IJavaElement.METHOD);

                if (method
                        instanceof IMethod) {

                    return (IMethod)
                            method;
                }
            }
        }

        return MethodContext
                .currentMethod();
    }
}
