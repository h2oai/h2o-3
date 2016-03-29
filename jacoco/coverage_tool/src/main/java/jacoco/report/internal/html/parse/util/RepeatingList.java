package jacoco.report.internal.html.parse.util;

/**
 * Created by nkalonia1 on 3/28/16.
 */
public class RepeatingList<T> extends SLL<T> {

    public RepeatingList() {
        super();
        _root._next = _root;
    }

    @Override
    public void add(T t) {
        if (isEmpty()) {
            _end._value = t;
        } else {
            _end._next = new Node(t);
            _end = _end._next;
            _end._next = _end;
        }
    }

    @Override
    public boolean isEmpty() {
        return super.isEmpty() && !_end.isValid();
    }
}
