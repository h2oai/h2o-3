package jacoco.report.internal.html.wrapper;

import org.jacoco.core.analysis.ICoverageNode;

import java.util.Hashtable;

/**
 * Created by nkalonia1 on 3/20/16.
 */
public class NodeHighlightResults {
    Hashtable<ICoverageNode.CounterEntity, Boolean> entity_total_results;
    Hashtable<ICoverageNode.CounterEntity, Boolean> entity_body_results;

    NodeHighlightResults() {
        entity_body_results = new Hashtable<ICoverageNode.CounterEntity, Boolean>();
        entity_total_results = new Hashtable<ICoverageNode.CounterEntity, Boolean>();
        for (ICoverageNode.CounterEntity ce : ICoverageNode.CounterEntity.values()) {
            entity_body_results.put(ce, true);
            entity_total_results.put(ce, true);
        }
    }

    public void mergeBodyResults(NodeHighlightResults nhr) {
        for (ICoverageNode.CounterEntity ce : entity_body_results.keySet()) {
            entity_body_results.put(ce, entity_body_results.get(ce) && nhr.entity_body_results.get(ce));
        }
    }

    public void mergeTotalResults(NodeHighlightResults nhr) {
        for (ICoverageNode.CounterEntity ce : entity_total_results.keySet()) {
            entity_total_results.put(ce, entity_total_results.get(ce) && nhr.entity_total_results.get(ce));
        }
    }

    void mergeTotaltoBody() {
        for (ICoverageNode.CounterEntity ce : entity_body_results.keySet()) {
            entity_body_results.put(ce, entity_body_results.get(ce) && entity_total_results.get(ce));
        }
    }

    public boolean getFinalBodyResult() {
        boolean result = true;
        for (boolean b : entity_body_results.values()) {
            result = result && b;
        }
        return result;
    }

    public boolean getFinalTotalResult() {
        boolean result = true;
        for (boolean b : entity_total_results.values()) {
            result = result && b;
        }
        return result;
    }

    public boolean getEntityBodyResult(ICoverageNode.CounterEntity ce) {
        return entity_body_results.get(ce);
    }

    public boolean getEntityTotalResult(ICoverageNode.CounterEntity ce) {
        return entity_total_results.get(ce);
    }

    public NodeHighlightResults getPlainCopy() {
        NodeHighlightResults copy = new NodeHighlightResults();
        for (ICoverageNode.CounterEntity ce : ICoverageNode.CounterEntity.values()) {
            copy.entity_body_results.put(ce, entity_body_results.get(ce));
            copy.entity_total_results.put(ce, entity_total_results.get(ce));
        }
        return copy;
    }
}
