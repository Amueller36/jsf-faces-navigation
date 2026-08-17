package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class IndexedViewFile {

    private final String resourcePath;
    private final long modificationStamp;
    private final List<ViewSymbol> symbols;

    public IndexedViewFile(
            String resourcePath,
            long modificationStamp,
            List<ViewSymbol> symbols) {

        this.resourcePath = resourcePath;
        this.modificationStamp = modificationStamp;
        this.symbols = Collections.unmodifiableList(
                new ArrayList<ViewSymbol>(symbols));
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public long getModificationStamp() {
        return modificationStamp;
    }

    public List<ViewSymbol> getSymbols() {
        return symbols;
    }
}
