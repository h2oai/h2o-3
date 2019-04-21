package hex.genmodel.algos.tree;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.Assert.*;

public class SharedTreeMojoModelTest {

  @Test
  public void testPubdev5818ScoreStump() {
    byte[] tree = new byte[7];
    ByteBuffer bb = ByteBuffer.wrap(tree, 0, tree.length).order(ByteOrder.nativeOrder());
    bb.put((byte) 0);
    bb.putChar((char) 65535);
    bb.putFloat(4.2f);

    // Root Node Prediction
    final double score = SharedTreeMojoModel.scoreTree(tree, null, false, null);
    assertEquals(4.2f, score, 0.0);

    // Leaf Node Assignment
    final double path = SharedTreeMojoModel.scoreTree(tree, null, true, null);
    assertEquals("", SharedTreeMojoModel.getDecisionPath(path));
  }

}