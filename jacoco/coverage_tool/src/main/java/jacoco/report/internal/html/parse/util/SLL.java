package jacoco.report.internal.html.parse.util;

import java.util.NoSuchElementException;

/**
 * Created by nkalonia1 on 3/28/16.
 */
public class SLL<T> {
    protected Node _root;
    protected Node _end;

    public SLL() {
        _root = new Node();
        _end = _root;
    }

    public void add(T t) {
        _end._value = t;
        _end._next = new Node();
        _end = _end._next;
    }

    public boolean isEmpty() {
        return _root == _end;
    }

    public SLLIterator<T> iterator() {
        return new Iterator();
    }

    class Iterator implements SLLIterator<T> {
        private Node _curr;

        Iterator() {
            _curr = _root;
        }

        @Override
        public boolean hasNext() {
            return _curr.isValid() && _curr.hasNext();
        }

        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException("Iterator at end of list");
            } else {
                T val = _curr._value;
                _curr = _curr._next;
                return val;
            }
        }

        @Override
        public boolean atEnd() {
            return _curr == _end;
        }

        @Override
        public void remove() {}
    }

    protected class Node {
        protected Node _next;
        protected T _value;

        public Node(T value, Node next) {
            _value = value;
            _next = next;
        }

        public Node(T value) {
            this (value, null);
        }

        public Node() {
            this(null);
        }

        public boolean hasNext() { return _next != null; }

        public boolean isValid() { return _value != null; }
    }
}
