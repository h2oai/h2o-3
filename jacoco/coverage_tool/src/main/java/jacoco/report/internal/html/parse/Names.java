package jacoco.report.internal.html.parse;

import jacoco.report.internal.html.parse.util.RepeatingList;
import jacoco.report.internal.html.parse.util.SLL;
import jacoco.report.internal.html.parse.util.SLLIterator;

/**
 * Created by nkalonia1 on 3/28/16.
 */
class NameString {
    private boolean _defined;
    private boolean _wild;
    private String _name;

    protected NameString(String name, boolean defined, boolean wild) {
        _name = name == null ? "" : name;
        _defined = defined;
        _wild = wild;
    }

    public NameString() {
        this(null, false, false);
    }

    public NameString(String s) {
        this(s, true, false);
    }

    public String get() {
        return _name;
    }

    public void clear() {
        _defined = false;
    }

    public boolean isDefined() { return _defined; }

    public boolean matches(NameString ns) {
        return ns != null && _defined && ns._defined && (_wild || ns._wild || _name.equals(ns._name));
    }
}

class WildString extends NameString {
    public WildString() {
        super(null, true, true);
    }

    @Override
    public void clear() {}
}

class PackageName implements Cloneable {
    private NameString _name;

    public PackageName(final NameString name) { set(name); }
    public PackageName() { this(new NameString()); }
    public boolean defined() { return _name.isDefined(); }
    public NameString getPackageName() { return _name; }
    public void set(NameString name) { _name = name; }
    public void clear() { _name.clear(); }

    @Override
    public PackageName clone() {
        return new PackageName(_name);
    }

    public boolean matches(PackageName p) {
        return p != null && _name.matches(p._name);
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

    public boolean matches(ClassName c) {
        return c != null &&
                _name.matches(c._name) &&
                _signature.matches(c._signature) &&
                _superclass.matches(c._superclass) &&
                _interfaces.matches(c._interfaces);
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

    public boolean matches(MethodName m) {
        return m != null &&
                _name.matches(m._name) &&
                _desc.matches(m._desc) &&
                _signature.matches(m._signature);
    }
}

class NameList {
    private SLL<NameString> _names;
    private boolean _repeating;

    public NameList() {
        this(false);
    }
    public NameList(boolean repeat) {
        _names = (_repeating = repeat) ? new RepeatingList<NameString>() : new SLL<NameString>();
    }
    public NameList(String[] names, boolean repeat) {
        this(repeat);
        if (names != null) {
            for (String s : names) {
                _names.add(new NameString(s));
            }
        }
    }

    public NameList(String[] names) {
        this(names, false);
    }

    public NameList(NameString[] n, boolean repeat) {
        this(repeat);
        if (n != null) {
            for (NameString ns : n) {
                _names.add(ns);
            }
        }
    }

    public NameList(NameString[] n) {
        this(n, false);
    }

    public void clear() {
        _names = _repeating ? new RepeatingList<NameString>() : new SLL<NameString>();
    }

    public boolean isDefined() { return true; }

    public SLLIterator<NameString> iterator() {
        return _names.iterator();
    }

    public boolean matches(NameList n) {
        if (n == null) return false;
        SLLIterator<NameString> a = iterator();
        SLLIterator<NameString> b = n.iterator();
        while (!a.atEnd() || !b.atEnd()) {
            if (!a.hasNext() || !b.hasNext()) {
                return false;
            }
            if (!a.next().matches(b.next())) {
                return false;
            }
        }
        return true;
    }
}
