package hex.schemas;

import hex.coxph.CoxPHModel;
import water.api.API;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;

public class CoxPHModelV3 extends ModelSchemaV3<CoxPHModel,
                                              CoxPHModelV3,
                                              CoxPHModel.CoxPHParameters,
                                              CoxPHV3.CoxPHParametersV3,
                                              CoxPHModel.CoxPHOutput,
                                              CoxPHModelV3.CoxPHModelOutputV3> {

  public static final class CoxPHModelOutputV3 extends ModelOutputSchemaV3<CoxPHModel.CoxPHOutput, CoxPHModelOutputV3> {

    @API(help = "coef_names")
    String[] coef_names;
    @API(help = "coef")
    double[] coef;
    @API(help = "exp_coef")
    double[] exp_coef;
    @API(help = "exp_neg_coef")
    double[] exp_neg_coef;
    @API(help = "se_coef")
    double[] se_coef;
    @API(help = "z_coef")
    double[] z_coef;
    @API(help = "var_coef")
    double[][] var_coef;
    @API(help = "null_loglik")
    double null_loglik;
    @API(help = "loglik")
    double loglik;
    @API(help = "loglik_test")
    double loglik_test;
    @API(help = "wald_test")
    double wald_test;
    @API(help = "score_test")
    double score_test;
    @API(help = "rsq")
    double rsq;
    @API(help = "maxrsq")
    double maxrsq;
    @API(help = "lre")
    double lre;
    @API(help = "iter")
    int iter;
    @API(help = "x_mean_cat")
    double[] x_mean_cat;
    @API(help = "x_mean_num")
    double[] x_mean_num;
    @API(help = "mean_offset")
    double[] mean_offset;
    @API(help = "offset_names")
    String[] offset_names;
    @API(help = "n")
    long n;
    @API(help = "n_missing")
    long n_missing;
    @API(help = "total_event")
    long total_event;
    @API(help = "min_time")
    long min_time;
    @API(help = "max_time")
    long max_time;
    @API(help = "time")
    long[] time;
    @API(help = "n_risk")
    double[] n_risk;
    @API(help = "n_event")
    double[] n_event;
    @API(help = "n_censor")
    double[] n_censor;
    @API(help = "cumhaz_0")
    double[] cumhaz_0;
    @API(help = "var_cumhaz_1")
    double[] var_cumhaz_1;
    @API(help = "var_cumhaz_2")
    double[][] var_cumhaz_2;

  } // CoxPHModelOutputV3

  public CoxPHV3.CoxPHParametersV3 createParametersSchema() { return new CoxPHV3.CoxPHParametersV3(); }
  public CoxPHModelOutputV3 createOutputSchema() { return new CoxPHModelOutputV3(); }

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the impl
  @Override public CoxPHModel createImpl() {
    CoxPHModel.CoxPHParameters parms = parameters.createImpl();
    return new CoxPHModel(model_id.key(), parms, new CoxPHModel.CoxPHOutput(null));
  }
}
