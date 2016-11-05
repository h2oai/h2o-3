package hex.genmodel.algos.tree;

/**
 * Graph Node.
 */
public class Node {
  private final int nodeNumber;
  private final int level;
  private boolean isLeftChild;
  private boolean isRightChild;
  private String colName;
  private float splitValue = Float.NaN;
  private float leafValue = Float.NaN;
  private Node leftChild;
  private Node rightChild;

  public Node(int n, int l) {
    nodeNumber = n;
    level = l;
  }

  public int getLevel() {
    return level;
  }

  void setIsLeftChild() {
    isLeftChild = true;
  }

  void setIsRightChild() {
    isRightChild = true;
  }

  public void setColName(String v) {
    colName = v;
  }

  void setSplitValue(float v) {
    splitValue = v;
  }

  void setLeafValue(float v) {
    leafValue = v;
  }

  void setLeftChild(Node v) {
    leftChild = v;
  }

  void setRightChild(Node v) {
    rightChild = v;
  }

  public String getName() {
    return "Node " + nodeNumber;
  }

  public void print() {
    System.out.println("        Node " + nodeNumber);
    System.out.println("            level:       " + level);
    System.out.println("            colName:     " + ((colName != null) ? colName : ""));
    System.out.println("            splitVal:    " + splitValue);
    System.out.println("            leafValue:   " + leafValue);
    System.out.println("            leftChild:   " + ((leftChild != null) ? leftChild.getName() : ""));
    System.out.println("            rightChild:  " + ((rightChild != null) ? rightChild.getName() : ""));
  }
}
