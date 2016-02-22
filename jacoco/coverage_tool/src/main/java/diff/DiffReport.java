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

    public boolean isEmpty() {
        return _files.isEmpty();
    }
}