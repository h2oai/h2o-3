package jacoco.report.internal.html.parse;

import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ICoverageNode;
import org.jacoco.core.analysis.IMethodCoverage;
import org.jacoco.core.analysis.IPackageCoverage;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by nkalonia1 on 3/27/16.
 */
public class ParseItem {
    PackageName _package;
    ClassName _class;
    MethodName _method;

    Map<ICoverageNode.CounterEntity, Double> _values;
    boolean _propagate;

    ParseItem(PackageName p, ClassName c, MethodName m, boolean propagate) {
        _package = p;
        _class = c;
        _method = m;
        _propagate = propagate;
        _values = new HashMap<ICoverageNode.CounterEntity, Double>();
    }

    public boolean matches(IPackageCoverage p) {
        return _package.matches(p.getName());
    }

    public boolean matches(IClassCoverage c) {
        return _class.matches(c.getName(), c.getSignature(), c.getSuperName(), c.getInterfaceNames());
    }

    public boolean matches(IMethodCoverage m) {
        return _method.matches(m.getName(), m.getDesc(), m.getSignature());
    }

    public boolean hasPackageName() {
        return _package.defined();
    }

    public boolean hasClassName() {
        return _class.defined();
    }

    public boolean hasMethodName() {
        return _method.defined();
    }

    public String getPackageName() { return _package.toString(); }
    public String getClassName() { return _class.toString(); }
    public String getMethodName() { return _method.toString(); }

    public Collection<ICoverageNode.CounterEntity> getHeaders() {
        return _values.keySet();
    }

    public double getValue(ICoverageNode.CounterEntity ce) {
        return _values.get(ce);
    }

    public boolean propagate() {
        return _propagate;
    }
}