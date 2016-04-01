package jacoco.report.internal.html.parse.util;

/**
 * Created by nkalonia1 on 3/31/16.
 */
public class NameList {
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

    public boolean matches(String[] l) {
        if (l == null) l = new String[0];
        SLLIterator<NameString> i = iterator();
        for (String s : l) {
            if (!i.hasNext()) return false;
            if (!i.next().matches(s)) return false;
        }
        return i.atEnd();
    }
}
