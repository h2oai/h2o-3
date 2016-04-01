package jacoco.report.internal.html.parse;

import jacoco.report.internal.html.parse.util.*;

class PackageName implements Cloneable {
    private NameString _name;

    public PackageName(final NameString name) { set(name); }
    public PackageName() { this(new NameString()); }
    public boolean defined() { return _name.isDefined(); }
    public NameString getPackageName() { return _name; }
    public void set(NameString name) { _name = name; }
    public void clear() { _name.clear(); }

    @Override
    public PackageName clone() { return new PackageName(_name);
    }

    public boolean matches(String packageName) {
        return packageName != null && _name.matches(packageName);
    }

    public String toString() {
        return _name.get();
    }
}

class ClassName implements Cloneable {
    private NameString _name;
    private NameString _signature;
    private NameString _superclass;
    private NameList _interfaces;

    public ClassName(final NameString vmname, final NameString vmsignature,
                     final NameString vmsuperclass, final NameList vminterfaces) {
        set(vmname, vmsignature, vmsuperclass, vminterfaces);
    }
    public ClassName(final NameString name) { this(name, new NameString(null), new NameString(null), new NameList()); }
    public ClassName() { this(new NameString(), new NameString(), new NameString(), new NameList()); }
    public boolean defined() { return _name.isDefined() && _signature.isDefined() && _superclass.isDefined() && _interfaces.isDefined(); }
    public NameString getClassName() { return _name; }
    public NameString getSignature() { return _signature; }
    public NameString getSuperClass() { return _superclass; }
    public NameList getInterfaces() { return _interfaces; }
    public void set(final NameString name, final NameString signature, final NameString superclass, final NameList interfaces) {
        _name = name;
        _signature = signature;
        _superclass = superclass;
        _interfaces = interfaces;
    }
    public void clear() { _name.clear(); _signature.clear(); _superclass.clear(); _interfaces.clear(); }

    public ClassName clone() {
        return new ClassName(_name, _signature, _superclass, _interfaces);
    }

    public boolean matches(String name, String signature, String superclass, String[] interfaces) {
        return name != null && signature != null && superclass != null && interfaces != null &&
                _name.matches(name) &&
                _signature.matches(signature) &&
                _superclass.matches(superclass) &&
                _interfaces.matches(interfaces);
    }

    public String toString() {
        return _name.get();
    }
}

class MethodName implements Cloneable {
    private NameString _name;
    private NameString _desc;
    private NameString _signature;

    public MethodName(final NameString vmname, final NameString vmdesc, final NameString vmsignature) {
        set(vmname, vmdesc, vmsignature);
    }
    public MethodName() { this(new NameString(), new NameString(), new NameString()); }
    public boolean defined() { return _name.isDefined() && _desc.isDefined() && _signature.isDefined(); }
    public NameString getMethodName() { return _name; }
    public NameString getDesc() { return _desc; }
    public NameString getSignature() { return _signature; }
    public void set(final NameString name, final NameString desc, final NameString signature) {
        _name = name;
        _desc = desc;
        _signature = signature;
    }
    public void clear() { _name.clear(); _desc.clear(); _signature.clear(); }

    public MethodName clone() {
        return new MethodName(_name, _desc, _signature);
    }

    public boolean matches(String name, String description, String signature) {
        return name != null && description != null && signature != null &&
                _name.matches(name) &&
                _desc.matches(description) &&
                _signature.matches(signature);
    }
}

