package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IFile;

public final class FlowTransactionLensReport {

    private final IFile file;
    private final List<FlowTransactionMethodReport> methods;

    public FlowTransactionLensReport(
            IFile file,
            List<FlowTransactionMethodReport> methods) {

        this.file = file;

        this.methods =
                Collections.unmodifiableList(
                        new ArrayList<FlowTransactionMethodReport>(
                                methods));
    }

    public IFile getFile() {
        return file;
    }

    public List<FlowTransactionMethodReport> getMethods() {
        return methods;
    }
}
