package ai.h2o.targetencoding;

import hex.Interaction;
import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import water.H2O;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static water.TestUtil.*;


/**
 * Benchmark comparing various implementations of interactions.
 * - interactionEncoderTrain: uses the implementation in TargetEncoderHelper: one MRTask encodes the interactions as a Long, and the new numeric column is then converted into categorical.
 * - interactionEncoderScore: uses the same implementation as above, but can reuse the domain computed during the training interaction, saving some time.
 * - interactionEncoderExtendedDomain: tries to use the same interaction encoding approach as in TargetEncoderHelper but in one single MRTask, which can require to compute a very large domain.
 * - interactionLegacy uses legacy `Interaction/CreateInteractions` class.
 * 
 * results:
 * - interactionEncoderTrain is only slightly slower that interactionEncoderExtendedDomain for small interactions, 
 *   showing that the additional step converting the numerical column to categorical comes at a low cost.
 *   For interactions with 4, 5 columns or more, it can even be considerably faster as the extended domain can be very large in this case.
 * - interactionEncoderScore is faster than interactionEncoderTrain and interactionEncoderExtendedDomain.
 * - interactionEncoder is up to 5x faster than interactionLegacy (on the 95th percentile).
 * 
 * using single shot time (3 warmups, 1000 iterations):
 * Benchmark                                                 (interactionSize)  Mode   Cnt   Score    Error  Units
 * CreateInteractionsBench.interactionEncoderExtendedDomain                  2    ss  1000   1.335 ±  0.189  ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain                  3    ss  1000   1.244 ±  0.153  ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain                  4    ss  1000   2.384 ±  1.473  ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain                  5    ss  1000  16.015 ± 17.151  ms/op
 * CreateInteractionsBench.interactionEncoderScore                           2    ss  1000   1.160 ±  0.162  ms/op
 * CreateInteractionsBench.interactionEncoderScore                           3    ss  1000   1.318 ±  0.144  ms/op
 * CreateInteractionsBench.interactionEncoderScore                           4    ss  1000   1.651 ±  0.196  ms/op
 * CreateInteractionsBench.interactionEncoderScore                           5    ss  1000   1.477 ±  0.182  ms/op
 * CreateInteractionsBench.interactionEncoderTrain                           2    ss  1000   1.902 ±  0.221  ms/op
 * CreateInteractionsBench.interactionEncoderTrain                           3    ss  1000   1.608 ±  0.154  ms/op
 * CreateInteractionsBench.interactionEncoderTrain                           4    ss  1000   2.344 ±  0.696  ms/op
 * CreateInteractionsBench.interactionEncoderTrain                           5    ss  1000   2.767 ±  0.798  ms/op
 * CreateInteractionsBench.interactionLegacy                                 2    ss  1000   2.356 ±  0.307  ms/op
 * CreateInteractionsBench.interactionLegacy                                 3    ss  1000   2.890 ±  0.284  ms/op
 * CreateInteractionsBench.interactionLegacy                                 4    ss  1000   7.334 ±  0.582  ms/op
 * CreateInteractionsBench.interactionLegacy                                 5    ss  1000  10.096 ±  0.634  ms/op
 *
 * 
 * using average time (2 warmups, 10 iterations, 1 sec/it):
 * Benchmark                                                 (interactionSize)  Mode  Cnt    Score     Error  Units
 * CreateInteractionsBench.interactionEncoderExtendedDomain                  2  avgt   10    1.206 ±   1.957  ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain                  3  avgt   10    1.379 ±   1.892  ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain                  4  avgt   10   12.220 ±  13.931  ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain                  5  avgt   10  121.151 ± 125.655  ms/op
 * CreateInteractionsBench.interactionEncoderScore                           2  avgt   10    1.122 ±   1.273  ms/op
 * CreateInteractionsBench.interactionEncoderScore                           3  avgt   10    1.643 ±   2.863  ms/op
 * CreateInteractionsBench.interactionEncoderScore                           4  avgt   10    2.438 ±   4.304  ms/op
 * CreateInteractionsBench.interactionEncoderScore                           5  avgt   10    2.339 ±   3.145  ms/op
 * CreateInteractionsBench.interactionEncoderTrain                           2  avgt   10    2.494 ±   3.509  ms/op
 * CreateInteractionsBench.interactionEncoderTrain                           3  avgt   10    2.474 ±   3.545  ms/op
 * CreateInteractionsBench.interactionEncoderTrain                           4  avgt   10    6.390 ±  10.737  ms/op
 * CreateInteractionsBench.interactionEncoderTrain                           5  avgt   10    4.433 ±   5.965  ms/op
 * CreateInteractionsBench.interactionLegacy                                 2  avgt   10    6.174 ±  11.804  ms/op
 * CreateInteractionsBench.interactionLegacy                                 3  avgt   10    9.269 ±  24.904  ms/op
 * CreateInteractionsBench.interactionLegacy                                 4  avgt   10   14.934 ±  29.076  ms/op
 * CreateInteractionsBench.interactionLegacy                                 5  avgt   10   20.093 ±  46.340  ms/op
 * 
 * using sample time (2 warmups, 10 iterations, 1 sec/it):
 * Benchmark                                                                                          (interactionSize)    Mode    Cnt     Score    Error  Units
 * CreateInteractionsBench.interactionEncoderExtendedDomain                                                           2  sample  10816     0.942 ±  0.325  ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain:interactionEncoderExtendedDomain·p0.00                    2  sample            0.398           ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain:interactionEncoderExtendedDomain·p0.50                    2  sample            0.650           ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain:interactionEncoderExtendedDomain·p0.90                    2  sample            0.962           ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain:interactionEncoderExtendedDomain·p0.95                    2  sample            1.348           ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain:interactionEncoderExtendedDomain·p0.99                    2  sample            4.687           ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain:interactionEncoderExtendedDomain·p0.999                   2  sample           35.933           ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain:interactionEncoderExtendedDomain·p0.9999                  2  sample          961.001           ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain:interactionEncoderExtendedDomain·p1.00                    2  sample         1035.993           ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain                                                           3  sample  10490     1.191 ±  0.670  ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain:interactionEncoderExtendedDomain·p0.00                    3  sample            0.480           ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain:interactionEncoderExtendedDomain·p0.50                    3  sample            0.579           ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain:interactionEncoderExtendedDomain·p0.90                    3  sample            0.934           ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain:interactionEncoderExtendedDomain·p0.95                    3  sample            1.109           ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain:interactionEncoderExtendedDomain·p0.99                    3  sample            3.944           ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain:interactionEncoderExtendedDomain·p0.999                   3  sample           76.705           ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain:interactionEncoderExtendedDomain·p0.9999                  3  sample         1530.053           ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain:interactionEncoderExtendedDomain·p1.00                    3  sample         1545.601           ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain                                                           4  sample   3218     7.896 ±  8.630  ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain:interactionEncoderExtendedDomain·p0.00                    4  sample            0.934           ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain:interactionEncoderExtendedDomain·p0.50                    4  sample            1.294           ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain:interactionEncoderExtendedDomain·p0.90                    4  sample            1.675           ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain:interactionEncoderExtendedDomain·p0.95                    4  sample            1.841           ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain:interactionEncoderExtendedDomain·p0.99                    4  sample            2.566           ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain:interactionEncoderExtendedDomain·p0.999                   4  sample         3664.106           ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain:interactionEncoderExtendedDomain·p0.9999                  4  sample         3829.400           ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain:interactionEncoderExtendedDomain·p1.00                    4  sample         3829.400           ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain                                                           5  sample    495    65.897 ± 68.632  ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain:interactionEncoderExtendedDomain·p0.00                    5  sample            3.760           ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain:interactionEncoderExtendedDomain·p0.50                    5  sample            4.145           ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain:interactionEncoderExtendedDomain·p0.90                    5  sample            5.300           ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain:interactionEncoderExtendedDomain·p0.95                    5  sample            5.523           ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain:interactionEncoderExtendedDomain·p0.99                    5  sample         3269.376           ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain:interactionEncoderExtendedDomain·p0.999                   5  sample         5066.719           ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain:interactionEncoderExtendedDomain·p0.9999                  5  sample         5066.719           ms/op
 * CreateInteractionsBench.interactionEncoderExtendedDomain:interactionEncoderExtendedDomain·p1.00                    5  sample         5066.719           ms/op
 * CreateInteractionsBench.interactionEncoderScore                                                                    2  sample  10240     1.000 ±  0.350  ms/op
 * CreateInteractionsBench.interactionEncoderScore:interactionEncoderScore·p0.00                                      2  sample            0.480           ms/op
 * CreateInteractionsBench.interactionEncoderScore:interactionEncoderScore·p0.50                                      2  sample            0.610           ms/op
 * CreateInteractionsBench.interactionEncoderScore:interactionEncoderScore·p0.90                                      2  sample            1.026           ms/op
 * CreateInteractionsBench.interactionEncoderScore:interactionEncoderScore·p0.95                                      2  sample            1.221           ms/op
 * CreateInteractionsBench.interactionEncoderScore:interactionEncoderScore·p0.99                                      2  sample            5.159           ms/op
 * CreateInteractionsBench.interactionEncoderScore:interactionEncoderScore·p0.999                                     2  sample           57.785           ms/op
 * CreateInteractionsBench.interactionEncoderScore:interactionEncoderScore·p0.9999                                    2  sample          982.880           ms/op
 * CreateInteractionsBench.interactionEncoderScore:interactionEncoderScore·p1.00                                      2  sample          998.244           ms/op
 * CreateInteractionsBench.interactionEncoderScore                                                                    3  sample  11034     0.925 ±  0.271  ms/op
 * CreateInteractionsBench.interactionEncoderScore:interactionEncoderScore·p0.00                                      3  sample            0.508           ms/op
 * CreateInteractionsBench.interactionEncoderScore:interactionEncoderScore·p0.50                                      3  sample            0.617           ms/op
 * CreateInteractionsBench.interactionEncoderScore:interactionEncoderScore·p0.90                                      3  sample            0.862           ms/op
 * CreateInteractionsBench.interactionEncoderScore:interactionEncoderScore·p0.95                                      3  sample            1.039           ms/op
 * CreateInteractionsBench.interactionEncoderScore:interactionEncoderScore·p0.99                                      3  sample            4.050           ms/op
 * CreateInteractionsBench.interactionEncoderScore:interactionEncoderScore·p0.999                                     3  sample           34.630           ms/op
 * CreateInteractionsBench.interactionEncoderScore:interactionEncoderScore·p0.9999                                    3  sample          662.261           ms/op
 * CreateInteractionsBench.interactionEncoderScore:interactionEncoderScore·p1.00                                      3  sample          672.137           ms/op
 * CreateInteractionsBench.interactionEncoderScore                                                                    4  sample   7301     1.414 ±  0.307  ms/op
 * CreateInteractionsBench.interactionEncoderScore:interactionEncoderScore·p0.00                                      4  sample            0.863           ms/op
 * CreateInteractionsBench.interactionEncoderScore:interactionEncoderScore·p0.50                                      4  sample            1.010           ms/op
 * CreateInteractionsBench.interactionEncoderScore:interactionEncoderScore·p0.90                                      4  sample            1.329           ms/op
 * CreateInteractionsBench.interactionEncoderScore:interactionEncoderScore·p0.95                                      4  sample            1.513           ms/op
 * CreateInteractionsBench.interactionEncoderScore:interactionEncoderScore·p0.99                                      4  sample            8.568           ms/op
 * CreateInteractionsBench.interactionEncoderScore:interactionEncoderScore·p0.999                                     4  sample           40.551           ms/op
 * CreateInteractionsBench.interactionEncoderScore:interactionEncoderScore·p0.9999                                    4  sample          614.466           ms/op
 * CreateInteractionsBench.interactionEncoderScore:interactionEncoderScore·p1.00                                      4  sample          614.466           ms/op
 * CreateInteractionsBench.interactionEncoderScore                                                                    5  sample   6210     1.630 ±  0.367  ms/op
 * CreateInteractionsBench.interactionEncoderScore:interactionEncoderScore·p0.00                                      5  sample            0.938           ms/op
 * CreateInteractionsBench.interactionEncoderScore:interactionEncoderScore·p0.50                                      5  sample            1.149           ms/op
 * CreateInteractionsBench.interactionEncoderScore:interactionEncoderScore·p0.90                                      5  sample            1.632           ms/op
 * CreateInteractionsBench.interactionEncoderScore:interactionEncoderScore·p0.95                                      5  sample            2.338           ms/op
 * CreateInteractionsBench.interactionEncoderScore:interactionEncoderScore·p0.99                                      5  sample           10.306           ms/op
 * CreateInteractionsBench.interactionEncoderScore:interactionEncoderScore·p0.999                                     5  sample           53.511           ms/op
 * CreateInteractionsBench.interactionEncoderScore:interactionEncoderScore·p0.9999                                    5  sample          661.651           ms/op
 * CreateInteractionsBench.interactionEncoderScore:interactionEncoderScore·p1.00                                      5  sample          661.651           ms/op
 * CreateInteractionsBench.interactionEncoderTrain                                                                    2  sample   5780     1.888 ±  0.525  ms/op
 * CreateInteractionsBench.interactionEncoderTrain:interactionEncoderTrain·p0.00                                      2  sample            1.031           ms/op
 * CreateInteractionsBench.interactionEncoderTrain:interactionEncoderTrain·p0.50                                      2  sample            1.403           ms/op
 * CreateInteractionsBench.interactionEncoderTrain:interactionEncoderTrain·p0.90                                      2  sample            1.755           ms/op
 * CreateInteractionsBench.interactionEncoderTrain:interactionEncoderTrain·p0.95                                      2  sample            1.954           ms/op
 * CreateInteractionsBench.interactionEncoderTrain:interactionEncoderTrain·p0.99                                      2  sample            8.736           ms/op
 * CreateInteractionsBench.interactionEncoderTrain:interactionEncoderTrain·p0.999                                     2  sample           45.595           ms/op
 * CreateInteractionsBench.interactionEncoderTrain:interactionEncoderTrain·p0.9999                                    2  sample          892.338           ms/op
 * CreateInteractionsBench.interactionEncoderTrain:interactionEncoderTrain·p1.00                                      2  sample          892.338           ms/op
 * CreateInteractionsBench.interactionEncoderTrain                                                                    3  sample   6573     1.648 ±  0.521  ms/op
 * CreateInteractionsBench.interactionEncoderTrain:interactionEncoderTrain·p0.00                                      3  sample            0.851           ms/op
 * CreateInteractionsBench.interactionEncoderTrain:interactionEncoderTrain·p0.50                                      3  sample            1.039           ms/op
 * CreateInteractionsBench.interactionEncoderTrain:interactionEncoderTrain·p0.90                                      3  sample            1.452           ms/op
 * CreateInteractionsBench.interactionEncoderTrain:interactionEncoderTrain·p0.95                                      3  sample            1.709           ms/op
 * CreateInteractionsBench.interactionEncoderTrain:interactionEncoderTrain·p0.99                                      3  sample           10.380           ms/op
 * CreateInteractionsBench.interactionEncoderTrain:interactionEncoderTrain·p0.999                                     3  sample           75.170           ms/op
 * CreateInteractionsBench.interactionEncoderTrain:interactionEncoderTrain·p0.9999                                    3  sample          986.710           ms/op
 * CreateInteractionsBench.interactionEncoderTrain:interactionEncoderTrain·p1.00                                      3  sample          986.710           ms/op
 * CreateInteractionsBench.interactionEncoderTrain                                                                    4  sample   3888     3.171 ±  1.980  ms/op
 * CreateInteractionsBench.interactionEncoderTrain:interactionEncoderTrain·p0.00                                      4  sample            1.243           ms/op
 * CreateInteractionsBench.interactionEncoderTrain:interactionEncoderTrain·p0.50                                      4  sample            1.923           ms/op
 * CreateInteractionsBench.interactionEncoderTrain:interactionEncoderTrain·p0.90                                      4  sample            2.433           ms/op
 * CreateInteractionsBench.interactionEncoderTrain:interactionEncoderTrain·p0.95                                      4  sample            3.208           ms/op
 * CreateInteractionsBench.interactionEncoderTrain:interactionEncoderTrain·p0.99                                      4  sample           12.423           ms/op
 * CreateInteractionsBench.interactionEncoderTrain:interactionEncoderTrain·p0.999                                     4  sample          129.557           ms/op
 * CreateInteractionsBench.interactionEncoderTrain:interactionEncoderTrain·p0.9999                                    4  sample         2311.062           ms/op
 * CreateInteractionsBench.interactionEncoderTrain:interactionEncoderTrain·p1.00                                      4  sample         2311.062           ms/op
 * CreateInteractionsBench.interactionEncoderTrain                                                                    5  sample   3583     3.434 ±  2.156  ms/op
 * CreateInteractionsBench.interactionEncoderTrain:interactionEncoderTrain·p0.00                                      5  sample            1.290           ms/op
 * CreateInteractionsBench.interactionEncoderTrain:interactionEncoderTrain·p0.50                                      5  sample            2.003           ms/op
 * CreateInteractionsBench.interactionEncoderTrain:interactionEncoderTrain·p0.90                                      5  sample            2.501           ms/op
 * CreateInteractionsBench.interactionEncoderTrain:interactionEncoderTrain·p0.95                                      5  sample            3.086           ms/op
 * CreateInteractionsBench.interactionEncoderTrain:interactionEncoderTrain·p0.99                                      5  sample           30.181           ms/op
 * CreateInteractionsBench.interactionEncoderTrain:interactionEncoderTrain·p0.999                                     5  sample          117.735           ms/op
 * CreateInteractionsBench.interactionEncoderTrain:interactionEncoderTrain·p0.9999                                    5  sample         2319.450           ms/op
 * CreateInteractionsBench.interactionEncoderTrain:interactionEncoderTrain·p1.00                                      5  sample         2319.450           ms/op
 * CreateInteractionsBench.interactionLegacy                                                                          2  sample   3013     3.516 ±  0.765  ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.00                                                  2  sample            1.802           ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.50                                                  2  sample            2.728           ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.90                                                  2  sample            3.232           ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.95                                                  2  sample            3.679           ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.99                                                  2  sample           22.277           ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.999                                                 2  sample           42.656           ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.9999                                                2  sample          542.114           ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p1.00                                                  2  sample          542.114           ms/op
 * CreateInteractionsBench.interactionLegacy                                                                          3  sample   2529     3.996 ±  0.352  ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.00                                                  3  sample            2.662           ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.50                                                  3  sample            3.092           ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.90                                                  3  sample            4.121           ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.95                                                  3  sample            4.526           ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.99                                                  3  sample           29.809           ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.999                                                 3  sample           81.903           ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.9999                                                3  sample           90.702           ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p1.00                                                  3  sample           90.702           ms/op
 * CreateInteractionsBench.interactionLegacy                                                                          4  sample   1057     9.612 ±  1.954  ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.00                                                  4  sample            6.103           ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.50                                                  4  sample            7.561           ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.90                                                  4  sample            8.241           ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.95                                                  4  sample            8.634           ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.99                                                  4  sample          107.560           ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.999                                                 4  sample          442.345           ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.9999                                                4  sample          460.849           ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p1.00                                                  4  sample          460.849           ms/op
 * CreateInteractionsBench.interactionLegacy                                                                          5  sample    916    11.123 ±  1.399  ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.00                                                  5  sample            7.733           ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.50                                                  5  sample            9.339           ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.90                                                  5  sample           10.535           ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.95                                                  5  sample           11.280           ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.99                                                  5  sample           87.531           ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.999                                                 5  sample          154.403           ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p0.9999                                                5  sample          154.403           ms/op
 * CreateInteractionsBench.interactionLegacy:interactionLegacy·p1.00                                                  5  sample          154.403           ms/op
 */

@Fork(value = 1)
@Threads(value = 1)
@Warmup(iterations = 2)
@Measurement(iterations = 10, time = 1)
//@BenchmarkMode(Mode.AverageTime)
@BenchmarkMode(Mode.SampleTime)
//@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class CreateInteractionsBench {
    
    @State(Scope.Benchmark)
    public static class DataPreparation {
        
        Frame fr;
        int[] categoricalColsIdx;
        String[] categoricalColsNames;
        Map<Integer, String[]> interactionDomains = new HashMap<>();
        
        @Param({"2", "3", "4", "5"})
//        @Param({"2"})
        int interactionSize;
        
        @Setup
        public void setUp() {
            stall_till_cloudsize(1);
            fr = parse_test_file("./smalldata/testng/airlines.csv");
            categoricalColsIdx = IntStream.range(0, fr.numCols()).filter(i -> fr.vec(i).isCategorical()).toArray();
            categoricalColsNames = IntStream.of(categoricalColsIdx).mapToObj(i -> fr.names()[i]).toArray(String[]::new);
            for (int i=2; i < 6; i++) {
                int[] interactingCols = Arrays.copyOfRange(categoricalColsIdx, 0, i);
                Vec interact = TargetEncoderHelper.createInteractionColumn(fr, interactingCols, null);
                interactionDomains.put(i, interact.domain());
            }
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
    public void interactionEncoderTrain(DataPreparation data, Blackhole bh) {
        int[] interactingCols = Arrays.copyOfRange(data.categoricalColsIdx, 0, data.interactionSize);
        Vec interact = TargetEncoderHelper.createInteractionColumn(data.fr, interactingCols, null);
        bh.consume(interact);
    }

    @Benchmark
    public void interactionEncoderScore(DataPreparation data, Blackhole bh) {
        int[] interactingCols = Arrays.copyOfRange(data.categoricalColsIdx, 0, data.interactionSize);
        String[] interactionDomain = data.interactionDomains.get(data.interactionSize);
        Vec interact = TargetEncoderHelper.createInteractionColumn(data.fr, interactingCols, interactionDomain);
        bh.consume(interact);
    }

    @Benchmark
    public void interactionEncoderExtendedDomain(DataPreparation data, Blackhole bh) {
        int[] interactingCols = Arrays.copyOfRange(data.categoricalColsIdx, 0, data.interactionSize);
        Vec interact = createInteractionColumnExtendedDomain(data.fr, interactingCols);
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


    /**
     * only advantage of this approach is that everything is done in one single MRTask, but the domain can get huge, as it keeps unused domain values.
     */
    static Vec createInteractionColumnExtendedDomain(Frame fr, int[] interactingColumnsIdx) {
        String[][] interactingDomains = new String[interactingColumnsIdx.length][];
        Vec[] interactingVecs = new Vec[interactingColumnsIdx.length];
        String[][] allDomains = fr.domains();
        for (int i=0; i<interactingColumnsIdx.length; i++) {
            interactingDomains[i] = allDomains[i];
            interactingVecs[i] = fr.vec(interactingColumnsIdx[i]);
        }
        boolean encodeUnseenASNA = true;
        final InteractionsEncoder encoder = new InteractionsEncoder(interactingDomains, encodeUnseenASNA);
        CreateInteractionAsCategoricalTask task = new CreateInteractionAsCategoricalTask(encoder);
        task.doAll(new byte[] {Vec.T_CAT}, interactingVecs);
        String[] domain = CreateInteractionAsCategoricalTask.createInteractionDomain(interactingDomains, encodeUnseenASNA);
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
