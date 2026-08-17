package de.andre.jsfnavigation;

import java.util.List;

import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

public final class ELNavigationService {

    private ELNavigationService() {
    }

    public static void navigate(ELSelection selection) {
        try {
            List<String> parts = selection.getExpression().getParts();

            if (parts.isEmpty()) {
                return;
            }

            IType currentType = JSFBeanResolver.resolve(parts.get(0));

            if (currentType == null) {
                return;
            }

            if (selection.getPartIndex() == 0) {
                JavaEditorOpener.open(currentType);
                return;
            }

            for (int i = 1; i <= selection.getPartIndex(); i++) {
                String member = parts.get(i);

                JavaMemberTarget target =
                        JavaPropertyResolver.resolve(currentType, member);

                if (target == null) {
                    return;
                }

                if (i == selection.getPartIndex()) {
                    JavaEditorOpener.open(target);
                    return;
                }

                IType nextType =
                        JavaReturnTypeResolver.resolve(currentType, target);

                if (nextType == null) {
                    return;
                }

                currentType = nextType;
            }

        } catch (JavaModelException e) {
            e.printStackTrace();
        }
    }
}
