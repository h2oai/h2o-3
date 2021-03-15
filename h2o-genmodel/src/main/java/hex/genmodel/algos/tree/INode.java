package hex.genmodel.algos.tree;

public interface INode<K> {
  public boolean isLeaf();
  public float getLeafValue();
  public int getSplitIndex();
  public int next(K value);
  public int getLeftChildIndex();
  public int getRightChildIndex();


}

