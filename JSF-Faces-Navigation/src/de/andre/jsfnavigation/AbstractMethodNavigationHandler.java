package de.andre.jsfnavigation;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jdt.core.IMethod;

public abstract class AbstractMethodNavigationHandler
        extends AbstractHandler {

    private final int mode;

    protected AbstractMethodNavigationHandler(int mode) {
        this.mode = mode;
    }

    @Override
    public Object execute(ExecutionEvent event)
            throws ExecutionException {

        IMethod method =
                MethodContext.currentMethod();

        if (method != null) {
            MethodNavigationService.execute(
                    method,
                    mode);
        }

        return null;
    }
}
