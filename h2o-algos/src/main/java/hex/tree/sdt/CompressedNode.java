package hex.tree.sdt;

public class CompressedNode extends AbstractCompressedNode {

    private final AbstractSplittingRule _splittingRule;

    public CompressedNode(final AbstractSplittingRule splittingRule) {
        super();
        this._splittingRule = splittingRule;
    }

    public AbstractSplittingRule getSplittingRule() {
        return _splittingRule;
    }

}
