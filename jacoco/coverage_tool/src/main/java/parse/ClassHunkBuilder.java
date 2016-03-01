package parse;

public class ClassHunkBuilder {
    private int _start;
    private String _name;

    protected ClassHunkBuilder() {};

    public ClassHunkBuilder(String name, int start) {
        if (start < 0) {
            throw new IllegalArgumentException("Arguments must be non-negative");
        } else {
            _name = name;
            _start = start;
        }
    }

    public ClassHunk end(int end) {
        if (end < _start) {
            throw new IllegalArgumentException("Ending line number must be greater or equal to start");
        } else {
            return new ClassHunk(_name, _start, end);
        }
    }
}

class InvalidClassHunkBuilder extends ClassHunkBuilder {
    @Override
    public ClassHunk end(int end) {
        throw new IllegalStateException("ClassHunkBuilder is Invalid");
    }
}