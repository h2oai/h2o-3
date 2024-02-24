package hex.pipeline;

import hex.pipeline.DataTransformer.FrameType;
import hex.pipeline.DataTransformerTest.AddRandomColumnTransformer;
import hex.pipeline.DataTransformerTest.FailingAssertionTransformer;
import hex.pipeline.DataTransformerTest.FrameTrackerAsTransformer;
import hex.pipeline.PipelineModel.PipelineParameters;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.Scope;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.ArrayUtils;
import water.util.Pair;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static hex.pipeline.DataTransformerTest.*;
import static org.junit.Assert.*;
import static water.TestUtil.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class TransformerChainTest {

  @Test
  public void test_transformers_are_applied_in_sequential_order() {
    try {
      Scope.enter();
      final Frame fr = Scope.track(dummyFrame());
      FrameTrackerAsTransformer tracker = new FrameTrackerAsTransformer().name("tracker");
      DataTransformer[] dts = new DataTransformer[] {
              tracker,
              new FrameCheckerAsTransformer(f -> {
                assertFalse(ArrayUtils.contains(f.names(), "foo"));
                assertFalse(ArrayUtils.contains(f.names(), "bar"));
              }),
              new AddRandomColumnTransformer("foo").name("add_foo"),
              new FrameCheckerAsTransformer(f -> {
                assertTrue(ArrayUtils.contains(f.names(), "foo"));
                assertFalse(ArrayUtils.contains(f.names(), "bar"));
              }),
              tracker,
              new AddRandomColumnTransformer("bar").name("add_bar"),
              new FrameCheckerAsTransformer(f -> {
                assertTrue(ArrayUtils.contains(f.names(), "foo"));
                assertTrue(ArrayUtils.contains(f.names(), "bar"));
              }),
              tracker,
              new RenameFrameTransformer("dumdum").name("rename_dumdum"),
              tracker,
      };
      TransformerChain chain = Scope.track_generic(new TransformerChain(dts).init());
      chain.prepare(null);
      Frame transformed = chain.transform(fr);
      assertEquals("dumdum", transformed.getKey().toString());
      assertArrayEquals(ArrayUtils.append(fr.names(), new String[]{"foo", "bar"}), transformed.names());
      assertEquals(4, tracker.getTransformations().size());
    } finally {
      Scope.exit();
    }
  }
  
  @Test
  
  public void test_a_failing_transformer_fails_the_entire_chain() {
    try {
      Scope.enter();
      final Frame fr = Scope.track(dummyFrame());
      DataTransformer[] dts = new DataTransformer[] {
              new AddRandomColumnTransformer("foo").name("add_foo"),
              new FailingAssertionTransformer().name("failing_assertion"),
              new AddRandomColumnTransformer("bar").name("add_bar"),
              new RenameFrameTransformer("dumdum").name("rename_dumdum"),
      };
      TransformerChain chain = Scope.track_generic(new TransformerChain(dts).init());
      AssertionError err = assertThrows(AssertionError.class, () -> chain.transform(fr));
      assertEquals("expected", err.getMessage());
    } finally {
      Scope.exit();
    }
  }

  
  @Test
  public void test_transformers_are_applied_in_sequential_order_using_context_tracker() {
    try {
      Scope.enter();
      final Frame fr = Scope.track(dummyFrame());
      DataTransformer[] dts = new DataTransformer[] {
              new AddRandomColumnTransformer("foo").name("add_foo"),
              new AddRandomColumnTransformer("bar").name("add_bar"),
              new RenameFrameTransformer("dumdum").name("rename_dumdum"),
      };
      List<String> dtIds = Arrays.stream(dts).map(DataTransformer::name).collect(Collectors.toList());
      final AtomicInteger dtIdx = new AtomicInteger(0);
      PipelineContext context = new PipelineContext(new PipelineParameters(), new FrameTracker() {
        @Override
        public void apply(Frame transformed, Frame original, FrameType type, PipelineContext context, DataTransformer transformer) {
          assertTrue(dtIds.contains(transformer.name()));
          switch (transformer.name()) {
            case "add_foo":
              assertEquals(0, dtIdx.getAndIncrement());
              assertEquals(fr, original);
              assertArrayEquals(ArrayUtils.append(fr.names(), new String[]{"foo"}), transformed.names());
              break;
            case "add_bar":
              assertEquals(1, dtIdx.getAndIncrement());
              assertArrayEquals(ArrayUtils.append(fr.names(), new String[]{"foo", "bar"}), transformed.names());
              break;
            case "rename_dumdum":
              assertEquals(2, dtIdx.getAndIncrement());
              assertArrayEquals(ArrayUtils.append(fr.names(), new String[]{"foo", "bar"}), transformed.names());
              assertEquals("dumdum", transformed.getKey().toString());
              break;
          }
        }
      }, null);
      
      TransformerChain chain = Scope.track_generic(new TransformerChain(dts).init());
      Frame transformed = chain.transform(fr);
      assertEquals("dumdum", transformed.getKey().toString());
      assertArrayEquals(ArrayUtils.append(fr.names(), new String[]{"foo", "bar"}), transformed.names());
    } finally {
      Scope.exit();
    }
  }
  
  @Test
  public void test_chain_can_be_applied_multiple_times() {
    try {
      Scope.enter();
      final Frame fr1 = Scope.track(dummyFrame());
      final Frame fr2 = Scope.track(oddFrame());
      DataTransformer[] dts = new DataTransformer[] {
              new AddRandomColumnTransformer("foo").name("add_foo"),
              new AddRandomColumnTransformer("bar").name("add_bar"),
              new RenameFrameTransformer("dumdum").name("rename_dumdum"),
      };
      TransformerChain chain = Scope.track_generic(new TransformerChain(dts).init());
      
      Frame tr1 = chain.transform(fr1);
      assertEquals("dumdum", tr1.getKey().toString());
      assertArrayEquals(ArrayUtils.append(fr1.names(), new String[]{"foo", "bar"}), tr1.names());
      
      Frame tr1bis = chain.transform(fr1);
      assertNotSame(tr1, tr1bis);
      assertEquals(tr1.getKey(), tr1bis.getKey());
      assertArrayEquals(tr1.names(), tr1bis.names());
      assertFrameEquals(tr1, tr1bis, 1e-10);
      
      Frame tr2 = chain.transform(fr2);
      assertEquals("dumdum", tr2.getKey().toString());
      assertArrayEquals(ArrayUtils.append(fr2.names(), new String[]{"foo", "bar"}), tr2.names());
    } finally {
      Scope.exit();
    }
  }
  
  
  @Test
  public void test_chain_can_be_finalized_by_completer() {
    try {
      Scope.enter();
      final Frame fr1 = Scope.track(dummyFrame());
      final Frame fr2 = Scope.track(oddFrame());
      DataTransformer[] dts = new DataTransformer[] {
              new AddRandomColumnTransformer("foo").name("add_foo"),
              new AddRandomColumnTransformer("bar").name("add_bar"),
              new RenameFrameTransformer("dumdum").name("rename_dumdum"),
      };
      TransformerChain chain = Scope.track_generic(new TransformerChain(dts).init());
      TransformerChain.Completer<Pair<Long, Long>> dim = new TransformerChain.UnaryCompleter<Pair<Long, Long>>() {
        @Override
        public Pair<Long, Long> apply(Frame frame, PipelineContext context) {
          return new Pair<>(frame.numRows(), (long)frame.numCols());
        }
      };

      assertEquals(new Pair<>(3L, 5L), chain.transform(fr1, FrameType.Test, null, dim));
      assertEquals(new Pair<>(3L, 7L), chain.transform(fr2, FrameType.Test, null, dim));
    } finally {
      Scope.exit();
    }
    
  }
  
  @Test
  public void test_multiple_frames_can_be_transformed_at_once_to_feed_a_completer() {
    try {
      Scope.enter();
      final Frame fr1 = Scope.track(dummyFrame());
      final Frame fr2 = Scope.track(oddFrame());
      DataTransformer[] dts = new DataTransformer[] {
              new AddRandomColumnTransformer("foo").name("add_foo"),
              new AddRandomColumnTransformer("bar").name("add_bar"),
              new RenameFrameTransformer("dumdum").name("rename_dumdum"),
      };
      TransformerChain chain = Scope.track_generic(new TransformerChain(dts).init());

      Frame[] transformed = chain.transform(
              new Frame[] {fr1, fr1, fr2},
              new FrameType[]{FrameType.Test, FrameType.Test, FrameType.Test},
              null,
              (fs, c) -> fs
      );
      Frame tr1 = transformed[0];
      assertEquals("dumdum", tr1.getKey().toString());
      assertArrayEquals(ArrayUtils.append(fr1.names(), new String[]{"foo", "bar"}), tr1.names());

      Frame tr1bis = transformed[1];
      assertNotSame(tr1, tr1bis);
      assertEquals(tr1.getKey(), tr1bis.getKey());
      assertArrayEquals(tr1.names(), tr1bis.names());
      assertFrameEquals(tr1, tr1bis, 1e-10);

      Frame tr2 = transformed[2];
      assertEquals("dumdum", tr2.getKey().toString());
      assertArrayEquals(ArrayUtils.append(fr2.names(), new String[]{"foo", "bar"}), tr2.names());

      Set<String> uniqueCols = chain.transform(
              new Frame[] {fr1, fr1, fr2},
              new FrameType[]{FrameType.Test, FrameType.Test, FrameType.Test},
              null,
              (Frame[] fs, PipelineContext c) -> 
                      Arrays.stream(fs)
                              .flatMap(f -> Arrays.stream(f.names()))
                              .collect(Collectors.toSet())
      );
      assertEquals(new HashSet<>(Arrays.asList("one", "two", "three", "five", "seven", "nine", "foo", "bar")), uniqueCols);
    } finally {
      Scope.exit();
    }
  }
  
  private Frame dummyFrame() {
    return new TestFrameBuilder()
            .withColNames("one", "two", "three")
            .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
            .withDataForCol(0, ard(1, 1, 1))
            .withDataForCol(1, ard(2, 2, 2))
            .withDataForCol(2, ard(3, 3, 3))
            .build();
  }
  private Frame oddFrame() {
    return new TestFrameBuilder()
            .withColNames("one", "three", "five", "seven", "nine")
            .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
            .withDataForCol(0, ard(1, 1, 1))
            .withDataForCol(1, ard(3, 3, 3))
            .withDataForCol(2, ard(5, 5, 5))
            .withDataForCol(3, ard(7, 7, 7))
            .withDataForCol(4, ard(9, 9, 9))
            .build();
  }
  
}
