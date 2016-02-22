package diff;

import java.lang.IllegalArgumentException;

public class DiffHunk {
    public enum Type {
        INSERT, REMOVE, REPLACE, NONE
    }

    private final int _remove_start; // Starts with 0, add 1 for actual line number
    private final int _insert_start; // Starts with 0, add 1 for actual line number

    private int _remove_length;
    private int _insert_length;

    public DiffHunk(int remove_start, int insert_start) {
        this(remove_start, 0, insert_start, 0);
    }

    public DiffHunk(int remove_start, int remove_length, int insert_start, int insert_length) {
        if (remove_start < 0 || remove_length < 0 || insert_start < 0 || insert_length < 0) {
            throw new IllegalArgumentException("Arguments must be non-negative");
        } else {
            _remove_start = remove_start;
            _remove_length = remove_length;
            _insert_start = insert_start;
            _insert_length = insert_length;
        }
    }

    public void pushRemove() {
        _remove_length += 1;
    }

    public void pushRemove(int num) {
        if (num < 0) {
            throw new IllegalArgumentException("Argument must be non-negative");
        } else {
            _remove_length += num;
        }
    }

    public void pushInsert() {
        _insert_length += 1;
    }

    public void pushInsert(int num) {
        if (num < 0) {
            throw new IllegalArgumentException("Argument must be non-negative");
        } else {
            _insert_length += num;
        }
    }

    public int getRemoveStart() {
        return _remove_start;
    }

    public int getRemoveLength() {
        return _remove_length;
    }

    public int getRemoveEnd() { return getRemoveStart() + getRemoveLength(); }

    public int getInsertStart() { return _insert_start; }

    public int getInsertLength() { return _insert_length; }

    public int getInsertEnd() { return getInsertStart() + getInsertLength(); }

    public boolean isEmpty() {
        return (_insert_length == 0 && _remove_length == 0);
    }

    public Type getType() {
        if (getRemoveLength() == 0 && getInsertLength() > 0) return Type.INSERT;
        if (getRemoveLength() > 0 && getInsertLength() == 0) return Type.REMOVE;
        if (getRemoveLength() > 0 && getInsertLength() > 0) return Type.REPLACE;
        return Type.NONE;
    }

    public String toString() {
        String out = String.format("DiffHunk: (%d, %d) -> (%d, %d)", _remove_start, _remove_length, _insert_start, _insert_length);
        return out;
    }

}