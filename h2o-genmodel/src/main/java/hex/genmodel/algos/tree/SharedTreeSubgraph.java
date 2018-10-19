package hex.genmodel.algos.tree;

import hex.genmodel.tools.PrintMojo;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Subgraph for representing a tree.
 * A subgraph contains nodes.
 */
public class SharedTreeSubgraph {
  public final int subgraphNumber;
  public final String name;
  public SharedTreeNode rootNode;
  public int fontSize=14;                 // default size
  public boolean setDecimalPlaces=false;  // default to not change tree split threshold decimal places
  public int nPlaces = -1;

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
  public SharedTreeNode makeRootNode() {
    SharedTreeNode n = new SharedTreeNode(null, subgraphNumber, 0);
    n.setInclusiveNa(true);
    nodesArray.add(n);
    rootNode = n;
    return n;
  }

  public void setDecimalPlace(int nplaces) {
    setDecimalPlaces=true;
    nPlaces = nplaces;
  }

  public void setFontSize(int fontsize) {
    fontSize = fontsize;
  }
  /**
   * Make the left child of a node.
   * @param parent Parent node
   * @return The new child node
   */
  public SharedTreeNode makeLeftChildNode(SharedTreeNode parent) {
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
  public SharedTreeNode makeRightChildNode(SharedTreeNode parent) {
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

  public SharedTreeNode walkNodes(final String path) {
    SharedTreeNode n = rootNode;
    for (int i = 0; i < path.length(); i++) {
      if (n == null)
        return null;
      switch (path.charAt(i)) {
        case 'L':
          n = n.getLeftChild();
          break;
        case 'R':
          n = n.getRightChild();
          break;
        default:
          throw new IllegalArgumentException("Invalid path specification '" + path +
                  "'. Paths must only be made of 'L' and 'R' characters.");
      }
    }
    return n;
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

  void printDot(PrintStream os, int maxLevelsToPrintPerEdge, boolean detail, String optionalTitle, PrintMojo.PrintTreeOptions treeOptions) {
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
      rootNode.printDotNodesAtLevel(os, level, detail, treeOptions);
      os.println("}");
    }

    os.println("");
    os.println("/* Edges */");
    for (SharedTreeNode n : nodesArray) {
      n.printDotEdges(os, maxLevelsToPrintPerEdge, rootNode.getWeight(), detail, treeOptions);
    }
    os.println("");
    os.println("fontsize="+40); // fix title label to be 40pts
    String title = SharedTreeNode.escapeQuotes((optionalTitle != null) ? optionalTitle : name);
    os.println("label=\"" + title + "\"");
    os.println("}");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SharedTreeSubgraph that = (SharedTreeSubgraph) o;
    return subgraphNumber == that.subgraphNumber &&
            Objects.equals(name, that.name) &&
            Objects.equals(rootNode, that.rootNode) &&
            Objects.equals(nodesArray, that.nodesArray);
  }

  @Override
  public int hashCode() {

    return Objects.hash(subgraphNumber);
  }

  @Override
  public String toString() {
    return "SharedTreeSubgraph{" +
            "subgraphNumber=" + subgraphNumber +
            ", name='" + name + '\'' +
            '}';
  }
}
