package hex.tfidf;

import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.rapids.ast.prims.mungers.AstGroup;

import java.util.Arrays;

/**
 * Map-Reduce task for computing Term Frequency values for words in given documents.
 */
public class TermFrequency extends MRTask<TermFrequency> {

    @Override
    public void map(Chunk[] cs, NewChunk[] ncs) {
        Frame inputChunk = new Frame(Arrays.stream(cs).map(Chunk::vec).toArray(Vec[]::new));

        AstGroup.AGG[] aggs = new AstGroup.AGG[1];
        aggs[0] = new AstGroup.AGG(AstGroup.FCN.nrow, 1, AstGroup.NAHandling.ALL, -1);

        int[] groupByColumnsNum = new int[]{ 1 };
        int[] groupByColumnsStr = new int[]{ 0, 2 };

        Frame groupedInputChunk = new AstGroup().performGroupingWithAggregations(inputChunk, 
                                                                                 groupByColumnsNum,
                                                                                 groupByColumnsStr, 
                                                                                 aggs).getFrame();

        for (int col = 0; col < groupedInputChunk.numCols(); col++) {
            Vec column = groupedInputChunk.vec(col);
            
            // TODO: More efficient way
            for (int row = 0; row < column.length(); row++)
                if (column.isString())
                    ncs[col].addStr(column.stringAt(row)); 
                else
                    ncs[col].addNum(column.at8(row));
        }

        groupedInputChunk.remove();
    }

    @Override
    public Frame outputFrame() {
        return finalOutputFrame(super.outputFrame(null, null, null));
    }

    @Override
    public Frame outputFrame(String[] names, String[][] domains) {
        return finalOutputFrame(super.outputFrame(null, names, domains));
    }

    @Override
    public Frame outputFrame(Key<Frame> key, String[] names, String[][] domains) {
        return finalOutputFrame(super.outputFrame(key, names, domains));
    }

    /**
     * Constructs output frame with final TF values from the output frame 
     * with partial TF values and <strong>discards the frame with partial TF values</strong>.
     * 
     * @param outFrame  output frame with partial TF values. This frame <strong>is discarded</strong>.
     * 
     * @return  output frame with final TF values.
     */
    private Frame finalOutputFrame(Frame outFrame) {
        AstGroup.AGG[] aggs = new AstGroup.AGG[1];
        aggs[0] = new AstGroup.AGG(AstGroup.FCN.sum, 3, AstGroup.NAHandling.ALL, -1);

        int[] groupByColumnsNum = new int[]{0, 3};
        int[] groupByColumnsStr = new int[]{1, 2};

        Frame groupedOutputFrame = new AstGroup()
                                        .performGroupingWithAggregations(outFrame,
                                                                         groupByColumnsNum,
                                                                         groupByColumnsStr,
                                                                         aggs).getFrame();

        outFrame.remove();

        return groupedOutputFrame;
    }
}
