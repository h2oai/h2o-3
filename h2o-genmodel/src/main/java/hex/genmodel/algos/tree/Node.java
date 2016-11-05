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
  private boolean isBitset = false;
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

  void setBitset() {
    isBitset = true;
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
    System.out.println("            isBitset:    " + isBitset);
    System.out.println("            leafValue:   " + leafValue);
    System.out.println("            leftChild:   " + ((leftChild != null) ? leftChild.getName() : ""));
    System.out.println("            rightChild:  " + ((rightChild != null) ? rightChild.getName() : ""));
  }

  private String getDotName() {
    return "SG_" + subgraphNumber + "_Node_" + nodeNumber;
  }

  private boolean isLeaf() {
    return (! Float.isNaN(leafValue));
  }

  private void printDot(PrintStream os) {
    os.print("\"" + getDotName() + "\"");
    if (isLeaf()) {
      os.print(" [label=\"");
      os.print(leafValue);
      os.print("\"]");
    }
    else if (isBitset) {
      os.print(" [shape=box,label=\"");
      os.print(colName + " bitset");
      os.print("\"]");
    }
    else {
      assert(! Float.isNaN(splitValue));
      os.print(" [shape=box,label=\"");
      os.print(colName + " < " + splitValue);
      os.print("\"]");
    }
    os.println("");
  }

  void printDotNodesAtLevel(PrintStream os, int levelToPrint) {
    if (getLevel() == levelToPrint) {
      printDot(os);
      return;
    }

    assert (getLevel() < levelToPrint);

    if (leftChild != null) {
      leftChild.printDotNodesAtLevel(os, levelToPrint);
    }
    if (rightChild != null) {
      rightChild.printDotNodesAtLevel(os, levelToPrint);
    }
  }

  void printDotEdges(PrintStream os) {
    {
      if (leftChild != null) {
        os.print("\"" + getDotName() + "\"" + " -> " + "\"" + leftChild.getDotName() + "\"" + " [");
        os.print("color=red");
        if (isBitset) {
        }
        else {
          os.print(",label=\"<\"");
        }
        os.println("]");
      }
    }
    {
      if (rightChild != null) {
        os.print("\"" + getDotName() + "\"" + " -> " + "\"" + rightChild.getDotName() + "\"" + " [");

        if (isBitset) {
        }
        else {
          os.print("label=\">=\"");
        }

        os.println("]");
      }
    }
  }
}
