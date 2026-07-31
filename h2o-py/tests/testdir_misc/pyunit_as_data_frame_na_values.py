#!/usr/bin/env python3
# -*- encoding: utf-8 -*-
"""
End-to-end tests for the 3.46.0.12 ``H2OFrame.as_data_frame()`` NA-handling change:

  * default ``na_values`` preserves literal ``"NA"`` / ``"None"`` / ``"NULL"``
    categorical levels and the resulting NaN count matches the cluster's NA count;
  * passing the legacy list restores the pre-3.46.0.12 coercion;
  * the single-thread pandas path and the multi-thread polars path agree on
    NA recognition;
  * the FutureWarning is deduped per call site and only fires for default calls,
    while still honoring warnings.simplefilter().
"""
import sys

sys.path.insert(1, "../../")
import warnings

import h2o
from h2o.utils.shared_utils import can_use_polars, can_use_pyarrow
from tests import pyunit_utils

LEGACY_NA_VALUES = ["", "NA", "NULL", "NaN", "None", "N/A", "#N/A"]


def _make_frame():
    """4-row frame: a categorical column with literal 'NA'/'None' levels and one real
    NA in the numeric column. (The dict-upload path turns a Python None in a string
    column into a literal '' level rather than an NA, so the real NA lives in `num`.)
    """
    fr = h2o.H2OFrame(
        {"cat": ["NA", "None", "x", "y"], "num": [1, 2, 3, None]},
        column_types=["enum", "numeric"],
    )
    assert fr.nrow == 4
    return fr


def test_default_preserves_literal_na_levels():
    fr = _make_frame()
    df = fr.as_data_frame(na_values=[""])
    cat = df["cat"]
    assert "NA" in set(cat.dropna()), "literal 'NA' level must survive: %r" % (cat.tolist(),)
    assert "None" in set(cat.dropna()), "literal 'None' level must survive: %r" % (cat.tolist(),)
    # NaN count must match the cluster's NA count exactly
    cluster_nas = sum(fr.nacnt())
    pandas_nas = int(df.isna().sum().sum())
    assert pandas_nas == cluster_nas, \
        "pandas NaN count %d != cluster NA count %d" % (pandas_nas, cluster_nas)


def test_default_value_of_na_values_matches_explicit_empty():
    fr = _make_frame()
    with warnings.catch_warnings():
        warnings.simplefilter("ignore")
        df_default = fr.as_data_frame()
    df_explicit = fr.as_data_frame(na_values=[""])
    assert df_default.equals(df_explicit), \
        "default na_values must behave exactly like na_values=['']"


def test_legacy_na_values_restore_old_coercion():
    fr = _make_frame()
    df = fr.as_data_frame(na_values=LEGACY_NA_VALUES)
    cat = df["cat"]
    assert "NA" not in set(cat.dropna()), "legacy list must coerce literal 'NA' to NaN"
    assert "None" not in set(cat.dropna()), "legacy list must coerce literal 'None' to NaN"
    assert int(cat.isna().sum()) == 2, \
        "expected 2 NaNs in cat ('NA' and 'None' coerced); got %r" % (cat.tolist(),)


def test_polars_path_agrees_on_na_recognition():
    if not (can_use_polars() and can_use_pyarrow()):
        print("SKIP: polars/pyarrow not installed — multi-thread path not testable here")
        return
    fr = _make_frame()
    df_single = fr.as_data_frame(na_values=[""])
    df_multi = fr.as_data_frame(use_multi_thread=True, na_values=[""])
    assert df_single.isna().equals(df_multi.isna()), \
        "single-thread and polars paths disagree on NA masks:\n%s\nvs\n%s" \
        % (df_single.isna(), df_multi.isna())
    # And with the legacy list as well
    df_single_legacy = fr.as_data_frame(na_values=LEGACY_NA_VALUES)
    df_multi_legacy = fr.as_data_frame(use_multi_thread=True, na_values=LEGACY_NA_VALUES)
    assert df_single_legacy.isna().equals(df_multi_legacy.isna()), \
        "paths disagree on NA masks for the legacy na_values list"


def _na_future_warnings(caught):
    return [w for w in caught
            if issubclass(w.category, FutureWarning) and "NA-handling" in str(w.message)]


def test_future_warning_deduped_for_default_calls_only():
    fr = _make_frame()
    # The "default" action dedups by (message, category, lineno) via the warnings
    # registry, so repeated default calls from the same call site warn once.
    with warnings.catch_warnings(record=True) as caught:
        warnings.simplefilter("default")
        for _ in range(3):
            fr.as_data_frame()           # default -> deduped to a single warning
        fr.as_data_frame(na_values=[""])  # explicit -> never warns
    future = _na_future_warnings(caught)
    assert len(future) == 1, \
        "expected exactly one NA-handling FutureWarning; got %d: %r" \
        % (len(future), [str(w.message) for w in future])


def test_future_warning_is_suppressible_via_filter():
    fr = _make_frame()
    # Unlike a module-global latch, the warning honors warnings.simplefilter().
    with warnings.catch_warnings(record=True) as caught:
        warnings.simplefilter("ignore")
        fr.as_data_frame()
    assert _na_future_warnings(caught) == [], \
        "simplefilter('ignore') must suppress the NA-handling FutureWarning"


def test_future_warning_fires_every_call_under_always_filter():
    fr = _make_frame()
    # simplefilter("always") means "every time": the per-call-site dedup must NOT
    # swallow repeated warnings from the same line, or the warnings contract is broken.
    with warnings.catch_warnings(record=True) as caught:
        warnings.simplefilter("always")
        for _ in range(3):
            fr.as_data_frame()           # same call site each iteration
    future = _na_future_warnings(caught)
    assert len(future) == 3, \
        "simplefilter('always') must fire on every as_data_frame() call; got %d: %r" \
        % (len(future), [str(w.message) for w in future])


def as_data_frame_na_values_suite():
    test_default_preserves_literal_na_levels()
    test_default_value_of_na_values_matches_explicit_empty()
    test_legacy_na_values_restore_old_coercion()
    test_polars_path_agrees_on_na_recognition()
    test_future_warning_deduped_for_default_calls_only()
    test_future_warning_is_suppressible_via_filter()
    test_future_warning_fires_every_call_under_always_filter()


if __name__ == "__main__":
    pyunit_utils.standalone_test(as_data_frame_na_values_suite)
else:
    as_data_frame_na_values_suite()
