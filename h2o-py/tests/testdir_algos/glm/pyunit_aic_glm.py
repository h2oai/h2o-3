import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
import statsmodels.api as sm
import statsmodels.tools as st


def _header(title, level=0):
    if level < 2:
        print("\n")
    print(title)
    print(["=", "-", "~"][min(2, level)] * len(title))


def assert_equal(
    h2o_model, sm_model, coef_tolerance=1e-6, aic_tolerance=1e-6, dk_params=0
):
    """
    :param h2o_model: h2o generalized linear model
    :param sm_model: statsmodels' GLMResults
    :param coef_tolerance: max difference for betas
    :param aic_tolerance: max difference for AIC
    :param dk_params: Used in newer statsmodels to include information about other estimated params such as dispersion
    """
    h2o_aic = h2o_model.aic()
    # We use old version of statsmodels, that don't have the info_criteria function.
    # The old version doesn't have a way to incorporate dispersion estimation.
    sm_aic = float(-2 * sm_model.llf + 2 * (sm_model.df_model + 1 + dk_params))
    coefs = h2o_model.coef()
    offenders = []
    for cn, cv in coefs.items():
        if cn == "Intercept":
            cn = "const"
        if abs(cv - sm_model.params.at[cn]) > coef_tolerance:
            offenders.append(
                f"Coefficient '{cn}' differs by {cv - sm_model.params.at[cn]}; h2o: {cv}; statsmodels: {sm_model.params.at[cn]}"
            )
    assert not offenders, "Coefficients diverge beyond coef_tolerance=%g:\n%s" % (
        coef_tolerance, "\n".join(offenders)
    )
    if abs(h2o_aic - sm_aic) > aic_tolerance:
        message = f"H2O's and statsmodels' AIC estimates don't match by {h2o_aic - sm_aic}. AIC(h2o)={h2o_aic}, AIC(sm)={sm_aic}"
        assert h2o_aic == sm_aic, message
    else:
        print(
            f"Differences between AIC is smaller than threshold: {h2o_aic - sm_aic}. AIC(h2o)={h2o_aic}, AIC(sm)={sm_aic}"
        )


def test_glm_aic_binomial_no_regularization():
    _header("Binomial regression")
    train_h2o = h2o.import_file(
        pyunit_utils.locate("smalldata/logreg/prostate_train.csv")
    )
    y = "CAPSULE"
    train_h2o[y] = train_h2o[y].asfactor()
    train_pd = train_h2o.as_data_frame(use_pandas=True)

    sm_glm_no_reg = sm.GLM(
        train_pd.loc[:, y],
        st.add_constant(train_pd.drop(y, axis=1)),
        family=sm.families.Binomial(),
    ).fit()

    _header("Calculate Likelihood", 1)
    glm_no_reg = H2OGeneralizedLinearEstimator(
        lambda_=0, family="binomial", calc_like=True
    )
    glm_no_reg.train(y=y, training_frame=train_h2o)
    assert_equal(glm_no_reg, sm_glm_no_reg)

    _header("Don't Calculate Likelihood", 1)
    glm_no_reg = H2OGeneralizedLinearEstimator(
        lambda_=0, family="binomial", calc_like=False
    )
    glm_no_reg.train(y=y, training_frame=train_h2o)
    assert_equal(glm_no_reg, sm_glm_no_reg)


def test_glm_aic_gamma_no_regularization():
    _header("Gamma regression")
    train_h2o = h2o.import_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    y = "PSA"
    train_pd = train_h2o.as_data_frame(use_pandas=True)

    _header("Calculate Likelihood", 1)

    sm_glm_no_reg = sm.GLM(
        train_pd.loc[:, y],
        st.add_constant(train_pd.drop(y, axis=1)),
        family=sm.families.Gamma(link=sm.families.links.log()),
    ).fit()

    glm_no_reg = H2OGeneralizedLinearEstimator(
        lambda_=0,
        family="gamma",
        calc_like=True,
        link="log",
        fix_dispersion_parameter=True,
        init_dispersion_parameter=sm_glm_no_reg.scale,
    )
    glm_no_reg.train(y=y, training_frame=train_h2o)
    assert_equal(glm_no_reg, sm_glm_no_reg, coef_tolerance=1e-3, aic_tolerance=1e-4)

    # Without calculating likelihood, H2O can't guess AIC


def test_glm_aic_gaussian_no_regularization():
    _header("Gaussian regression")
    train_h2o = h2o.import_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    y = "PSA"
    train_pd = train_h2o.as_data_frame(use_pandas=True)

    _header("Calculate Likelihood", 1)
    glm_no_reg = H2OGeneralizedLinearEstimator(
        lambda_=0,
        family="gaussian",
        calc_like=True,
        dispersion_parameter_method="deviance",
    )
    glm_no_reg.train(y=y, training_frame=train_h2o)
    sm_glm_no_reg = sm.GLM(
        train_pd.loc[:, y],
        st.add_constant(train_pd.drop(y, axis=1)),
        family=sm.families.Gaussian(),
    ).fit()

    # Statsmodels use different dispersion estimation (divides by n and we divide by (n-p)) for likelihood estimation:
    #             scale = (np.power(self._endog - self.mu, 2) * self._iweights).sum()
    #             scale /= self.model.wnobs
    # This creates difference in dispersion ours ~315 and statsmodels' ~308. When using statmodels' .scale method their
    # dispersion is roughly the same as ours so the difference seems to be limited to the log likelihood estimation.
    assert_equal(glm_no_reg, sm_glm_no_reg, dk_params=1, aic_tolerance=0.2)

    _header("Don't Calculate Likelihood", 1)
    glm_no_reg = H2OGeneralizedLinearEstimator(
        lambda_=0, family="gaussian", calc_like=False
    )
    glm_no_reg.train(y=y, training_frame=train_h2o)
    assert_equal(glm_no_reg, sm_glm_no_reg, dk_params=1, aic_tolerance=1e-5)


def test_glm_aic_negative_binomial_no_regularization():
    _header("Negative Binomial regression")
    train_h2o = h2o.import_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    y = "GLEASON"
    train_pd = train_h2o.as_data_frame(use_pandas=True)

    _header("Estimating theta", 1)
    glm_no_reg_theta_est = H2OGeneralizedLinearEstimator(
        lambda_=0,
        family="negativebinomial",
        calc_like=True,
        link="log",
        dispersion_parameter_method="ml",
        theta=34.8,
        dispersion_learning_rate=1e-5,
        max_iterations_dispersion=100,
    )
    glm_no_reg_theta_est.train(y=y, training_frame=train_h2o)
    theta = glm_no_reg_theta_est._model_json["output"]["dispersion"]

    sm_glm_no_reg = sm.GLM(
        train_pd.loc[:, y],
        st.add_constant(train_pd.drop(y, axis=1)),
        family=sm.families.NegativeBinomial(alpha=1 / theta),
    ).fit()

    assert_equal(
        glm_no_reg_theta_est,
        sm_glm_no_reg,
        coef_tolerance=1e-2,
        aic_tolerance=2e-2,
        dk_params=1,
    )  # dk_params == 1 since we had to estimate the dispersion

    _header(f"Fixed theta = {theta}", 1)
    glm_no_reg = H2OGeneralizedLinearEstimator(
        lambda_=0,
        family="negativebinomial",
        calc_like=True,
        theta=theta,
        link="log",
        fix_dispersion_parameter=True,
    )
    glm_no_reg.train(y=y, training_frame=train_h2o)

    sm_glm_no_reg = sm.GLM(
        train_pd.loc[:, y],
        st.add_constant(train_pd.drop(y, axis=1)),
        family=sm.families.NegativeBinomial(alpha=1 / theta),
    ).fit()

    assert_equal(glm_no_reg, sm_glm_no_reg, coef_tolerance=1e-2, aic_tolerance=2e-2)

    # # Neg. Binomial needs likelihood calculation -> can't guess proper AIC, result might be usable just for comparison between similar models
    # _header("Don't Calculate Likelihood", 2)
    # glm_no_reg = H2OGeneralizedLinearEstimator(lambda_=0, family="negativebinomial", calc_like=False,
    #                                            init_dispersion_parameter=alpha, fix_dispersion_parameter=True)
    # glm_no_reg.train(y=y, training_frame=train_h2o)
    # assert_equal(glm_no_reg, sm_glm_no_reg)


def test_glm_aic_poisson_no_regularization():
    _header("Poisson regression")
    train_h2o = h2o.import_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    y = "GLEASON"
    train_pd = train_h2o.as_data_frame(use_pandas=True)

    _header("Calculate Likelihood", 1)
    glm_no_reg = H2OGeneralizedLinearEstimator(
        lambda_=0, family="poisson", calc_like=True
    )
    glm_no_reg.train(y=y, training_frame=train_h2o)

    sm_glm_no_reg = sm.GLM(
        train_pd.loc[:, y],
        st.add_constant(train_pd.drop(y, axis=1)),
        family=sm.families.Poisson(),
    ).fit()
    assert_equal(glm_no_reg, sm_glm_no_reg)

    _header("Don't Calculate Likelihood", 1)
    glm_no_reg = H2OGeneralizedLinearEstimator(
        lambda_=0, family="poisson", calc_like=False
    )
    glm_no_reg.train(y=y, training_frame=train_h2o)
    assert_equal(glm_no_reg, sm_glm_no_reg)


def test_glm_aic_tweedie_no_regularization():
    _header("Tweedie regression")
    train_h2o = h2o.import_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    y = "PSA"
    train_pd = train_h2o.as_data_frame(use_pandas=True)

    _header("Calculate Likelihood", 1)

    sm_glm_no_reg = sm.GLM(
        train_pd.loc[:, y],
        st.add_constant(train_pd.drop(y, axis=1)),
        family=sm.families.Tweedie(link=sm.families.links.log(), var_power=1.5),
    ).fit()

    # Pin H2O's dispersion to statsmodels' Pearson chi^2 scale so both evaluate the
    # Tweedie log-likelihood at the same phi. Without this, H2O uses ML-estimated phi
    # while statsmodels uses Pearson chi^2 / df_resid, producing different llf at the
    # same fitted mu.
    glm_no_reg = H2OGeneralizedLinearEstimator(
        lambda_=0,
        family="tweedie",
        calc_like=True,
        link="tweedie",
        tweedie_variance_power=1.5,
        tweedie_link_power=0,
        fix_dispersion_parameter=True,
        init_dispersion_parameter=sm_glm_no_reg.scale,
    )
    glm_no_reg.train(y=y, training_frame=train_h2o)
    assert_equal(glm_no_reg, sm_glm_no_reg, coef_tolerance=1e-3, aic_tolerance=1e-4)

    # Sanity check on H2O's ML phi estimator: by definition, the ML phi must
    # produce a loglikelihood at least as high as any other phi on the same
    # data. `glm_no_reg` above was trained with phi pinned to statsmodels'
    # Pearson chi^2 / df_resid scale; if H2O's golden-section ML search
    # regresses (returns a non-optimal phi), `glm_ml_phi.loglikelihood()` will
    # drop below `glm_no_reg.loglikelihood()`. This is a self-consistency
    # check on H2O alone — we do NOT compare phi values across libraries,
    # because statsmodels and H2O use different default estimators (Pearson
    # vs ML) and legitimately produce different phi on small datasets.
    glm_ml_phi = H2OGeneralizedLinearEstimator(
        lambda_=0,
        family="tweedie",
        calc_like=True,
        link="tweedie",
        tweedie_variance_power=1.5,
        tweedie_link_power=0,
        dispersion_parameter_method="ml",
    )
    glm_ml_phi.train(y=y, training_frame=train_h2o)
    ml_llf = glm_ml_phi.loglikelihood()
    pearson_llf = glm_no_reg.loglikelihood()
    # Mixed tolerance: the absolute 1e-6 was too tight for LL values in the
    # 1e3-1e5 range (golden-section search converges to ~eps*(b-a), and any
    # bump in H2O's dispersion_epsilon could trip the bound spuriously).
    # Allow the larger of 1e-6 absolute and 1e-8 relative slack -- still
    # strict enough to catch a real ML-search regression.
    slack = max(1e-6, abs(pearson_llf) * 1e-8)
    assert ml_llf >= pearson_llf - slack, (
        "H2O ML phi=%g yields loglikelihood %g, which is LOWER than the "
        "loglikelihood %g at the pinned Pearson phi=%g (slack=%g). The ML "
        "estimator must maximize likelihood by definition -- the golden-section "
        "search may have regressed." % (
            glm_ml_phi._model_json["output"]["dispersion"], ml_llf,
            pearson_llf, sm_glm_no_reg.scale, slack,
        )
    )

    # Without calculating likelihood, H2O can't guess AIC


def test_glm_aic_control_variables():
    _header("Gaussian regression with control variables")
    train_h2o = h2o.import_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    y = "PSA"
    x = ["CAPSULE", "AGE", "RACE", "DPROS", "DCAPS", "VOL", "GLEASON"]
    x_plus_intercept_plus_disp = len(x) + 1 + 1
    cv = ["RACE", "AGE"]

    _header("No regularization", 1)

    glm_no_reg = H2OGeneralizedLinearEstimator(
        lambda_=0,
        calc_like=True,
        control_variables=cv,
        compute_p_values=True,
        remove_collinear_columns=True,
        seed=12345,
    )
    glm_no_reg.train(x=x, y=y, training_frame=train_h2o)
    aic_diff = abs(
        (-2 * glm_no_reg.loglikelihood() + 2 * x_plus_intercept_plus_disp)
        - glm_no_reg.aic()
    )
    assert aic_diff < 1e-5, f"AIC difference is {aic_diff}"

    ## MOJOs are not supported
    # glm_fn = tempfile.mkdtemp()
    # glm_fn = glm_no_reg.download_mojo(glm_fn)
    # glm_no_reg_mojo = H2OGenericEstimator.from_file(glm_fn)
    # aic_diff = abs(glm_no_reg_mojo.aic() - glm_no_reg.aic())
    # assert aic_diff < 1e-5, f"AIC difference between mojo and glm: {aic_diff}"

    ## MOJOs are not supported
    # aic_mojo = float(glm_no_reg_mojo.model_performance(train_h2o)._metric_json["AIC"])
    # aic_model = float(glm_no_reg._model_json["output"]["training_metrics"]._metric_json["AIC"])
    # aic_diff = abs(aic_mojo - aic_model)
    # assert aic_diff < 1e-5, f"AIC difference between mojo.model_perf and glm: {aic_diff}, aic(model)={aic_model}; aic(mojo)={aic_mojo}; model.aic()={glm_no_reg.aic()}, mojo.aic()={glm_no_reg_mojo.aic()}"

    _header("LASSO", 1)
    glm_cv = H2OGeneralizedLinearEstimator(
        lambda_=1, alpha=1, calc_like=True, control_variables=cv  # LASSO
    )
    glm_cv.train(x=x, y=y, training_frame=train_h2o)

    x_reg_plus_intercept_plus_disp = (
        len([v for k, v in glm_cv.coef().items() if v != 0 or k in cv]) + 1
    )
    aic_diff = abs(
        (-2 * glm_cv.loglikelihood() + 2 * x_reg_plus_intercept_plus_disp)
        - glm_cv.aic()
    )
    assert aic_diff < 1e-5, f"AIC difference is {aic_diff}"

    ## MOJOs are not supported
    # glm_cv_fn = tempfile.mkdtemp()
    # glm_cv_fn = glm_cv.download_mojo(glm_cv_fn)
    # glm_cv_mojo = H2OGenericEstimator.from_file(glm_cv_fn)
    # aic_diff = abs(glm_cv_mojo.aic() - glm_cv.aic())
    # assert aic_diff < 1e-5, f"AIC difference between mojo and glm: {aic_diff}"

    ## MOJOs are unsupported
    # aic_mojo = float(glm_cv_mojo.model_performance(train_h2o)._metric_json["AIC"])
    # aic_diff = abs(aic_mojo - glm_cv.aic())
    # assert aic_diff < 1e-5, f"AIC difference between mojo.model_perf and glm: {aic_diff}"


pyunit_utils.run_tests(
    [
        test_glm_aic_binomial_no_regularization,
        test_glm_aic_gamma_no_regularization,
        test_glm_aic_gaussian_no_regularization,
        test_glm_aic_negative_binomial_no_regularization,
        test_glm_aic_poisson_no_regularization,
        test_glm_aic_tweedie_no_regularization,
        test_glm_aic_control_variables,
    ]
)
