package hex.deepwater;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by fmilo on 8/17/16.
 */


public class TensorFrameTest {

    public static TensorFrame sum(TensorFrame a, TensorFrame b){
        return TensorFrame.allocateFloat32();
    }

    @Test
    public void testFrameAllocation(){
        TensorFrame<Float> tf = TensorFrame.allocateFloat32(10);

        TensorFrame<Float> result = sum(tf, tf);

    }
}