package hex.genmodel.algos.tree;

import java.io.PrintStream;

/**
 * Graph Node.
 */
public class Node {
  private final int subgraphNumber;
  private final int nodeNumber;
  private final int level;
  private String colName;
  private float splitValue = Float.NaN;
  private float leafValue = Float.NaN;
  private Node leftChild;
  private Node rightChild;

  public Node(int sn, int n, int l) {
    subgraphNumber = sn;
    nodeNumber = n;
    level = l;
  }

  public int getLevel() {
    return level;
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

  Node getLeftChild() {
    return leftChild;
  }

  void setRightChild(Node v) {
    rightChild = v;
  }

  Node getRightChild() {
    return rightChild;
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

  String getDotName() {
    return "SG_" + subgraphNumber + "_Node_" + nodeNumber;
  }

  private boolean getIsLeaf() {
    return (! Float.isNaN(leafValue));
  }

  private void printDot(PrintStream os) {
    os.print("\"" + getDotName() + "\"");
    if (getIsLeaf()) {
      os.print(" [label=\"");
      os.print(leafValue);
      os.print("\"]");
    }
    else {
      os.print(" [shape=box,label=\"");
      os.print(colName + " < " + splitValue);
      os.print("\"]");
    }
    os.println("");
  }

  void printDotLevel(PrintStream os, int levelToPrint) {
    if (getLevel() == levelToPrint) {
      printDot(os);
      return;
    }

    assert (getLevel() < levelToPrint);

    if (leftChild != null) {
      leftChild.printDotLevel(os, levelToPrint);
    }
    if (rightChild != null) {
      rightChild.printDotLevel(os, levelToPrint);
    }
  }
}
