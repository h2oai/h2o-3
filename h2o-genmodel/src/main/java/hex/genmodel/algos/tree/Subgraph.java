package hex.genmodel.algos.tree;

import java.io.PrintStream;
import java.util.ArrayList;

/**
 * Subgraph for representing a tree.
 */
class Subgraph {
  private final int subgraphNumber;
  private final String name;
  private int nextNodeNumber = 0;
  private Node rootNode;

  private ArrayList<Node> nodesArray;

  Subgraph(int sn, String n) {
    subgraphNumber = sn;
    name = n;
    nodesArray = new ArrayList<>();
  }

  Node makeRootNode() {
    Node n = new Node(subgraphNumber, nextNodeNumber, 0);
    nextNodeNumber++;
    nodesArray.add(n);
    rootNode = n;
    return n;
  }

  Node makeLeftChildNode(Node parent) {
    Node child = new Node(subgraphNumber, nextNodeNumber, parent.getLevel() + 1);
    nextNodeNumber++;
    nodesArray.add(child);
    makeLeftEdge(parent, child);
    return child;
  }

  Node makeRightChildNode(Node parent) {
    Node child = new Node(subgraphNumber, nextNodeNumber, parent.getLevel() + 1);
    nextNodeNumber++;
    nodesArray.add(child);
    makeRightEdge(parent, child);
    return child;
  }

  private void makeLeftEdge(Node parent, Node child) {
    parent.setLeftChild(child);
  }

  private void makeRightEdge(Node parent, Node child) {
    parent.setRightChild(child);
  }

  public void print() {
    System.out.println("");
    System.out.println("    ----- " + name + " -----");

    System.out.println("    Nodes");
    for (Node n : nodesArray) {
      n.print();
    }

    System.out.println("");
    System.out.println("    Edges");
    rootNode.printEdges();
  }

  void printDot(PrintStream os) {
    os.println("");
    os.println("subgraph " + "cluster_" + subgraphNumber + " {");
    os.println("/* Nodes */");

    int maxLevel = -1;
    for (Node n : nodesArray) {
      if (n.getLevel() > maxLevel) {
        maxLevel = n.getLevel();
      }
    }

    for (int level = 0; level <= maxLevel; level++) {
      os.println("");
      os.println("/* Level " + level + " */");
      os.println("{");
      rootNode.printDotNodesAtLevel(os, level);
      os.println("}");
    }

    os.println("");
    os.println("/* Edges */");
    for (Node n : nodesArray) {
      n.printDotEdges(os);
    }
    os.println("");
    os.println("fontsize=40");
    os.println("label=\"" + name + "\"");
    os.println("}");
  }
}
