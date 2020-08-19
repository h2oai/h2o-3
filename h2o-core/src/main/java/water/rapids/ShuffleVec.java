package water.rapids;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.fvec.Frame;
import water.parser.BufferedString;
import water.util.FrameUtils;
import water.util.RandomUtils;

import java.util.Random;

import static water.util.RandomUtils.getRNG;

public class ShuffleVec {

    public static long seed(int cidx) {
        return (0xe031e74f321f7e29L + ((long) cidx << 32L));
    }

    public static class ShuffleTask extends MRTask<ShuffleTask> {

        @Override
        public void map(Chunk ic, Chunk oc) {
            if (ic._len == 0) return;

            Random rand = new Random();
            long rseed = rand.nextLong();
            Random rng = getRNG(rseed);
            for (int row = 1; row < ic._len; row++) {
                int j = rng.nextInt(row + 1); // inclusive upper bound <0,row>
                if (j != row) oc.set(row, oc.atd(j));
                oc.set(j, ic.atd(row));
            }
        }

        public static Vec shuffle(Vec ivec) {
            Vec ovec = Vec.makeZero(ivec.length(), ivec.get_type());
            switch (ivec.get_type()) {
                case Vec.T_STR:
                    new ShuffleTaskString().doAll(ivec, ovec).outputFrame();
                    break;
                case Vec.T_NUM:
                    new ShuffleTask().doAll(ivec, ovec).outputFrame();
                    break;
                default:
                    try{
//                    new ShuffleTask().doAll(ivec, ovec).outputFrame();
                        new ShuffleTask().doAll(ivec).outputFrame();
                    } catch (Exception e){
                        throw new IllegalArgumentException("vec type not supported yet");
                    }
            }
            return ovec;
        }

        public static class ShuffleTaskString extends MRTask<ShuffleTaskString> {

            @Override
            public void map(Chunk ic, NewChunk oc) { //consider NewChunk oc
//                if (cs[i] instanceof CStrChunk)  // <- try dis w/ NewChunk
                Random rand = new Random();
                long rseed = rand.nextLong();
                Random rng = getRNG(rseed);
                BufferedString tmpStr = new BufferedString();
                for (int row = 1; row < ic._len; row++) {
                    int j = rng.nextInt(row + 1); // inclusive upper bound <0,row>
                    String _tmpStr = ic.stringAt(j);
                    if (j != row) oc.setAny(row, _tmpStr);
                    oc.setAny(j, ic.stringAt(row));
                }
            }
        }
    }

}
