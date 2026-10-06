package de.andre.jsfnavigation;

import org.eclipse.jdt.core.IType;

public final class JpaResolvedReference {

    private final JpaQueryReference reference;
    private final IType aliasType;
    private final IType declaringType;
    private final JavaMemberTarget member;

    public JpaResolvedReference(
            JpaQueryReference reference,
            IType aliasType,
            IType declaringType,
            JavaMemberTarget member) {

        this.reference = reference;
        this.aliasType = aliasType;
        this.declaringType = declaringType;
        this.member = member;
    }

    public JpaQueryReference getReference() {
        return reference;
    }

    public IType getAliasType() {
        return aliasType;
    }

    public IType getDeclaringType() {
        return declaringType;
    }

    public JavaMemberTarget getMember() {
        return member;
    }
}
