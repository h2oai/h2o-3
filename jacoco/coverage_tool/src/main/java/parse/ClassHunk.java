package parse;

public class ClassHunk {
    private int _start;
    private int _end;
    private String _name;

    public ClassHunk(String name, int start, int end) {
        if (start < 0 || end < 0) {
            throw new IllegalArgumentException("Arguments must be non-negative");
        } else {
            _name = name;
            _start = start;
            _end = end;
        }
    }

    public int getStart() {
        return _start;
    }

    public int getEnd() {
        return _end;
    }

    public String getName() {
        return _name;
    }

    public String toString() {
        return "Class " + _name + " from line " + _start + " to " + _end;
    }
}
