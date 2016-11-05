package hex.genmodel.algos.tree;

import java.util.ArrayList;

/**
 * Graph for representing a forest.
 */
public class Graph {
  private ArrayList<Subgraph> subgraphArray = new ArrayList<>();
  private int nextSubgraphNumber = 0;

  public Graph() {
    subgraphArray = new ArrayList<>();
  }

  public Subgraph makeSubgraph(String name) {
    Subgraph sg = new Subgraph(name + " (Subgraph " + nextSubgraphNumber + ")");
    nextSubgraphNumber++;
    subgraphArray.add(sg);
    return sg;
  }

  public void print() {
    System.out.println("------------------------------------------------------------");
    System.out.println("Graph");
    for (int i = 0; i < subgraphArray.size(); i++) {
      subgraphArray.get(i).print();
    }
  }
}
