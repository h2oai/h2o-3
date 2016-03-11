package diff;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.Iterator;

public class DiffFile {
    private final Path _a_path;
    private final Path _b_path;
    private Comparator<DiffHunk> _comp;
    private List<DiffHunk> _diffs;

    public enum Type {
        INSERT, DELETE, MODIFY, NONE;
    }

    public DiffFile(Path old_path, Path new_path) {
        _a_path = old_path;
        _b_path = new_path;
        _diffs = new ArrayList<DiffHunk>();
        sortByRemove();
    }

    public boolean pushDiff(DiffHunk diff) {
        return _diffs.add(diff);
    }

    public Iterator<DiffHunk> iterator() {
        Collections.sort(_diffs, _comp);
        return _diffs.iterator();
    }

    public void sortByInsert() {
        _comp = new Comparator<DiffHunk>() {
            public int compare(DiffHunk o1, DiffHunk o2) {
                return o1.getInsertStart() - o2.getInsertStart();
            }
        };
    }

    public void sortByRemove() {
        _comp = new Comparator<DiffHunk>() {
            public int compare(DiffHunk o1, DiffHunk o2) {
                return o1.getRemoveStart() - o2.getRemoveStart();
            }
        };
    }

    public Path getPathA() {
        return _a_path;
    }

    public Path getPathB() {
        return _b_path;
    }

    public String getNameA() {
        return _a_path.getFileName().toString();
    }

    public String getNameB() {
        return _b_path.getFileName().toString();
    }

    public boolean hasLineB(int num) {
        for (DiffHunk dh : _diffs) {
            if (dh.hasLineB(num - 1)) {
                return true;
            }
        }
        return false;
    }

    public Type getType() {
        if (!_a_path.getFileName().toString().equals("null")) {
            if (!_b_path.getFileName().toString().equals("null")) {
                return Type.MODIFY;
            } else {
                return Type.DELETE;
            }
        } else {
            if (!_b_path.getFileName().toString().equals("null")) {
                return Type.INSERT;
            } else {
                return Type.NONE;
            }
        }
    }

    public String toString() {
        Iterator<DiffHunk> i = iterator();
        String out = "DiffFile: '" + _a_path + "' -> '" + _b_path + "'";
        while (i.hasNext()) out += "\n\t" + i.next().toString();
        return out;
    }

    public static void main(String[] args) {
    }

}