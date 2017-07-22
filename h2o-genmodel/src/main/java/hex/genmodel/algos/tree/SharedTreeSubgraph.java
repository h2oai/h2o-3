package hex.genmodel.algos.tree;

import java.io.PrintStream;
import java.util.ArrayList;

/**
 * Subgraph for representing a tree.
 * A subgraph contains nodes.
 */
public class SharedTreeSubgraph {
  public final int subgraphNumber;
  public final String name;
  public SharedTreeNode rootNode;

  // Even though all the nodes are reachable from rootNode, keep a second handy list of nodes.
  // For some bookkeeping tasks.
  public ArrayList<SharedTreeNode> nodesArray;

  /**
   * Create a new tree object.
   * @param sn Tree number
   * @param n Tree name
   */
  SharedTreeSubgraph(int sn, String n) {
    subgraphNumber = sn;
    name = n;
    nodesArray = new ArrayList<>();
  }

  /**
   * Make the root node in the tree.
   * @return The node
   */
  SharedTreeNode makeRootNode() {
    SharedTreeNode n = new SharedTreeNode(null, subgraphNumber, 0);
    n.setInclusiveNa(true);
    nodesArray.add(n);
    rootNode = n;
    return n;
  }

  /**
   * Make the left child of a node.
   * @param parent Parent node
   * @return The new child node
   */
  SharedTreeNode makeLeftChildNode(SharedTreeNode parent) {
    SharedTreeNode child = new SharedTreeNode(parent, subgraphNumber, parent.getDepth() + 1);
    nodesArray.add(child);
    makeLeftEdge(parent, child);
    return child;
  }

  /**
   * Make the right child of a node.
   * @param parent Parent node
   * @return The new child node
   */
  SharedTreeNode makeRightChildNode(SharedTreeNode parent) {
    SharedTreeNode child = new SharedTreeNode(parent, subgraphNumber, parent.getDepth() + 1);
    nodesArray.add(child);
    makeRightEdge(parent, child);
    return child;
  }

  private void makeLeftEdge(SharedTreeNode parent, SharedTreeNode child) {
    parent.setLeftChild(child);
  }

  private void makeRightEdge(SharedTreeNode parent, SharedTreeNode child) {
    parent.setRightChild(child);
  }

  void print() {
    System.out.println("");
    System.out.println("    ----- " + name + " -----");

    System.out.println("    Nodes");
    for (SharedTreeNode n : nodesArray) {
      n.print();
    }

    System.out.println("");
    System.out.println("    Edges");
    rootNode.printEdges();
  }

  void printDot(PrintStream os, int maxLevelsToPrintPerEdge, boolean detail, String optionalTitle) {
    os.println("");
    os.println("subgraph " + "cluster_" + subgraphNumber + " {");
    os.println("/* Nodes */");

    int maxLevel = -1;
    for (SharedTreeNode n : nodesArray) {
      if (n.getDepth() > maxLevel) {
        maxLevel = n.getDepth();
      }
    }

    for (int level = 0; level <= maxLevel; level++) {
      os.println("");
      os.println("/* Level " + level + " */");
      os.println("{");
      rootNode.printDotNodesAtLevel(os, level, detail);
      os.println("}");
    }

    os.println("");
    os.println("/* Edges */");
    for (SharedTreeNode n : nodesArray) {
      n.printDotEdges(os, maxLevelsToPrintPerEdge, rootNode.getWeight(), detail);
    }
    os.println("");
    os.println("fontsize=40");
    String title = SharedTreeNode.escapeQuotes((optionalTitle != null) ? optionalTitle : name);
    os.println("label=\"" + title + "\"");
    os.println("}");
  }
}
