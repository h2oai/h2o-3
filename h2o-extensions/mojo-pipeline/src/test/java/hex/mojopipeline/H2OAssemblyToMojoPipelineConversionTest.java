package hex.mojopipeline;

import ai.h2o.mojos.runtime.api.MojoPipelineService;
import ai.h2o.mojos.runtime.api.PipelineLoader;
import ai.h2o.mojos.runtime.api.PipelineLoaderFactory;
import ai.h2o.mojos.runtime.api.backend.MemoryReaderBackend;
import ai.h2o.mojos.runtime.api.backend.ReaderBackend;
import ai.h2o.mojos.runtime.lic.LicenseException;
import org.junit.*;
import water.*;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Assembly;
import water.rapids.transforms.H2OBinaryOp;
import water.rapids.transforms.H2OColOp;
import water.rapids.transforms.H2OColSelect;
import water.rapids.transforms.Transform;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class H2OAssemblyToMojoPipelineConversionTest extends TestUtil {

    @BeforeClass() public static void setup() {
        stall_till_cloudsize(1);
    }

    @Before
    public void checkLicense() {
        Assume.assumeNotNull(System.getenv("DRIVERLESS_AI_LICENSE_FILE"));
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
    public void testPipelineWithInplaceOperations() throws IOException {
        try {
            Scope.enter();
            Frame frame = createTestingFrame();
            Transform[] steps = new Transform[]{
                new H2OColOp("sin", "(sin (cols_py dummy 'c'))", false, new String[]{"newCol1"}),
                new H2OColOp("abs", "(abs (cols_py dummy 'newCol1'))", true, new String[0]),
                new H2OColOp("sqrt", "(sqrt (cols_py dummy 'c'))", false, new String[]{"newCol2"}),
                new H2OColOp("cos", "(cos (cols_py dummy 'newCol1'))", true, new String[0]),
                new H2OBinaryOp("plus", "(+ (cols_py dummy 'newCol1') 1)", true, new String[0]), 
                new H2OColOp("to_string", "(as.character (cols_py dummy 'newCol2'))", true, new String[0]), 
                new H2OBinaryOp("strDist", 
                        "(strDistance (cols_py dummy 'newCol2') (cols_py dummy 'newCol2') 'lv' False)", 
                        true,
                        new String[0]), 
                new H2OColSelect("select", "(cols_py dummy ['newCol1', 'newCol2'])", false, null)
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
    public void testPipelineWithOnlyInplaceOperations() throws IOException {
        try {
            Scope.enter();
            Frame frame = createTestingFrame();
            Frame frameForMojo = frame.deepCopy("forMojo");
            Transform[] steps = new Transform[]{
                    new H2OColOp("exp", "(exp (cols_py dummy 'c'))", true, new String[0]),
                    new H2OColOp("abs", "(abs (cols_py dummy 'c'))", true, new String[0]),
                    new H2OColOp("sqrt", "(sqrt (cols_py dummy 'c'))", true, new String[0]),
                    new H2OBinaryOp("minus", "(- (cols_py dummy 'c') 1)", true, new String[0]),
            };

            Assembly assembly = new Assembly(Key.make(), steps);
            Frame expected = assembly.fit(frame.clone());
            System.out.println(expected.toTwoDimTable());

            MojoPipeline mojoPipeline = H2OAssemblyToMojoPipelineConverter.convert(assembly);
            Frame result = mojoPipeline.transform(frameForMojo, true);
            System.out.println(result.toTwoDimTable());

            assertFrameEquals(expected, result , 1e-6);
        } finally {
            Scope.exit();
        }
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

    @Test
    public void testConversionOnH2OColOperationWithUnaryStringFunction() throws IOException {
        try {
            Scope.enter();
            Frame frame = new Frame(
                new String[]{"s"},
                new Vec[]{
                    Vec.makeVec(new String[] {"fsttta", "TtdaftT", "", "tds"}, Vec.newKey())
                }
            );
            frame._key = Key.make("dummy");
            DKV.put(frame);
            Scope.track(frame);
            Transform[] steps = new Transform[]{
                    new H2OColOp(
                        "replaceall",
                        "(replaceall (cols_py dummy 's') 'tt' 'bccc' True)",
                        false,
                        new String[]{"newCol"}),
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
    public void testConversionOnH2OColOperationWithBinaryStringPropertiesFunction() throws IOException {
        try {
            Scope.enter();
            Scope.enter();
            Frame frame = new Frame(
                    new String[]{"sl", "sr"},
                    new Vec[]{
                            Vec.makeVec(new String[] {"fsttta", "TtdaftT", "", "tds"}, Vec.newKey()),
                            Vec.makeVec(new String[] {"ftta", "TttT", "", "ts"}, Vec.newKey())
                    }
            );
            frame._key = Key.make("dummy");
            DKV.put(frame);
            Scope.track(frame);
            Transform[] steps = new Transform[]{
                    new H2OBinaryOp("op1",
                            "(strDistance (cols_py dummy 'sl') (cols_py dummy 'sr') 'lv' True )", 
                            false,
                            new String[]{"newCol1"}),
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
    public void testConversionOnH2OColOperationWithUnaryTimeFunction() throws IOException {
        try {
            Scope.enter();
            Frame frame = new Frame(
                    new String[]{"dt"},
                    new Vec[]{
                            Vec.makeVec(new String[] {
                                    "15.07.09 1:01:34",
                                    "30.09.09 23:00:43",
                                    "3.01.06 13:30:00",
                                    "30.09.09 23:00:12"},
                            Vec.newKey()),
                    }
            );
            frame._key = Key.make("dummy");
            DKV.put(frame);
            Scope.track(frame);
            Transform[] steps = new Transform[]{
                    new H2OColOp("op_parse_dt",
                            "(as.Date (cols_py dummy 'dt') '%d.%m.%y %H:%M:%S' )",
                            false,
                            new String[]{"i"}),
                    new H2OColOp("col_op_day",
                            "(day (cols_py dummy 'i') )",
                            false,
                            new String[]{"o"}),
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
