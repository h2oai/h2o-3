package diff;

import java.lang.IllegalArgumentException;

public class DiffHunkHeader {
    private int _remove_start; // Starts with 0, add 1 for actual line number
    private int _insert_start; // Starts with 0, add 1 for actual line number

    public DiffHunkHeader(int remove_start, int insert_start) {
        if (remove_start < 0 || insert_start < 0) {
            throw new IllegalArgumentException("Arguments must be non-negative");
        } else {
            _remove_start = remove_start;
            _insert_start = insert_start;
        }
    }

    public void pushRemove() {
        _remove_start += 1;
    }

    public void pushRemove(int num) {
        if (num < 0) {
            throw new IllegalArgumentException("Argument must be non-negative");
        } else {
            _remove_start += num;
        }
    }

    public void pushInsert() {
        _insert_start += 1;
    }

    public void pushInsert(int num) {
        if (num < 0) {
            throw new IllegalArgumentException("Argument must be non-negative");
        } else {
            _insert_start += num;
        }
    }

    public int getRemoveStart() { return _remove_start; }

    public int getInsertStart() { return _insert_start; }

    public String toString() {
        String out = String.format("DiffHunkHeader: (%d) -> (%d)", _remove_start, _insert_start);
        return out;
    }
}