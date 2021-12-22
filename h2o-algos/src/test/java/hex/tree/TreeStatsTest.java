package hex.tree;

import org.junit.Test;

import static org.junit.Assert.*;

public class TreeStatsTest {

  @Test
  public void testMergeWith() {
    TreeStats treeStats1 = new TreeStats();
    treeStats1._min_depth = 3;
    treeStats1._max_depth = 4;
    treeStats1._min_leaves = 0;
    treeStats1._max_leaves = 9;
    treeStats1._byte_size = (byte) 123; 
    treeStats1._num_trees = 2;
    treeStats1._sum_leaves = 5;
    treeStats1._sum_depth = 7;
    
    TreeStats treeStats2 = new TreeStats();
    treeStats2._min_depth = 2;
    treeStats2._max_depth = 5;
    treeStats2._min_leaves = 3;
    treeStats2._max_leaves = 20;
    treeStats2._byte_size = (byte) 111;
    treeStats2._num_trees = 3;
    treeStats2._sum_leaves = 8;
    treeStats2._sum_depth = 13;
    
    treeStats1.mergeWith(treeStats2);
    assertEquals(treeStats1._min_depth, 2);
    assertEquals(treeStats1._max_depth, 5);
    assertEquals(treeStats1._min_leaves, 0);
    assertEquals(treeStats1._max_leaves, 20);
    assertEquals(treeStats1._byte_size, 234);
    assertEquals(treeStats1._num_trees, 5);
    assertEquals(treeStats1._sum_leaves, 13);
    assertEquals(treeStats1._sum_depth, 20);
    assertEquals(treeStats1._mean_depth, 4.0, 1e-1);
    assertEquals(treeStats1._mean_leaves, 2.6, 1e-1);

    treeStats2.mergeWith(treeStats1);
    assertEquals(treeStats2._min_depth, 2);
    assertEquals(treeStats2._max_depth, 5);
    assertEquals(treeStats2._min_leaves, 0);
    assertEquals(treeStats2._max_leaves, 20);
    assertEquals(treeStats2._byte_size, 345);
    assertEquals(treeStats2._num_trees, 8);
    assertEquals(treeStats2._sum_leaves, 21);
    assertEquals(treeStats2._sum_depth, 33);
    assertEquals(treeStats2._mean_depth, 4.125, 1e-3);
    assertEquals(treeStats2._mean_leaves, 2.625, 1e-3);
  }
}
