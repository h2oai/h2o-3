package jacoco.report.internal.html.wrapper;

import org.jacoco.core.analysis.IPackageCoverage;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ISourceFileCoverage;
import org.jacoco.core.internal.analysis.PackageCoverageImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by nkalonia1 on 3/17/16.
 */
public class PackageCoverageHighlight extends PackageCoverageImpl implements IHighlightNode {
    private NodeHighlightResults _nhr;

    public PackageCoverageHighlight(final String name,
                                    final Collection<IClassCoverage> classes,
                                    final Collection<ISourceFileCoverage> sourceFiles) {
        super(name, classes, sourceFiles);
        _nhr = new NodeHighlightResults();
    }

    @Override
    public NodeHighlightResults getHighlightResults() {
        return _nhr;
    }
}
