package ai.h2o.targetencoding;

import hex.Interaction;
import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import water.DKV;
import water.H2O;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.VecUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static water.TestUtil.*;


/**
 * Benchmark comparing various implementations of interactions.
 * - interaction2Steps: uses the implementation in TargetEncoderHelper: one MRTask encodes the interactions as a Long, and the new numeric column is then converted into categorical.
 * - interaction1Step: tries to use the same interaction encoding approach as in TargetEncoderHelper but in one single MRTask (although, creates a domain mismatch currently).
 * - interactionLegacy uses legacy `Interaction/CreateInteractions` class.
 * 
 * results:
 * - interaction1Step is only slightly faster than interaction2Steps, showing that the additional step converting the numerical column to categorical comes at a low cost.
 * - interaction2Steps is up to 5x faster than interactionLegacy (on the 95th percentile).
 * 
 * using single shot time (3 warmups, 1000 iterations):
 * Benchmark                                  (interactionSize)  Mode   Cnt   Score   Error  Units
 * CreateInteractionsBench.interaction1Step                   2    ss  1000   1.411 ± 0.192  ms/op
 * CreateInteractionsBench.interaction1Step                   3    ss  1000   1.333 ± 0.186  ms/op
 * CreateInteractionsBench.interaction1Step                   4    ss  1000   2.119 ± 0.705  ms/op
 * CreateInteractionsBench.interaction1Step                   5    ss  1000   2.284 ± 0.789  ms/op
 * CreateInteractionsBench.interaction2Steps                  2    ss  1000   1.935 ± 0.202  ms/op
 * CreateInteractionsBench.interaction2Steps                  3    ss  1000   1.865 ± 0.252  ms/op
 * CreateInteractionsBench.interaction2Steps                  4    ss  1000   2.586 ± 0.655  ms/op
 * CreateInteractionsBench.interaction2Steps                  5    ss  1000   2.584 ± 0.718  ms/op
 * CreateInteractionsBench.interactionLegacy                  2    ss  1000   1.977 ± 0.244  ms/op
 * CreateInteractionsBench.interactionLegacy                  3    ss  1000   3.029 ± 0.250  ms/op
 * CreateInteractionsBench.interactionLegacy                  4    ss  1000   7.620 ± 0.635  ms/op
 * CreateInteractionsBench.interactionLegacy                  5    ss  1000  10.282 ± 0.670  ms/op
 * 
 * using average time (2 warmups, 10 iterations, 1 sec/it):
 * Benchmark                                  (interactionSize)  Mode  Cnt   Score    Error  Units
 * CreateInteractionsBench.interaction1Step                   2  avgt   10   1.190 ±  1.717  ms/op
 * CreateInteractionsBench.interaction1Step                   3  avgt   10   1.730 ±  2.290  ms/op
 * CreateInteractionsBench.interaction1Step                   4  avgt   10   3.303 ±  4.695  ms/op
 * CreateInteractionsBench.interaction1Step                   5  avgt   10   3.928 ±  4.553  ms/op
 * CreateInteractionsBench.interaction2Steps                  2  avgt   10   2.296 ±  2.930  ms/op
 * CreateInteractionsBench.interaction2Steps                  3  avgt   10   2.778 ±  4.011  ms/op
 * CreateInteractionsBench.interaction2Steps                  4  avgt   10   4.042 ±  4.917  ms/op
 * CreateInteractionsBench.interaction2Steps                  5  avgt   10   4.222 ±  6.583  ms/op
 * CreateInteractionsBench.interactionLegacy                  2  avgt   10   5.274 ± 10.126  ms/op
 * CreateInteractionsBench.interactionLegacy                  3  avgt   10  11.288 ± 34.352  ms/op
 * CreateInteractionsBench.interactionLegacy                  4  avgt   10  13.501 ± 23.610  ms/op
 * CreateInteractionsBench.interactionLegacy                  5  avgt   10  20.559 ± 43.168  ms/op
 * 
 * using sample time (2 warmups, 10 iterations, 1 sec/it):
 * Benchmark                                                            (interactionSize)    Mode    Cnt     Score   Error  Units
 * CreateInteractionsBench.interaction1Step                                             2  sample  12939     0.804 ± 0.177  ms/op
 * CreateInteractionsBench.interaction1Step:interaction1Step·p0.00                      2  sample            0.471          ms/op
 * CreateInteractionsBench.interaction1Step:interaction1Step·p0.50                      2  sample            0.539          ms/op
 * CreateInteractionsBench.interaction1Step:interaction1Step·p0.90                      2  sample            0.840          ms/op
 * CreateInteractionsBench.interaction1Step:interaction1Step·p0.95                      2  sample            1.069          ms/op
 * CreateInteractionsBench.interaction1Step:interaction1Step·p0.99                      2  sample            3.825          ms/op
 * CreateInteractionsBench.interaction1Step:interaction1Step·p0.999                     2  sample           33.516          ms/op
 * CreateInteractionsBench.interaction1Step:interaction1Step·p0.9999                    2  sample          495.810          ms/op
 * CreateInteractionsBench.interaction1Step:interaction1Step·p1.00                      2  sample          658.506          ms/op
 * CreateInteractionsBench.interaction1Step                                             3  sample  11067     0.911 ± 0.194  ms/op
 * CreateInteractionsBench.interaction1Step:interaction1Step·p0.00                      3  sample            0.505          ms/op
 * CreateInteractionsBench.interaction1Step:interaction1Step·p0.50                      3  sample            0.594          ms/op
 * CreateInteractionsBench.interaction1Step:interaction1Step·p0.90                      3  sample            0.822          ms/op
 * CreateInteractionsBench.interaction1Step:interaction1Step·p0.95                      3  sample            1.094          ms/op
 * CreateInteractionsBench.interaction1Step:interaction1Step·p0.99                      3  sample            5.830          ms/op
 * CreateInteractionsBench.interaction1Step:interaction1Step·p0.999                     3  sample           29.954          ms/op
 * CreateInteractionsBench.interaction1Step:interaction1Step·p0.9999                    3  sample          549.834          ms/op
 * CreateInteractionsBench.interaction1Step:interaction1Step·p1.00                      3  sample          603.980          ms/op
 * CreateInteractionsBench.interaction1Step                                             4  sample   5268     2.319 ± 1.471  ms/op
 * CreateInteractionsBench.interaction1Step:interaction1Step·p0.00                      4  sample            1.035          ms/op
 * CreateInteractionsBench.interaction1Step:interaction1Step·p0.50                      4  sample            1.255          ms/op
 * CreateInteractionsBench.interaction1Step:interaction1Step·p0.90                      4  sample            1.684          ms/op
 * CreateInteractionsBench.interaction1Step:interaction1Step·p0.95                      4  sample            3.410          ms/op
 * CreateInteractionsBench.interaction1Step:interaction1Step·p0.99                      4  sample           11.102          ms/op
 * CreateInteractionsBench.interaction1Step:interaction1Step·p0.999                     4  sample           92.215          ms/op
 * CreateInteractionsBench.interaction1Step:interaction1Step·p0.9999                    4  sample         2327.839          ms/op
 * CreateInteractionsBench.interaction1Step:interaction1Step·p1.00                      4  sample         2327.839          ms/op
 * CreateInteractionsBench.interaction1Step                                             5  sample   4502     2.770 ± 1.916  ms/op
 * CreateInteractionsBench.interaction1Step:interaction1Step·p0.00                      5  sample            1.128          ms/op
 * CreateInteractionsBench.interaction1Step:interaction1Step·p0.50                      5  sample            1.331          ms/op
 * CreateInteractionsBench.interaction1Step:interaction1Step·p0.90                      5  sample            1.731          ms/op
 * CreateInteractionsBench.interaction1Step:interaction1Step·p0.95                      5  sample            2.866          ms/op
 * CreateInteractionsBench.interaction1Step:interaction1Step·p0.99                      5  sample            9.273          ms/op
 * CreateInteractionsBench.interaction1Step:interaction1Step·p0.999                     5  sample           90.407          ms/op
 * CreateInteractionsBench.interaction1Step:interaction1Step·p0.9999                    5  sample         2065.695          ms/op
 * CreateInteractionsBench.interaction1Step:interaction1Step·p1.00                      5  sample         2065.695          ms/op
 * CreateInteractionsBench.interaction2Steps                                            2  sample   5622     1.785 ± 0.162  ms/op
 * CreateInteractionsBench.interaction2Steps:interaction2Steps·p0.00                    2  sample            1.083          ms/op
 * CreateInteractionsBench.interaction2Steps:interaction2Steps·p0.50                    2  sample            1.341          ms/op
 * CreateInteractionsBench.interaction2Steps:interaction2Steps·p0.90                    2  sample            1.773          ms/op
 * CreateInteractionsBench.interaction2Steps:interaction2Steps·p0.95                    2  sample            2.021          ms/op
 * CreateInteractionsBench.interaction2Steps:interaction2Steps·p0.99                    2  sample           10.253          ms/op
 * CreateInteractionsBench.interaction2Steps:interaction2Steps·p0.999                   2  sample           73.847          ms/op
 * CreateInteractionsBench.interaction2Steps:interaction2Steps·p0.9999                  2  sample          111.149          ms/op
 * CreateInteractionsBench.interaction2Steps:interaction2Steps·p1.00                    2  sample          111.149          ms/op
 * CreateInteractionsBench.interaction2Steps                                            3  sample   6769     1.588 ± 0.497  ms/op
 * CreateInteractionsBench.interaction2Steps:interaction2Steps·p0.00                    3  sample            0.867          ms/op
 * CreateInteractionsBench.interaction2Steps:interaction2Steps·p0.50                    3  sample            1.057          ms/op
 * CreateInteractionsBench.interaction2Steps:interaction2Steps·p0.90                    3  sample            1.337          ms/op
 * CreateInteractionsBench.interaction2Steps:interaction2Steps·p0.95                    3  sample            1.441          ms/op
 * CreateInteractionsBench.interaction2Steps:interaction2Steps·p0.99                    3  sample            9.753          ms/op
 * CreateInteractionsBench.interaction2Steps:interaction2Steps·p0.999                   3  sample           80.410          ms/op
 * CreateInteractionsBench.interaction2Steps:interaction2Steps·p0.9999                  3  sample          959.447          ms/op
 * CreateInteractionsBench.interaction2Steps:interaction2Steps·p1.00                    3  sample          959.447          ms/op
 * CreateInteractionsBench.interaction2Steps                                            4  sample   3644     3.288 ± 2.016  ms/op
 * CreateInteractionsBench.interaction2Steps:interaction2Steps·p0.00                    4  sample            1.370          ms/op
 * CreateInteractionsBench.interaction2Steps:interaction2Steps·p0.50                    4  sample            1.982          ms/op
 * CreateInteractionsBench.interaction2Steps:interaction2Steps·p0.90                    4  sample            2.521          ms/op
 * CreateInteractionsBench.interaction2Steps:interaction2Steps·p0.95                    4  sample            3.502          ms/op
 * CreateInteractionsBench.interaction2Steps:interaction2Steps·p0.99                    4  sample           21.112          ms/op
 * CreateInteractionsBench.interaction2Steps:interaction2Steps·p0.999                   4  sample          111.471          ms/op
 * CreateInteractionsBench.interaction2Steps:interaction2Steps·p0.9999                  4  sample         2206.204          ms/op
 * CreateInteractionsBench.interaction2Steps:interaction2Steps·p1.00                    4  sample         2206.204          ms/op
 * CreateInteractionsBench.interaction2Steps                                            5  sample   3314     3.236 ± 1.212  ms/op
 * CreateInteractionsBench.interaction2Steps:interaction2Steps·p0.00                    5  sample            1.475          ms/op
 * CreateInteractionsBench.interaction2Steps:interaction2Steps·p0.50                    5  sample            2.216          ms/op
 * CreateInteractionsBench.interaction2Steps:interaction2Steps·p0.90                    5  sample            2.634          ms/op
 * CreateInteractionsBench.interaction2Steps:interaction2Steps·p0.95                    5  sample            3.046          ms/op
 * CreateInteractionsBench.interaction2Steps:interaction2Steps·p0.99                    5  sample           24.458          ms/op
 * CreateInteractionsBench.interaction2Steps:interaction2Steps·p0.999                   5  sample           99.033          ms/op
 * CreateInteractionsBench.interaction2Steps:interaction2Steps·p0.9999                  5  sample         1184.891          ms/op
 * CreateInteractionsBench.interaction2Steps:interaction2Steps·p1.00                    5  sample         1184.891          ms/op
 * CreateInteractionsBench.interactionLegacy                                            2  sample   3237     3.116 ± 0.206  ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.00                    2  sample            2.005          ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.50                    2  sample            2.671          ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.90                    2  sample            3.203          ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.95                    2  sample            3.512          ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.99                    2  sample           27.127          ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.999                   2  sample           40.389          ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.9999                  2  sample           45.023          ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p1.00                    2  sample           45.023          ms/op
 * CreateInteractionsBench.interactionLegacy                                            3  sample   2765     3.649 ± 0.874  ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.00                    3  sample            2.175          ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.50                    3  sample            3.056          ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.90                    3  sample            3.494          ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.95                    3  sample            3.656          ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.99                    3  sample           11.480          ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.999                   3  sample          176.543          ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.9999                  3  sample          609.223          ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p1.00                    3  sample          609.223          ms/op
 * CreateInteractionsBench.interactionLegacy                                            4  sample   1005    10.242 ± 1.816  ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.00                    4  sample            6.988          ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.50                    4  sample            8.192          ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.90                    4  sample            9.051          ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.95                    4  sample            9.875          ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.99                    4  sample          103.513          ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.999                   4  sample          376.149          ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.9999                  4  sample          377.487          ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p1.00                    4  sample          377.487          ms/op
 * CreateInteractionsBench.interactionLegacy                                            5  sample    812    13.035 ± 3.006  ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.00                    5  sample            8.503          ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.50                    5  sample           10.060          ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.90                    5  sample           12.075          ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.95                    5  sample           13.835          ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.99                    5  sample          116.578          ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.999                   5  sample          615.514          ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.9999                  5  sample          615.514          ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p1.00                    5  sample          615.514          ms/op
 */

@Fork(value = 1)
@Threads(value = 1)
@Warmup(iterations = 2)
@Measurement(iterations = 1000, time = 1)
//@BenchmarkMode(Mode.AverageTime)
//@BenchmarkMode(Mode.SampleTime)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class CreateInteractionsBench {
    
    @State(Scope.Benchmark)
    public static class DataPreparation {
        
        Frame fr;
        int[] categoricalColsIdx;
        String[] categoricalColsNames;
        
        @Param({"2", "3", "4", "5"})
//        @Param({"2"})
        int interactionSize;
        
        @Setup
        public void setUp() {
            stall_till_cloudsize(1);
            fr = parse_test_file("./smalldata/testng/airlines.csv");
            categoricalColsIdx = IntStream.range(0, fr.numCols()).filter(i -> fr.vec(i).isCategorical()).toArray();
            categoricalColsNames = IntStream.of(categoricalColsIdx).mapToObj(i -> fr.names()[i]).toArray(String[]::new);
        }
        
        @TearDown
        public void teardown() {
            if (fr != null) fr.remove();
            H2O.closeAll();
            H2O.orderlyShutdown();
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("DataPreparation{");
            sb.append("categoricalColsIdx=").append(Arrays.toString(categoricalColsIdx));
            sb.append(", interactionSize=").append(interactionSize);
            sb.append('}');
            return sb.toString();
        }
    }

    @Benchmark
    public void interaction2Steps(DataPreparation data, Blackhole bh) {
        int[] interactingCols = Arrays.copyOfRange(data.categoricalColsIdx, 0, data.interactionSize);
        Vec interact = TargetEncoderHelper.createInteractionColumn(data.fr, interactingCols, true);
        bh.consume(interact);
    }

    @Benchmark
    public void interaction1Step(DataPreparation data, Blackhole bh) {
        int[] interactingCols = Arrays.copyOfRange(data.categoricalColsIdx, 0, data.interactionSize);
        Vec interact = createInteractionColumn1Step(data.fr, interactingCols, true);
        bh.consume(interact);
    }

    @Benchmark
    public void interactionLegacy(DataPreparation data, Blackhole bh) {
        String[] interactingCols = Arrays.copyOfRange(data.categoricalColsNames, 0, data.interactionSize);
        Vec interact = createInteractionColumnLegacy(data.fr, interactingCols);
        bh.consume(interact);
    }
    
    public static void main(String[] args) throws Exception {
        Main.main(args);
    }




    static Vec createInteractionColumnLegacy(Frame fr, String[] names) {
        Interaction interact = Interaction.getInteraction(fr._key,  names, 1000);
        interact._min_occurrence = 1;
        return interact.execImpl(null).get().lastVec();
    }


    static Vec createInteractionColumn1Step(Frame fr, int[] interactingColumnsIdx, boolean encodeUnseenAsNA) {
        String[][] interactingDomains = new String[interactingColumnsIdx.length][];
        Vec[] interactingVecs = new Vec[interactingColumnsIdx.length];
        String[][] allDomains = fr.domains();
        for (int i=0; i<interactingColumnsIdx.length; i++) {
            interactingDomains[i] = allDomains[i];
            interactingVecs[i] = fr.vec(interactingColumnsIdx[i]);
        }
        final InteractionsEncoder encoder = new InteractionsEncoder(interactingDomains, encodeUnseenAsNA);
        CreateInteractionAsCategoricalTask task = new CreateInteractionAsCategoricalTask(encoder);
        task.doAll(new byte[] {Vec.T_CAT}, interactingVecs);
        String[] domain = CreateInteractionAsCategoricalTask.createInteractionDomain(interactingDomains, encodeUnseenAsNA);
//        String[] domain = Arrays.stream(task._domain).mapToObj(Integer::toString).toArray(String[]::new);
        Vec interaction = task.outputFrame(null, null, new String[][] {domain}).lastVec();
//        interaction = VecUtils.remapDomain(domain, interaction);
        return interaction;
    }

    private static class CreateInteractionAsCategoricalTask extends MRTask<CreateInteractionAsCategoricalTask> {
        final InteractionsEncoder _encoder;
        int[] _domain = new int[0];

        public CreateInteractionAsCategoricalTask(InteractionsEncoder encoder) {
            _encoder = encoder;
        }

        @Override
        public void map(Chunk[] cs, NewChunk nc) {
//            Set<Integer> domain = new HashSet<>();
            for (int row=0; row < cs[0].len(); row++) {
                int[] interactingValues = new int[cs.length];
                for (int i=0; i<cs.length; i++) {
                    interactingValues[i] = cs[i].isNA(row) ? -1 : (int)cs[i].at8(row);
                }
                long val = _encoder.encode(interactingValues);
                assert val < Integer.MAX_VALUE;
                if (val < 0) {
                    nc.addNA();
                } else {
                    nc.addCategorical((int)val);
//                    domain.add((int)val);
                }
            }
//            _domain = domain.stream().mapToInt(Integer::intValue).toArray();
        }

//        @Override
//        public void reduce(CreateInteractionAsCategoricalTask mrt) {
//            _domain = Stream.concat(
//                    Arrays.stream(_domain).boxed(),
//                    Arrays.stream(mrt._domain).boxed()
//            ).distinct().sorted().mapToInt(Integer::intValue).toArray();
//        }


        private static String[] createInteractionDomain(String[][] interactingDomains, boolean encodeUnseenAsNA) {
            int card = 1;
            for (String[] domain : interactingDomains)
                card *= (encodeUnseenAsNA ? (domain.length + 1) : (domain.length + 2));  // +1 for potential unseen values, +1 for NAs
            return IntStream.range(0, card).mapToObj(Integer::toString).toArray(String[]::new);
        }

    }

}
