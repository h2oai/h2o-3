package hex.tree.xgboost.rabit.util;

import water.util.Pair;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Java implementation of ai.h2o.xgboost4j.scala.rabit.util.AssignedRank
 */
public class AssignedRank {
    private int rank;
    private List<Integer> neighbours;
    private Pair<Integer, Integer> ring;
    private int parent;

    public AssignedRank(int rank, List<Integer> neighbours, Pair<Integer, Integer> ring, int parent) {
        this.rank = rank;
        this.neighbours = neighbours;
        this.ring = ring;
        this.parent = parent;
    }

    public ByteBuffer toByteBuffer(int worldSize) {
        ByteBuffer buffer = ByteBuffer.allocate(4 * (neighbours.size() + 6)).order(ByteOrder.nativeOrder());
        buffer.putInt(rank).putInt(parent).putInt(worldSize).putInt(neighbours.size());
        // neighbors in tree structure
        for(Integer n : neighbours) {
            buffer.putInt(n);
        }
        buffer.putInt((ring._1() != -1 && ring._1() != rank) ? ring._1() : -1);
        buffer.putInt((ring._2() != -1 && ring._2() != rank) ? ring._2() : -1);
        buffer.flip();
        return buffer;
    }

}


