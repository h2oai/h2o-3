package jacoco.report.internal.html.wrapper;

import org.jacoco.core.analysis.IPackageCoverage;
import org.jacoco.core.internal.analysis.BundleCoverageImpl;
import java.util.Collection;

/**
 * Created by nkalonia1 on 3/17/16.
 */
public class BundleCoverageHighlight extends BundleCoverageImpl implements IHighlightNode {
    NodeHighlightResults _nhr;
    public BundleCoverageHighlight(final String name,
                                   final Collection<IPackageCoverage> packages) {
        super(name, packages);
        _nhr = new NodeHighlightResults();
    }

    @Override
    public NodeHighlightResults getHighlightResults() {
        return _nhr;
    }
}
