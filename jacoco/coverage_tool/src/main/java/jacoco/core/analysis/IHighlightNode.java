package jacoco.core.analysis;

import jacoco.report.internal.html.wrapper.NodeHighlightResults;
import org.jacoco.core.analysis.ICoverageNode;

/**
 * Created by nkalonia1 on 3/20/16.
 */
public interface IHighlightNode {
    public NodeHighlightResults getHighlightResults();
}
