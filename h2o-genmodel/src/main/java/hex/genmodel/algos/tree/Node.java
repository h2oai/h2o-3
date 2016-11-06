package hex.genmodel.algos.tree;

import hex.genmodel.utils.GenmodelBitSet;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.BitSet;

/**
 * Graph Node.
 */
public class Node {
  private final Node parent;
  private final int subgraphNumber;
  private final int nodeNumber;
  private final int level;
  private int colId;
  private String colName;
  private boolean leftward;
  private boolean naVsRest;
  private float splitValue = Float.NaN;
  private String[] domainValues;
  private GenmodelBitSet bs;
  private float leafValue = Float.NaN;
  private Node leftChild;
  private Node rightChild;

  // When parent is a categorical, levels of parentColId that are reachable to this node.
  // This in particular includes any earlier splits of the same colId.
  private BitSet inclusiveLevels;

  public Node(Node p, int sn, int n, int l) {
    parent = p;
    subgraphNumber = sn;
    nodeNumber = n;
    level = l;
  }

  public int getLevel() {
    return level;
  }

  void setCol(int v1, String v2) {
    colId = v1;
    colName = v2;
  }

  private int getColId() {
    return colId;
  }

  void setLeftward(boolean v) {
    leftward = v;
  }

  void setNaVsRest(boolean v) {
    naVsRest = v;
  }

  void setSplitValue(float v) {
    splitValue = v;
  }

  void setBitset(String[] v1, GenmodelBitSet v2) {
    assert (v1 != null);
    domainValues = v1;
    bs = v2;
  }

  void setLeafValue(float v) {
    leafValue = v;
  }

  private BitSet findInclusiveLevels(int colId) {
    if (parent == null) {
      return null;
    }
    if (parent.getColId() == colId) {
      return inclusiveLevels;
    }
    return parent.findInclusiveLevels(colId);
  }

  private BitSet calculateChildInclusiveLevels(boolean nodeBitsetDoesContain) {
    BitSet inheritedInclusiveLevels = findInclusiveLevels(colId);
    BitSet childInclusiveLevels = new BitSet();
    for (int i = 0; i < domainValues.length; i++) {
      if (bs.contains(i) == nodeBitsetDoesContain) {
        if (inheritedInclusiveLevels == null) {
          // If there is no prior split history for this column, then treat the
          // inherited set as complete.
          childInclusiveLevels.set(i);
        }
        else if (inheritedInclusiveLevels.get(i)) {
          // Filter out levels that were already discarded from a previous split.
          childInclusiveLevels.set(i);
        }
      }
    }
    return childInclusiveLevels;
  }

  void setLeftChild(Node v) {
    leftChild = v;

    if (! isBitset()) {
      return;
    }
    BitSet childInclusiveLevels = calculateChildInclusiveLevels(false);
    v.setInclusiveLevels(childInclusiveLevels);
  }

  void setRightChild(Node v) {
    rightChild = v;

    if (! isBitset()) {
      return;
    }
    BitSet childInclusiveLevels = calculateChildInclusiveLevels(true);
    v.setInclusiveLevels(childInclusiveLevels);
  }

  private void setInclusiveLevels(BitSet v) {
    inclusiveLevels = v;
  }

  private BitSet getInclusiveLevels() {
    return inclusiveLevels;
  }

  public String getName() {
    return "Node " + nodeNumber;
  }

  public void print() {
    System.out.println("        Node " + nodeNumber);
    System.out.println("            level:       " + level);
    System.out.println("            colId:       " + colId);
    System.out.println("            colName:     " + ((colName != null) ? colName : ""));
    System.out.println("            leftward:    " + leftward);
    System.out.println("            naVsRest:    " + naVsRest);
    System.out.println("            splitVal:    " + splitValue);
    System.out.println("            isBitset:    " + isBitset());
    System.out.println("            leafValue:   " + leafValue);
    System.out.println("            leftChild:   " + ((leftChild != null) ? leftChild.getName() : ""));
    System.out.println("            rightChild:  " + ((rightChild != null) ? rightChild.getName() : ""));
  }

  void printEdges() {
    if (leftChild != null) {
      System.out.println("        " + getName() + " ---left---> " + leftChild.getName());
      leftChild.printEdges();
    }
    if (rightChild != null) {
      System.out.println("        " + getName() + " ---right--> " + rightChild.getName());
      rightChild.printEdges();
    }
  }

  private String getDotName() {
    return "SG_" + subgraphNumber + "_Node_" + nodeNumber;
  }

  private boolean isLeaf() {
    return (! Float.isNaN(leafValue));
  }

  private boolean isBitset() {
    return (domainValues != null);
  }

  private void printDot(PrintStream os) {
    os.print("\"" + getDotName() + "\"");
    if (isLeaf()) {
      os.print(" [label=\"");
      os.print(leafValue);
      os.print("\"]");
    }
    else if (isBitset()) {
      os.print(" [shape=box,label=\"");
      os.print(colName);
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

  private void printDotEdgesCommon(PrintStream os, int maxLevelsToPrintPerEdge, ArrayList<String> arr, Node child) {
    if (isBitset()) {
      BitSet childInclusiveLevels = child.getInclusiveLevels();
      int total = childInclusiveLevels.cardinality();
      if ((total > 0) && (total <= maxLevelsToPrintPerEdge)) {
        for (int i = childInclusiveLevels.nextSetBit(0); i >= 0; i = childInclusiveLevels.nextSetBit(i+1)) {
          arr.add(domainValues[i]);
        }
      }
      else {
        arr.add(total + " levels");
      }
    }
    else {
      arr.add("<");
    }
    os.print("label=\"");
    for (String s : arr) {
      os.print(s + "\\n");
    }
    os.print("\"");
    os.println("]");
  }

  void printDotEdges(PrintStream os, int maxLevelsToPrintPerEdge) {
    assert (leftChild == null) == (rightChild == null);

    if (leftChild != null) {
      os.print("\"" + getDotName() + "\"" + " -> " + "\"" + leftChild.getDotName() + "\"" + " [");

      ArrayList<String> arr = new ArrayList<>();
      if (leftward) {
        arr.add("[NA]");
      }
      if (naVsRest) {
        arr.add("[Not NA]");
      }
      printDotEdgesCommon(os, maxLevelsToPrintPerEdge, arr, leftChild);
    }

    if (rightChild != null) {
      os.print("\"" + getDotName() + "\"" + " -> " + "\"" + rightChild.getDotName() + "\"" + " [");

      ArrayList<String> arr = new ArrayList<>();
      if (! leftward) {
        arr.add("[NA]");
      }
      printDotEdgesCommon(os, maxLevelsToPrintPerEdge, arr, rightChild);
    }
  }
}
