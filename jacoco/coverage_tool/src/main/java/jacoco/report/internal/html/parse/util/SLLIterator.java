package jacoco.report.internal.html.parse.util;

/**
 * Created by nkalonia1 on 3/28/16.
 */
public interface SLLIterator<E> {
    E next();
    boolean hasNext();
    boolean atEnd();
}
