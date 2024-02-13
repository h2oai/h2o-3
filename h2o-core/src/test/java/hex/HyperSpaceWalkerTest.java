package hex;

import hex.grid.HyperSpaceSearchCriteria;
import hex.grid.HyperSpaceWalker;
import hex.grid.SimpleParametersBuilderFactory;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.test.dummy.DummyModelParameters;
import water.util.ReflectionUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

public class HyperSpaceWalkerTest extends TestUtil {
    @BeforeClass public static void stall() { stall_till_cloudsize(1); }

    static public class DummyXGBoostModelParameters extends DummyModelParameters {
      
        private static final DummyXGBoostModelParameters DEFAULTS;

        static {
          try {
            DEFAULTS = DummyXGBoostModelParameters.class.newInstance();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        }
      
        public int _max_depth;
        public double _min_rows;
        public double _sample_rate;
        public double _col_sample_rate;
        public double _col_sample_rate_per_tree;
        public String _booster;
        public float _reg_lambda;
        public float _reg_alpha;
        public float _scale_pos_weight;
        public float _max_delta_step;

      @Override
      public Object getParameterDefaultValue(String name) {
        // tricking the default logic here as this parameters class is not properly registered, so we can't obtain the defaults the usual way.
        return ReflectionUtils.getFieldValue(DEFAULTS, name);
      }
    }


    @Test
    public void testRandomDiscreteValueWalkerFinishes() {
        Map<String, Object[]> searchParams = new HashMap<>();
        searchParams.put("_max_depth", new Integer[]{3, 6, 9, 12, 15});
        searchParams.put("_min_rows", new Double[]{1.0, 3.0, 5.0, 10.0, 15.0, 20.0});
        searchParams.put("_sample_rate", new Double[]{0.6, 0.8, 1.0});
        searchParams.put("_col_sample_rate", new Double[]{0.6, 0.8, 1.0});
        searchParams.put("_col_sample_rate_per_tree", new Double[]{0.7, 0.8, 0.9, 1.0});
        searchParams.put("_booster", new String[]{
                "XGBoostParameters.Booster.gbtree",
                "XGBoostParameters.Booster.gbtree",
                "XGBoostParameters.Booster.dart"
        });
        searchParams.put("_reg_lambda", new Float[]{0.001f, 0.01f, 0.1f, 1f, 10f, 100f});
        searchParams.put("_reg_alpha", new Float[]{0.001f, 0.01f, 0.1f, 0.5f, 1f});
        searchParams.put("_scale_pos_weight", new Float[]{1.f, 30f, 5f});
        searchParams.put("_max_delta_step", new Float[]{0f, 5f, 10f});

        HyperSpaceWalker.RandomDiscreteValueWalker rdvw = new HyperSpaceWalker.RandomDiscreteValueWalker<>(new DummyXGBoostModelParameters(),
                searchParams, new SimpleParametersBuilderFactory<>(), new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria());
        HyperSpaceWalker.HyperSpaceIterator hsi = rdvw.iterator();
        try {
            while (hsi.hasNext()) {
                hsi.nextModelParameters();
            }
        } catch (NoSuchElementException e) {
            // pass as it is expected when there are other possible configs but there is a hash collision or
            // there were no new parameter configuration found within Math.max(1e4, _maxHyperSpaceSize) iterations
        }
        assert true;
    }
}
