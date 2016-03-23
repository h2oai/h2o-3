package jacoco.report.internal.html.wrapper;

import org.jacoco.core.analysis.CoverageNodeImpl;
import org.jacoco.core.analysis.ICoverageNode;

/**
 * Created by nkalonia1 on 3/17/16.
 */
public class CoverageNodeHighlight extends CoverageNodeImpl implements IHighlightNode {
    private NodeHighlightResults _nhr;

    public CoverageNodeHighlight(final ElementType elementType, final String name, NodeHighlightResults nhr) {
        super(elementType, name);
        _nhr = nhr;
    }

    public CoverageNodeHighlight(final ElementType elementType, final String name) {
        this(elementType, name, new NodeHighlightResults());
    }


    @Override
    public NodeHighlightResults getHighlightResults() {
        return _nhr;
    }

    @Override
    public CoverageNodeHighlight getPlainCopy() {
        CoverageNodeHighlight copy = new CoverageNodeHighlight(getElementType(), getName(), _nhr.getPlainCopy());
        return copy;
    }

}
