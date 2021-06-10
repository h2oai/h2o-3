package ai.h2o.automl;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.Key;
import water.Keyed;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static water.TestUtil.parseTestFile;


@CloudSize(1)
@RunWith(H2ORunner.class)
public class AutoMLConcurrencyTest {
    
    private List<Keyed> cleanMe = new ArrayList<>();
    
    @After
    public void cleanUp() {
        for (Keyed k : cleanMe) k.remove();
        cleanMe.clear();
    }
    
    @Test
    public void test_several_instances_can_be_run_in_parallel() {
        int nThreads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(nThreads);

        Frame fr = parseTestFile("./smalldata/prostate/prostate.csv");
        cleanMe.add(fr);
        String target = "CAPSULE";
        
        Stream<Supplier<AutoML>> amlRunners = IntStream.range(0, 5)
                .mapToObj(i -> {  // we need distinct objects, if we just clone, then they will share the same project name and therefore leaderboard.
                    AutoMLBuildSpec spec = new AutoMLBuildSpec();
                    spec.input_spec.training_frame = fr._key;
                    spec.input_spec.response_column = target;
                    spec.build_control.nfolds = 0;
                    spec.build_control.stopping_criteria.set_max_models(3);
                    spec.build_control.stopping_criteria.set_seed(42);
                    return spec;
                })
                .map(spec -> () -> {
                    AutoML aml = AutoML.startAutoML(spec);
                    cleanMe.add(aml);
                    aml.get();
                    return aml;
                });

        @SuppressWarnings("unchecked")
        CompletableFuture<AutoML>[] futures = amlRunners
                .map(call -> CompletableFuture.supplyAsync(call, executor))
                .toArray(CompletableFuture[]::new);
        
        CompletableFuture.allOf(futures).join();
        
        List<AutoML> amls = Arrays.stream(futures).map(CompletableFuture::join).collect(Collectors.toList());
        
        assertEquals(5, amls.stream().map(AutoML::projectName).distinct().count());
        assertEquals(15, amls.stream().flatMap(aml -> Arrays.stream(aml.leaderboard().getModelKeys()))
                                      .map(Key::toString)
                                      .distinct().count());
        
    }
}
