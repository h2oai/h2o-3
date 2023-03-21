package hex.mojopipeline;

import org.junit.*;
import water.*;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Assembly;
import water.rapids.transforms.H2OBinaryOp;
import water.rapids.transforms.H2OColOp;
import water.rapids.transforms.H2OColSelect;
import water.rapids.transforms.Transform;
import java.io.IOException;

public class H2OAssemblyToMojoPipelineConversionTest extends TestUtil {

    @BeforeClass() public static void setup() {
        stall_till_cloudsize(1);
    }
    
    Frame createTestingFrame() {
        Frame result = new Frame(
            new String[]{"a", "b", "c", "d", "e"},
            new Vec[]{
                Vec.makeRepSeq(100, 3), 
                Vec.makeRepSeq(100, 5),
                Vec.makeRepSeq(100, 7),
                Vec.makeRepSeq(100, 9),
                Vec.makeRepSeq(100, 11) 
            }
        );
        result._key = Key.make("dummy");
        DKV.put(result);
        Scope.track(result);
        return result;
    }
    
    @Test
    public void testConversionOnH2OColSelect() throws IOException {
        try {
            Scope.enter();
            Transform[] steps = new Transform[]{
                new H2OColSelect("select", "(cols_py dummy ['b', 'd'])", false, null)
            };
            
            Assembly assembly = new Assembly(Key.make(), steps);

            Frame frame = createTestingFrame();
            Frame expected = frame.subframe(new String[]{"b", "d"});
            
            assembly.fit(frame);
            MojoPipeline mojoPipeline = H2OAssemblyToMojoPipelineConverter.convert(assembly);
            Frame result = mojoPipeline.transform(frame, true);

            assertFrameEquals( expected, result, 1e-6);
        } finally {
            Scope.exit();
        }
        
    }

    @Test
    public void testConversionOnH2OColOperationWithUnaryMathFunction() throws IOException {
        try {
            Scope.enter();
            Frame frame = createTestingFrame();
            Transform[] steps = new Transform[]{
                    new H2OColOp("cos", "(cos (cols_py dummy 'c'))", false, new String[]{"newCol"}),
            };

            Assembly assembly = new Assembly(Key.make(), steps);
            Frame expected = assembly.fit(frame.clone());
            
            MojoPipeline mojoPipeline = H2OAssemblyToMojoPipelineConverter.convert(assembly);
            Frame result = mojoPipeline.transform(frame, true);
            
            assertFrameEquals(expected, result , 1e-6);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testConversionOnH2OColOperationWithBinaryMathFunction() throws IOException {
        try {
            Scope.enter();
            Frame frame = createTestingFrame();
            Transform[] steps = new Transform[]{
                new H2OBinaryOp("op1", "(- (cols_py dummy 'c') 5)", false, new String[]{"newCol1"}),
                new H2OBinaryOp("op2", "(- 5 (cols_py dummy 'c'))", false, new String[]{"newCol2"}),
                new H2OBinaryOp("op3", "(- (cols_py dummy 'c') (cols_py dummy 'd') )", false, new String[]{"newCol3"})
            };

            Assembly assembly = new Assembly(Key.make(), steps);
            Frame expected = assembly.fit(frame.clone());
            
            MojoPipeline mojoPipeline = H2OAssemblyToMojoPipelineConverter.convert(assembly);
            Frame result = mojoPipeline.transform(frame, true);

            assertFrameEquals(expected, result , 1e-6);
        } finally {
            Scope.exit();
        }
    }
}
