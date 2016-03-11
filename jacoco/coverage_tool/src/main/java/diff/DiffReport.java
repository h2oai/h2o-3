package diff;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

public class DiffReport {
    private List<DiffFile> _files;

    DiffReport() {
        _files = new ArrayList<DiffFile>();
    }

    void pushDiffFile(DiffFile file) {
        _files.add(file);
    }

    public Iterator<DiffFile> iterator() {
        return _files.iterator();
    }

    public DiffFile getDiffFile(String name) {
        for (DiffFile f : _files) {
            if (f.getNameB().equals(name)) return f;
        }
        return null;
    }

    public String toString() {
        String s = "";
        for (DiffFile f : _files) {
            s += f + "\n";
        }
        return s;
    }

    public boolean isEmpty() {
        return _files.isEmpty();
    }
}