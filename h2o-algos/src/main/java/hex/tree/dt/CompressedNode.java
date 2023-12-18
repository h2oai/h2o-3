package hex.tree.dt;

public class CompressedNode extends AbstractCompressedNode {

    private final AbstractSplittingRule _splittingRule;

    public CompressedNode(final AbstractSplittingRule splittingRule) {
        super();
        this._splittingRule = splittingRule;
    }

    public AbstractSplittingRule getSplittingRule() {
        return _splittingRule;
    }

    @Override
    public String toString() {
        return "[node: " + _splittingRule.toString() + "]";
    }

}
