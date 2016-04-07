package jacoco.report.internal.html.parse;

import jacoco.report.internal.html.parse.util.*;

class PackageName {
    private NameString _name;
    private boolean _defined;

    public PackageName(final NameString name) {
        _name = name;
        _defined = name != null;
    }
    public PackageName() { this(null); }
    public boolean defined() { return _defined; }
    public NameString getPackageName() { return _name; }

    public boolean matches(String packageName) {
        return defined() && _name.matches(packageName);
    }

    public String toString() {
        return _defined ? _name.get() : null;
    }
}

class ClassName {
    private NameString _name;
    private NameString _signature;
    private NameString _superclass;
    private NameList _interfaces;
    private boolean _defined;

    public ClassName(final NameString name, final NameString signature,
                     final NameString superclass, final NameList interfaces) {
        _name = name;
        _signature = signature;
        _superclass = superclass;
        _interfaces = interfaces;
        _defined = name != null && signature != null && superclass != null && interfaces != null;
    }

    public ClassName() { this(null, null, null, null); }
    public boolean defined() { return _defined; }
    public NameString getClassName() { return _name; }
    public NameString getSignature() { return _signature; }
    public NameString getSuperClass() { return _superclass; }
    public NameList getInterfaces() { return _interfaces; }

    public boolean matches(String name, String signature, String superclass, String[] interfaces) {
        return defined() &&
                _name.matches(name) &&
                _signature.matches(signature) &&
                _superclass.matches(superclass) &&
                _interfaces.matches(interfaces);
    }

    public String toString() {
        return _defined ? _name.get() : null;
    }
}

class MethodName {
    private NameString _name;
    private NameString _desc;
    private NameString _signature;
    private boolean _defined;

    public MethodName(final NameString name, final NameString desc, final NameString signature) {
        _name = name;
        _desc = desc;
        _signature = signature;
        _defined = name != null && desc != null && signature != null;
    }
    public MethodName() { this(null, null, null); }
    public boolean defined() { return _defined; }
    public NameString getMethodName() { return _name; }
    public NameString getDesc() { return _desc; }
    public NameString getSignature() { return _signature; }

    public boolean matches(String name, String description, String signature) {
        return defined() &&
                _name.matches(name) &&
                _desc.matches(description) &&
                _signature.matches(signature);
    }

    public String toString() {
        return _defined ? _name.get() : null;
    }
}

