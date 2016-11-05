package hex.genmodel.algos.tree;

/**
 * Graph Edge class.
 */
public class Edge {
  private final Node parent;
  private final Node child;
  private boolean isLeftEdge;
  private boolean isRightEdge;

  public Edge(Node p, Node c) {
    parent = p;
    child = c;
  }

  void setIsLeftEdge() {
    isLeftEdge = true;
  }

  void setIsRightEdge() {
    isRightEdge = true;
  }

  void print() {
    String edge;
    if (isLeftEdge) {
      edge = " ---left---> ";
    }
    else if (isRightEdge) {
      edge = " ---right--> ";
    }
    else {
      throw new RuntimeException("bad edge");
    }

    System.out.println("        " + parent.getName() + edge + child.getName());
  }
}
