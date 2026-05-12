package hex.tree.xgboost;

import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;

import java.util.Locale;

import static org.junit.Assert.assertNotNull;


public class XGBoostParamsTest extends TestUtil {

  @BeforeClass
  public static void setUp() {
    TestUtil.stall_till_cloudsize(1);
  }

  /**
   * Different locales may lead to different decimal formatting. XGBoost is only able to parse english locale-like
   * decimal formatting.
   */
  @Test
  public void parametersLocaleIndependent() {
    final Locale originalLocale = Locale.getDefault();
    try {
      Scope.enter();
      Frame trainingFrame = parseTestFile("./smalldata/testng/airlines_train.csv");
      Scope.track(trainingFrame);
      String response = "Distance";

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._ntrees = 1;
      parms._max_depth = 5;
      parms._min_rows = 5000000;
      parms._ignored_columns = new String[]{"fYear", "fMonth", "fDayofMonth", "fDayOfWeek", "UniqueCarrier", "Dest"};
      parms._train = trainingFrame._key;
      parms._response_column = response;

      TestUtil.setLocale(Locale.FRENCH); // French uses different decimal formatting than English
      XGBoostModel model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      assertNotNull(model);
      Scope.track_generic(model);

    } finally {
      TestUtil.setLocale(originalLocale);
      Scope.exit();
    }
  }
}
