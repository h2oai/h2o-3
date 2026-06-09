import sys
sys.path.insert(1,"../../../")
import h2o

from lifelines import CoxPHFitter
from lifelines.datasets import load_rossi
from pandas.testing import assert_frame_equal



from tests import pyunit_utils
from h2o.estimators.coxph import H2OCoxProportionalHazardsEstimator


def coxph_concordance_and_baseline():
    rossi = load_rossi()

    without_strata(rossi)
    with_strata(rossi)


def without_strata(rossi):
    check_cox(rossi
              , x=["age", "fin", "race", "wexp", "mar", "paro", "prio"]
              , stratify_by=[]
              , formula="Surv(week, arrest) ~ fin + age + race + wexp + mar + paro + prio"
              )

def with_strata(rossi):
    check_cox(rossi
              , x=["age", "fin", "race", "wexp", "mar", "paro", "prio"]
              , stratify_by=["race", "mar"]
              , formula="Surv(week, arrest) ~ fin + age + wexp + paro + prio + strata(race) + strata(mar)"
              )


def _normalize_col_name(col_name):
    # numpy 2.x stringifies tuples whose elements are numpy scalars as "(np.int64(0), ...)";
    # unwrap each scalar via .item() so the result matches h2o's column naming "(0, ...)".
    if isinstance(col_name, tuple):
        return str(tuple(v.item() if hasattr(v, "item") else v for v in col_name))
    return str(col_name)


# Py3.10 and earlier run against lifelines<0.29 + pandas 1.3.5 + scipy 1.10.1.
# Py3.11+ uses lifelines>=0.29 + scipy 1.17 — measured locally with lifelines
# 0.30.3 + scipy 1.15.3 the actual H2O-vs-lifelines drift on rossi is:
#   hazard:   max_abs=4.6e-11, max_rel=4.2e-9 (no strata) / 5.2e-9 (with strata)
#   survival: max_abs=9.3e-10, max_rel=1.2e-9
# So rtol=1e-6 is ~200× the observed worst case — enough headroom for cross-
# platform FP variance (BLAS thread order, OpenBLAS vs Accelerate) without
# silently absorbing a real H2O regression. The Breslow self-check below is
# the H2O-only invariant; if drift creeps, the diagnostic prints show whether
# it is an H2O-side regression (self-check trips) or lifelines drift only.
# To bisect a future failure: compare _diagnose_baseline_drift output from a
# green build to the failing one — the argmax row pinpoints the observation
# that crossed _BASELINE_RTOL.
_BASELINE_RTOL = 1e-6 if sys.version_info >= (3, 11) else 1e-5

# Breslow identity: S_0(t) = exp(-Σ_{s<=t} h_0(s)). Pure math, must hold to
# machine precision regardless of lifelines drift. Observed locally: max_abs
# ~2e-15 (rossi) / ~8e-15 (shelter). 1e-12 keeps 5 orders of magnitude of
# headroom for future cross-platform FP variance.
_BRESLOW_SELF_CHECK_ATOL = 1e-12


def _diagnose_baseline_drift(name, h2o_df, lib_df):
    """Print max abs/rel diff between H2O and lifelines baselines.

    Information-only: surfaces the drift magnitude in CI logs so a future bisect
    can read the trend across builds without re-running the failing test.
    """
    try:
        import numpy as _np
        a = h2o_df.to_numpy(dtype=float)
        b = lib_df.to_numpy(dtype=float)
        if a.shape != b.shape:
            print("[baseline-diff:%s] shape mismatch h2o=%s lifelines=%s"
                  % (name, a.shape, b.shape))
            return
        abs_diff = _np.abs(a - b)
        max_abs = float(abs_diff.max()) if abs_diff.size else 0.0
        denom = _np.maximum(_np.abs(b), 1e-300)
        max_rel = float((abs_diff / denom).max()) if abs_diff.size else 0.0
        idx = _np.unravel_index(int(abs_diff.argmax()), abs_diff.shape) if abs_diff.size else (0, 0)
        print("[baseline-diff:%s] max_abs=%g max_rel=%g at row=%d col=%d "
              "(h2o=%g lifelines=%g)"
              % (name, max_abs, max_rel, idx[0], idx[1], a[idx], b[idx]))
    except Exception as exc:  # diagnostics must never break the test
        print("[baseline-diff:%s] could not compute: %s" % (name, exc))


def _check_h2o_baseline_self_consistency(name, hazard_h2o_df, survival_h2o_df):
    """Hard check: Breslow identity ``S_0(t) = exp(-cumsum(h_0(t)))``.

    Pure math identity for the Breslow baseline estimator — independent of
    lifelines. Asserted at ``_BRESLOW_SELF_CHECK_ATOL`` (1e-12), with the
    actual magnitude printed to surface the trend in CI logs. Failure here
    indicates a genuine H2O regression in the baseline computation; the
    lifelines comparison cannot disentangle this from cross-library drift.
    """
    import numpy as _np
    hazard = hazard_h2o_df.to_numpy(dtype=float)
    survival = survival_h2o_df.to_numpy(dtype=float)
    assert hazard.shape == survival.shape, \
        "shape mismatch hazard=%s survival=%s" % (hazard.shape, survival.shape)
    derived_survival = _np.exp(-_np.cumsum(hazard, axis=0))
    max_abs = float(_np.abs(derived_survival - survival).max()) if survival.size else 0.0
    print("[breslow-self-check:%s] max|exp(-cumsum(hazard)) - survival| = %g"
          % (name, max_abs))
    assert max_abs < _BRESLOW_SELF_CHECK_ATOL, (
        "Breslow self-check failed on %s: max|exp(-cumsum(hazard)) - survival| = %g "
        "exceeds tolerance %g. This is an H2O-only invariant (no lifelines "
        "dependency); a regression in baseline_hazard_frame or baseline_survival_frame "
        "computation is the most likely cause." % (name, max_abs, _BRESLOW_SELF_CHECK_ATOL)
    )


# expected (the first line with time=0 and values = 0)
# When tests are run at CI wyth Python version 2.x and old lifelines, lifelines result contains one more line then
def fix_py_result_for_older_lifelines(df):
    one_more_line = 50 == len(df.index)
    if one_more_line:
        print("droping first line")
        return df.drop(df.index[0:1]).reset_index( drop=True)
    else:
        return df


def check_cox(rossi, x, stratify_by, formula):
    if stratify_by:
        cph_py = CoxPHFitter(strata=stratify_by)
    else:
        cph_py = CoxPHFitter()

    for col in stratify_by:
        rossi[col] = rossi[col].astype('category')

    cph_py.fit(rossi, duration_col='week', event_col='arrest')
    cph_py.print_summary()
    rossi_h2o = h2o.H2OFrame(rossi)

    for col in stratify_by:
        rossi_h2o[col] = rossi_h2o[col].asfactor()
    
    cph_h2o = H2OCoxProportionalHazardsEstimator(stop_column="week", stratify_by=stratify_by)
    cph_h2o.train(x=x, y="arrest", training_frame=rossi_h2o)
    
    assert cph_h2o.model_id != ""
    assert cph_h2o.model_id != ""
    assert cph_h2o.formula() == formula, "Expected formula to be '" + formula + "' but it was " + cph_h2o.formula()
    
    predH2O = cph_h2o.predict(test_data=rossi_h2o)
    assert len(predH2O) == len(rossi)
    metrics_h2o = cph_h2o.model_performance(rossi_h2o)
    concordance_py = concordance_for_lifelines(cph_py)
    assert abs(concordance_py - metrics_h2o.concordance()) < 0.001
    hazard_h2o_as_pandas = cph_h2o.baseline_hazard_frame.as_data_frame(use_pandas=True)

    hazard_py = cph_py.baseline_hazard_
    
    for col_name in hazard_py.columns:
        hazard_py.rename(columns={col_name: _normalize_col_name(col_name)}, inplace=True)

    hazard_py_reordered_columns = hazard_py.reset_index(drop=True).sort_index(axis=1)
    hazard_h2o_reordered_columns = hazard_h2o_as_pandas.drop('t', axis="columns").reset_index( drop=True).sort_index(axis=1)

    hazard_py_reordered_columns = fix_py_result_for_older_lifelines(hazard_py_reordered_columns)

    print("h2o:")
    print(hazard_h2o_as_pandas.reset_index(drop=True))

    print("lifelines:")
    print(hazard_py_reordered_columns.reset_index(drop=True))

    _diagnose_baseline_drift("hazard", hazard_h2o_reordered_columns, hazard_py_reordered_columns)

    assert_frame_equal(hazard_py_reordered_columns, hazard_h2o_reordered_columns,
                       check_dtype=False, check_index_type=False, check_column_type=False, rtol=_BASELINE_RTOL)
    
    survival_h2o_as_pandas = cph_h2o.baseline_survival_frame.as_data_frame(use_pandas=True)

    survival_py = cph_py.baseline_survival_
    
    for col_name in survival_py.columns:
        survival_py.rename(columns={col_name: _normalize_col_name(col_name)}, inplace=True)

    survival_py_reordered_columns = survival_py.reset_index(drop=True).sort_index(axis=1)
    survival_h2o_reordered_columns = survival_h2o_as_pandas.drop('t', axis="columns").reset_index( drop=True).sort_index(axis=1)

    survival_py_reordered_columns = fix_py_result_for_older_lifelines(survival_py_reordered_columns)
    
    print("h2o:")
    print(survival_h2o_as_pandas.reset_index(drop=True))

    print("lifelines:")
    print(survival_py_reordered_columns.reset_index(drop=True))

    _diagnose_baseline_drift("survival", survival_h2o_reordered_columns, survival_py_reordered_columns)
    _check_h2o_baseline_self_consistency("rossi", hazard_h2o_reordered_columns, survival_h2o_reordered_columns)

    assert_frame_equal(survival_py_reordered_columns, survival_h2o_reordered_columns,
                       check_dtype=False, check_index_type=False, check_column_type=False, rtol=_BASELINE_RTOL)


# There are different API versions for concordance in lifelines library
def concordance_for_lifelines(cph):
    if "_model" in cph.__dict__.keys():
        py_concordance = cph._model._concordance_index_
    elif "_concordance_index_" in cph.__dict__.keys():
        py_concordance = cph._concordance_index_
    else:
        py_concordance = cph._concordance_score_
    return py_concordance


if __name__ == "__main__":
    pyunit_utils.standalone_test(coxph_concordance_and_baseline)
else:
    coxph_concordance_and_baseline()

