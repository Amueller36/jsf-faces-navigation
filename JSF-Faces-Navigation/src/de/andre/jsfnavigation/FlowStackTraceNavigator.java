package de.andre.jsfnavigation;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;

public final class FlowStackTraceNavigator {

    private static final Pattern JAVA_FRAME =
            Pattern.compile(
                    "\\s*at\\s+([A-Za-z_$][A-Za-z0-9_$.]*)\\.[^\\s(]+"
                    + "\\([^:()]+\\.java:(\\d+)\\)");

    private FlowStackTraceNavigator() {
    }

    public static boolean open(
            String stackLine) {

        if (stackLine == null) {
            return false;
        }

        Matcher matcher =
                JAVA_FRAME.matcher(
                        stackLine);

        if (!matcher.find()) {
            return false;
        }

        String className =
                stripInnerClass(
                        matcher.group(1));

        int line;

        try {
            line =
                    Integer.parseInt(
                            matcher.group(2));

        } catch (NumberFormatException e) {
            return false;
        }

        IFile file =
                resolveTypeFile(
                        className);

        if (file == null) {
            return false;
        }

        int offset =
                offsetForLine(
                        file,
                        line);

        WebEditorOpener.open(
                file,
                offset);

        return true;
    }

    private static IFile resolveTypeFile(
            String className) {

        try {
            IJavaModel model =
                    JavaCore.create(
                            ResourcesPlugin
                                    .getWorkspace()
                                    .getRoot());

            for (IJavaProject project :
                    model.getJavaProjects()) {

                if (!project.exists()) {
                    continue;
                }

                IType type =
                        project.findType(
                                className);

                if (type == null
                        || !type.exists()) {

                    continue;
                }

                IResource resource =
                        type.getResource();

                if (resource
                        instanceof IFile) {

                    return (IFile)
                            resource;
                }
            }

        } catch (Exception e) {
            return null;
        }

        return null;
    }

    private static String stripInnerClass(
            String className) {

        int dollar =
                className.indexOf('$');

        return dollar >= 0
                ? className.substring(
                        0,
                        dollar)
                : className;
    }

    private static int offsetForLine(
            IFile file,
            int oneBasedLine) {

        String source =
                read(file);

        if (source == null) {
            return 0;
        }

        IDocument document =
                new Document(source);

        int line =
                Math.max(
                        0,
                        oneBasedLine - 1);

        try {
            if (line
                    >= document.getNumberOfLines()) {

                line =
                        Math.max(
                                0,
                                document.getNumberOfLines()
                                        - 1);
            }

            return document.getLineOffset(
                    line);

        } catch (Exception e) {
            return 0;
        }
    }

    private static String read(
            IFile file) {

        InputStream in = null;

        try {
            in =
                    file.getContents();

            ByteArrayOutputStream out =
                    new ByteArrayOutputStream();

            byte[] buffer =
                    new byte[8192];

            int count;

            while ((count =
                    in.read(buffer)) >= 0) {

                out.write(
                        buffer,
                        0,
                        count);
            }

            return new String(
                    out.toByteArray(),
                    Charset.forName(
                            file.getCharset()));

        } catch (Exception e) {
            return null;

        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
