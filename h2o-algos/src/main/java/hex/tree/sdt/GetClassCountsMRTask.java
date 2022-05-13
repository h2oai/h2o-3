package hex.tree.sdt;

import water.MRTask;
import water.fvec.Chunk;

public class GetClassCountsMRTask extends MRTask<GetClassCountsMRTask> {
    int count0;
    int count1;

    public GetClassCountsMRTask() {
        this.count0 = 0;
        this.count1 = 0;
    }

    @Override
    public void map(Chunk[] cs) {
        int classColumn = cs.length - 1; // the last column
        int numRows = cs[0]._len;
        for (int row = 0; row < numRows; row++) {
//            System.out.println("* " + cs[classColumn].atd(row));
            if (Math.abs(cs[classColumn].atd(row)) <= 0.1) { // todo - not just 0 and 1
                count0++;
            } else {
                count1++;
            }
        }
    }

    @Override
    public void reduce(GetClassCountsMRTask mrt) {
        this.count0 += mrt.count0;
        this.count1 += mrt.count1;
    }
}
