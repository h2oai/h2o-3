package hex.genmodel.algos.tree;

import java.util.ArrayList;

/**
 * Subgraph for representing a tree.
 */
public class Subgraph {
  private String name;
  private int nextNodeNumber = 0;

  private ArrayList<Node> nodesArray;
  private ArrayList<Edge> edgesArray;

  public Subgraph(String n) {
    name = n;
    nodesArray = new ArrayList<>();
    edgesArray = new ArrayList<>();
  }

  public Node makeRootNode() {
    Node n = new Node(nextNodeNumber, 0);
    nextNodeNumber++;
    nodesArray.add(n);
    return n;
  }

  public Node makeLeftChildNode(Node parent) {
    Node child = new Node(nextNodeNumber, parent.getLevel() + 1);
    nextNodeNumber++;
    nodesArray.add(child);
    makeLeftEdge(parent, child);
    return child;
  }

  public Node makeRightChildNode(Node parent) {
    Node child = new Node(nextNodeNumber, parent.getLevel() + 1);
    nextNodeNumber++;
    nodesArray.add(child);
    makeRightEdge(parent, child);
    return child;
  }

  public Edge makeLeftEdge(Node parent, Node child) {
    parent.setLeftChild(child);
    child.setIsLeftChild();
    Edge e = new Edge(parent, child);
    e.setIsLeftEdge();
    edgesArray.add(e);
    return e;
  }

  public Edge makeRightEdge(Node parent, Node child) {
    parent.setRightChild(child);
    child.setIsRightChild();
    Edge e = new Edge(parent, child);
    edgesArray.add(e);
    e.setIsRightEdge();
    return e;
  }

  public void print() {
    System.out.println("");
    System.out.println("    ----- " + name + " -----");

    System.out.println("    Nodes");
    for (int i = 0; i < nodesArray.size(); i++) {
      nodesArray.get(i).print();
    }
    System.out.println("");
    System.out.println("    Edges");
    for (int i = 0; i < edgesArray.size(); i++) {
      edgesArray.get(i).print();
    }
  }
}
