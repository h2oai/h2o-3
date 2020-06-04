package water.rapids;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Vec;
import water.fvec.Frame;
import water.util.FrameUtils;
import water.util.RandomUtils;

import java.util.Random;

import static water.util.RandomUtils.getRNG;

public class ShuffleVec  {
    
    Frame _fr;
    String _col_name;
    
    
    
    public ShuffleVec(Frame fr) { 
        _fr = fr;    
    }
    
    /* create a frame with shuffled columns */
    public /*Frame*/ void ShuffleFrameVecs(int features_no, Frame out){
        Vec v_shuffled [] = new Vec[features_no];
        for (int f_no = 0 ; f_no < features_no ; f_no++){
            v_shuffled [f_no] = ShuffleTask.shuffle(_fr.vec(f_no));
            
        }
    }
    

    public Vec getVecInArray(String col_names ) {
//        d_vals = new FrameUtils.Vec2ArryTsk((int) _fr.numRows()).doAll(_fr.vec(_col_name)).res;
        return ShuffleTask.shuffle(_fr.vec(_col_name));
//        long d = v_shuffled.length();

//        RandomUtils.getRNG()
    }
    public static long seed(int cidx) { return (0xe031e74f321f7e29L + ((long)cidx << 32L)); }

    public static class ShuffleTask extends MRTask<ShuffleTask> {

        @Override public void map(Chunk ic, Chunk oc) {
            if (ic._len==0) return;
            
            Random rand = new Random();
            long rseed = rand.nextLong();
            Random rng = getRNG(rseed);
            for (int row=1; row<ic._len; row++) {
                int j = rng.nextInt(row+1); // inclusive upper bound <0,row>
                if (j!=row) oc.set(row, oc.atd(j));
                oc.set(j, ic.atd(row));
            }
        }

/* 
    dont forget to check !
    
    for (int i = 0; i < n; ++i)
      assert(ArrayUtils.contains(the array, a[i]));
    return result;
     
*/

    
        public static Vec shuffle(Vec ivec) {
        Vec ovec = ivec.makeZero();
        new ShuffleTask().doAll(ivec, ovec).outputFrame();
        return ovec;
        }
        
    }
}

