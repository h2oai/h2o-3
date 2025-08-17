import sys

from h2o.automl import H2OAutoML

sys.path.insert(1, "../../")
from tests import pyunit_utils

import random
import h2o
import numpy as np
import pandas as pd

from itertools import chain, combinations
from collections import defaultdict

try:
    from tqdm.auto import tqdm
except ImportError:

    def tqdm(x, *args, **kwargs):
        return x

from h2o.estimators import *

seed = 6
K = 2
LARGE_K = 5


def prob_to_logit(p):
    return np.log(p / (1 - p))


def logit_to_prob(l):
    return np.exp(l) / (1 + np.exp(l))


def sample(x, k=K):
    x = list(x)
    if len(x) < k:
        return x
    return random.sample(x, k)

def val_eq(a, b):
    if a == b:
        return True
    if str(a) == str(b):
        return True

    try:
        if abs(float(a) - float(b)) < 1e-6:
            return True

        if a != a and b != b:  # both are NaNs
            return True
    except Exception:
        pass
    return False

def remove_all_but(*args):
    keep = {x.key if hasattr(x, "key") else x.frame_id for x in args}
    h2o.remove([key for key in h2o.ls().iloc[:, 0].values if key not in keep])


def test_local_accuracy(
    mod, train, test, link=False, eps=1e-5, output_format="original", output_space=False
):
    if link:
        print("Testing local accuracy in link space...")
    elif not link and output_space:
        print("Testing local accuracy in output space...")
    else:
        print("Testing local accuracy...")
    with h2o.no_progress_block():
        cf = mod.predict_contributions(
            test, background_frame=train, output_format=output_format, output_space=output_space,
            output_per_reference=True
        )
        pf = mod.predict(test)
        col = "Yes" if "Yes" in pf.names else "predict"
        p = pf[col].as_data_frame()[col]
        h2o.remove(pf)
        fr = cf.drop("BackgroundRowIdx").group_by("RowIdx").mean().get_frame()
        tmp = fr.drop("RowIdx").sum(axis=1, return_frame=True)
        mu = tmp.as_data_frame().iloc[:, 0]
        h2o.remove(cf)
        h2o.remove(fr)
        h2o.remove(tmp)
        if link:
            mu = logit_to_prob(mu)

    assert (
        np.abs(p - mu) < eps
    ).any(), f"Failed local accuracy test: {mod.key} on {test.frame_id}. max diff = {np.max(np.abs(p - mu))}, mean diff = {np.mean(np.abs(p - mu))}"


def test_dummy_property(mod, train, test, output_format):
    print("Testing dummy property...")
    train = train.na_omit()  # with NA this test sometimes fails due to pandas converting int to float due to NA presence
    test = test.na_omit()
    contr_h2o = (
        mod.predict_contributions(
            test, background_frame=train, output_format=output_format,
            output_per_reference=True
        )
        .sort(["RowIdx", "BackgroundRowIdx"])
        .drop(["BiasTerm", "RowIdx", "BackgroundRowIdx"])
    )
    contr_df = contr_h2o.as_data_frame()
    h2o.remove(contr_h2o)

    train_df = train.as_data_frame()
    test_df = test.as_data_frame()
    for ts in tqdm(sample(range(test_df.shape[0]), LARGE_K), desc="Test"):
        for tr in sample(range(train_df.shape[0]), LARGE_K):
            for col in contr_df.columns:
                row_in_contr = ts * train.shape[0] + tr
                if col not in train_df.columns:
                    fragments = col.split(".")
                    col_name, cat = [
                        (".".join(fragments[:i]), ".".join(fragments[i:]))
                        for i in range(1, len(fragments))
                        if ".".join(fragments[:i]) in train.columns
                    ][0]

                    if contr_df.loc[row_in_contr, col] != 0:
                        if val_eq(test_df.loc[ts, col_name], train_df.loc[tr, col_name]):
                            print(
                                f"test={test_df.loc[ts, col_name]} != train={train_df.loc[tr, col_name]}: contr={contr_df.loc[row_in_contr, col]}| ts={ts}, tr={tr}"
                            )
                            assert False
                        # not train_df.loc[tr, col_name] != cat and \
                        if (
                            not val_eq(test_df.loc[ts, col_name], cat)
                            and not (
                            cat == "missing(NA)"
                            and (
                                pd.isna(test_df.loc[ts, col_name])
                                or pd.isna(train_df.loc[tr, col_name])
                            )
                        )
                            and not val_eq(train_df.loc[tr, col_name], cat)
                        ):  # TODO: THINK ABOUT THIS MORE (GLM)
                            print(
                                f"Category not used but contributes! col={col_name}; test={test_df.loc[ts, col_name]} != cat={cat}; train={train_df.loc[tr, col_name]}: contr={contr_df.loc[row_in_contr, col]}| ts={ts}, tr={tr} | {cat == 'missing(NA)'} and {pd.isna(test_df.loc[ts, col_name])}"
                            )
                            assert False
                else:
                    if contr_df.loc[row_in_contr, col] != 0:
                        if val_eq(test_df.loc[ts, col], train_df.loc[tr, col]):
                            print(
                                f"test={test_df.loc[ts, col]} != train={train_df.loc[tr, col]}: contr={contr_df.loc[row_in_contr, col]}| ts={ts}, tr={tr}"
                            )
                            assert False


def test_symmetry(mod, train, test, output_format, eps=1e-10):
    """This test does not test the symmetry axiom from shap. It tests whether contributions are same magnitude
    but opposite sign if we switch the background with the foreground."""
    
    
    print("Testing symmetry...")
    contr = (
        mod.predict_contributions(
            test, background_frame=train, output_format=output_format, output_per_reference=True
        )
        .sort(["RowIdx", "BackgroundRowIdx"])
        .as_data_frame()
    )
    contr2 = (
        mod.predict_contributions(
            train, background_frame=test, output_format=output_format, output_per_reference=True
        )
        .sort(["RowIdx", "BackgroundRowIdx"][::-1])
        .as_data_frame()
    )

    test = test.as_data_frame()
    train = train.as_data_frame()

    for row in tqdm(sample(range(contr.shape[0]), LARGE_K), desc="Row"):
        for col in sample(contr.columns, LARGE_K):
            if col in ["BiasTerm", "RowIdx", "BackgroundRowIdx"]:
                continue
            if col not in train.columns:
                fragments = col.split(".")
                col_name, cat = [
                    (".".join(fragments[:i]), ".".join(fragments[i:]))
                    for i in range(1, len(fragments))
                    if ".".join(fragments[:i]) in train.columns
                ][0]

                val = test.loc[row // train.shape[0], col_name]
                if val == "NA" or pd.isna(val):
                    val = "missing(NA)"

                val_bg = train.loc[row % train.shape[0], col_name]
                if val_bg == "NA" or pd.isna(val_bg):
                    val_bg = "missing(NA)"

                if abs(contr.loc[row, col]) > 0:
                    assert val_eq(val, cat) or val_eq(val_bg, cat), f"val = {val}; cat = {cat}; val_bg = {val_bg}"

                if (
                    f"{col_name}.{val}" in contr.columns and  # can be missing in GLM without regularization (moved to intercept)
                    f"{col_name}.{val}" in contr2.columns and
                    f"{col_name}.{val_bg}" in contr2.columns and
                    abs(
                        contr.loc[row, f"{col_name}.{val}"]
                        + contr2.loc[row, f"{col_name}.{val_bg}"]
                    )
                    > eps
                    and abs(
                    contr.loc[row, f"{col_name}.{val}"]
                    + contr2.loc[row, f"{col_name}.{val}"]
                )
                    > eps  # GLM TODO: THINK ABOUT THIS MORE
                ):
                    print(
                        f"row: {row}, col: {col}, col2: {col}, {contr.loc[row, col]} != - {contr2.loc[row, col]}"
                    )
                    print(
                        f"row: {row}, col: {col}, col2: {col_name}.{val_bg}, {contr.loc[row, col_name + '.' + val]} != - {contr2.loc[row, col_name + '.' + val_bg]}"
                    )
                    print(
                        f"row: {row}; RowIdx: {contr.loc[row, 'RowIdx']}, BgRowIdx: {contr.loc[row, 'BackgroundRowIdx']}; RowIdx: {contr2.loc[row, 'RowIdx']}, BgRowIdx: {contr2.loc[row, 'BackgroundRowIdx']};"
                    )
                    assert False
            else:
                if abs(contr.loc[row, col] + contr2.loc[row, col]) > eps:
                    print(
                        f"row: {row}, col: {col}, {contr.loc[row, col]} != - {contr2.loc[row, col]}"
                    )
                    assert False


def powerset(iterable):
    s = list(iterable)
    return list(chain.from_iterable(combinations(s, r) for r in range(len(s) + 1)))


def fact(n):
    if n < 1:
        return 1
    return n * fact(n - 1)


def naiveBSHAP(mod, y, train, test, xrow, brow, link=False):
    x = test[xrow, :].as_data_frame()
    b = train[brow, :].as_data_frame()

    cols = [
        col
        for col in x.columns.values[(x != b).values[0]]
        if col in mod._model_json["output"]["names"]
           and not (pd.isna(x.loc[0, col]) and pd.isna(b.loc[0, col]))
           and col != y
    ]
    pset = powerset(cols)

    df = pd.concat([b for _ in range(len(pset))], ignore_index=True)
    for row in tqdm(range(df.shape[0]), desc="Creating data frame", leave=False):
        for col in pset[row]:
            df.loc[row, col] = x[col].values

    df = h2o.H2OFrame(df, column_types=train.types)

    for i, cat in enumerate(train.isfactor()):
        if cat:
            df[df.columns[i]] = df[df.columns[i]].asfactor()

    results = defaultdict(lambda: 0)
    preds = mod.predict(df).as_data_frame()

    resp = "Yes" if "Yes" in preds.columns else "predict"
    if link is True or link == "logit":
        preds[resp] = prob_to_logit(preds[resp])
    elif link == "log":
        preds[resp] = np.log(preds[resp])
    elif link == "inverse":
        preds[resp] = 1 / (preds[resp])

    evals = list(zip(pset, preds[resp]))

    for c in tqdm(cols, desc="Calculating B-SHAP", leave=False):
        F = len(cols)
        for ec, ev in evals:
            if c in ec:
                S = len(ec) - 1
                coef = fact(S) * fact(F - S - 1) / fact(F)
                results[c] += ev * coef
            if c not in ec:
                S = len(ec)
                coef = fact(S) * fact(F - S - 1) / fact(F)
                results[c] -= ev * coef
    return results


def test_contributions_against_naive(mod, y, train, test, link=False, eps=1e-6):
    # In this test, I'm generating the data in python and then at once converting to h2o frame. This speeds it by several magnitudes
    # but it also creates nasty bugs when NAs are involved, e.g., category level "3" gets converted to float and hence is a new level "3.0"
    # that's why I remove NAs here
    print("Testing against naive shap calculation...")
    train = train.na_omit()
    test = test.na_omit()
    if mod.actual_params.get("ntrees", 0) > 50 and y == "survived":
        eps = 1e-3  # With more trees we get more numerical errors (preds in floats, contribs in double) and link seems to increase the the likelihood of num. errors  (big num. "squeezed" close to 1 and then on python side "unsqueezed" -> smaller error in link space increases in the "unlinked" space)
    for xrow in tqdm(sample(range(test.nrow), k=LARGE_K), desc="X row"):
        if any([test[xrow, k] == "NA" for k, v in train.types.items() if v == "enum"]):
            continue  # Converting NA from pandas to h2oFrame gets very messy
        for brow in tqdm(sample(range(train.nrow), k=K), leave=False, desc="B row"):
            if any(
                [train[brow, k] == "NA" for k, v in train.types.items() if v == "enum"]
            ):
                continue
            with h2o.no_progress_block():
                naive_contr = naiveBSHAP(mod, y, train, test, xrow, brow, link=link)
                contr = mod.predict_contributions(
                    test[xrow, :],
                    background_frame=train[brow, :],
                    output_format="compact",
                    output_per_reference=True
                ).as_data_frame()
                contr = contr.loc[:, (contr != 0).values[0]]
                cols = set(contr.columns)
                cols = cols.union(set(naive_contr.keys()))
                if "BiasTerm" in cols:
                    cols.remove("BiasTerm")
                for col in cols:
                    if col not in contr.columns:
                        assert (
                            abs(naive_contr[col]) < eps
                        ), f"{col} present in naive contr but not in contr with value {naive_contr[col]}, xrow={xrow}, brow={brow}"
                    else:
                        assert (
                            abs(naive_contr[col] - contr.loc[0, col]) < eps
                        ), f"{col} contributions differ: contr={contr.loc[0, col]}, naive_contr={naive_contr[col]}, diff={naive_contr[col] - contr.loc[0, col]}, xrow={xrow}, brow={brow}"


def import_data(seed=seed, no_NA=False):
    h2o.remove_all()
    df = h2o.import_file(
        pyunit_utils.locate("smalldata/titanic/titanic_expanded.csv"),
        na_strings=["", " ", "NA"],
    ).drop(["name", "body", "ticket"])
    df["survived"] = df["survived"].asfactor()
    if no_NA:
        df = df.na_omit()
    return df.split_frame([0.75], seed=seed)


def test_per_reference_aggregation(model, train, test, output_format):
    print("Testing per reference aggregation...")
    contrib = model.predict_contributions(test, background_frame=train, output_format=output_format).as_data_frame()
    py_agg_contrib = (
        model
        .predict_contributions(test, background_frame=train, output_per_reference=True, output_format=output_format)
        .as_data_frame()
        .drop("BackgroundRowIdx", axis=1)
        .groupby("RowIdx")
        .mean()
        .sort_values("RowIdx")
    )
    for c in contrib.columns:
        diff = (contrib[c] - py_agg_contrib[c]).abs()
        bool_diff = diff.max() < 1e-10
        assert bool_diff, f"{c}: {bool_diff} ({diff.max()})"


def helper_test_all(
    Estimator, y, train, test, output_format, link=False, eps=1e-6, skip_naive=False, skip_symmetry=False, **kwargs
):
    # Using seed to prevent DL models to end up with an unstable model
    mod = Estimator(seed=seed, **kwargs)
    mod.train(y=y, training_frame=train)
    
    train, _ = train.split_frame([2*LARGE_K/train.nrows], seed=seed)
    test, _ = test.split_frame([2*LARGE_K/test.nrows], seed=seed)
    
    test_local_accuracy(
        mod, train, test, link=link, output_format=output_format, eps=eps
    )

    if link:
        test_local_accuracy(
            mod, train, test, link=False, output_format=output_format, eps=eps, output_space=True
        )

    test_dummy_property(mod, train, test, output_format=output_format)

    if not skip_symmetry:  # Used for stacked ensembles when output_format == "original"
        test_symmetry(mod, train, test, output_format=output_format, eps=eps)

    if output_format.lower() == "compact" and not skip_naive:
        test_contributions_against_naive(mod, y, train, test, link=link, eps=eps)

    test_per_reference_aggregation(mod, train, test, output_format)


def helper_test_automl(y, train, test, output_format, eps=1e-4, max_models=13, monotone=False, **kwargs
                       ):
    remove_all_but(train, test)
    # Using seed to prevent DL models to end up with an unstable model
    aml = H2OAutoML(max_models=max_models, seed=seed,
                    monotone_constraints=dict(age=1, family_size=-1) if monotone else None,
                    **kwargs)
    aml.train(y=y, training_frame=train)

    models = [m[0] for m in aml.leaderboard[:, "model_id"].as_data_frame(False, False)]

    for model in models:
        print(model + " (" + output_format + ")\n" + "=" * (len(model) + len(output_format) + 3))
        mod = h2o.get_model(model)
        link = y == "survived" and mod.algo.lower() in ["glm", "gbm", "xgboost", "stackedensemble"]
        skip_naive = mod.algo.lower() in ["deeplearning", "stackedensemble"]
        skip_symmetry = mod.algo.lower() in ["stackedensemble", "glm"] and output_format == "original"
        skip_dummy = mod.algo.lower() in ["glm", "stackedensemble"] and output_format == "original"

        test_local_accuracy(
            mod, train, test, link=link, output_format=output_format, eps=eps
        )

        if link:
            test_local_accuracy(
                mod, train, test, link=False, output_format=output_format, eps=eps, output_space=True
            )

        if not skip_dummy:
            test_dummy_property(mod, train, test, output_format=output_format)

        if not skip_symmetry:
            test_symmetry(mod, train, test, output_format=output_format, eps=eps)

        if output_format.lower() == "compact" and not skip_naive:
            test_contributions_against_naive(mod, y, train, test, link=link,
                                             eps=eps if mod.algo.lower() != "xgboost" else 0.0002)

        test_per_reference_aggregation(mod, train, test, output_format)


def helper_test_automl_distributions(y, train, test, output_format, distribution, eps=1e-4, max_models=13, **kwargs
                                     ):
    remove_all_but(train, test)
    distribution = distribution.lower()
    # Using seed to prevent DL models to end up with an unstable model
    aml = H2OAutoML(max_models=max_models, seed=seed,
                    distribution=distribution,
                    **kwargs)
    aml.train(y=y, training_frame=train)

    models = [m[0] for m in aml.leaderboard[:, "model_id"].as_data_frame(False, False)]

    for model in models:
        try:
            print(model + " (" + output_format + ")\n" + "=" * (len(model) + len(output_format) + 3))
            mod = h2o.get_model(model)
            dist = mod.actual_params.get("distribution", mod.actual_params.get("family", "")).lower()
            if hasattr(mod, "metalearner"):
                dist = mod.metalearner().actual_params.get("distribution",
                                                           mod.metalearner().actual_params.get("family", "")).lower()
            if dist != distribution:
                print(f"Skipping model {model}... {distribution} not supported...")
                continue

            skip_naive = mod.algo.lower() in ["deeplearning", "stackedensemble"]
            skip_symmetry = mod.algo.lower() in ["stackedensemble"] and output_format == "original"
            skip_dummy = mod.algo.lower() in ["glm", "stackedensemble"] and output_format == "original"
            link = False
            if dist in ["poisson", "gamma", "tweedie", "negativebinomial"]:
                link = "log"
            if "GLM" in model:
                if dist == "gamma":
                    link = "inverse"
                if dist == "tweedie":
                    link = "identity"

            test_local_accuracy(
                mod, train, test, link=False, output_format=output_format, eps=eps, output_space=True
            )

            if not skip_dummy:
                test_dummy_property(mod, train, test, output_format=output_format)

            if not skip_symmetry:
                test_symmetry(mod, train, test, output_format=output_format, eps=eps)

            if output_format.lower() == "compact" and not skip_naive:
                test_contributions_against_naive(mod, y, train, test, link=link, eps=eps)

            test_per_reference_aggregation(mod, train, test, output_format)
        except OSError:
            pass  # when no base models are used by the SE


########################################################################################################################


def test_drf_binomial_compact():
    train, test = import_data()
    helper_test_all(H2ORandomForestEstimator, "survived", train, test, "compact")


def test_drf_regression_original():
    train, test = import_data()
    helper_test_all(H2ORandomForestEstimator, "fare", train, test, "original")


def test_xrt_binomial_original():
    train, test = import_data()
    helper_test_all(
        H2ORandomForestEstimator,
        "survived",
        train,
        test,
        "original",
        histogram_type="random",
    )


def test_xrt_regression_compact():
    train, test = import_data()
    helper_test_all(
        H2ORandomForestEstimator,
        "fare",
        train,
        test,
        "compact",
        histogram_type="random",
    )


def test_gbm_binomial_compact():
    train, test = import_data()
    helper_test_all(
        H2OGradientBoostingEstimator, "survived", train, test, "compact", link=True
    )


def test_gbm_regression_original():
    train, test = import_data()
    helper_test_all(H2OGradientBoostingEstimator, "fare", train, test, "original")


def test_xgboost_binomial_original():
    train, test = import_data()
    helper_test_all(H2OXGBoostEstimator, "survived", train, test, "original", link=True)


def test_xgboost_regression_compact():
    train, test = import_data()
    helper_test_all(H2OXGBoostEstimator, "fare", train, test, "compact", eps=1e-3)


def test_glm_binomial_compact():
    train, test = import_data(no_NA=True)
    helper_test_all(
        H2OGeneralizedLinearEstimator,
        "survived",
        train,
        test,
        "compact",
        link=True,
        standardize=True,
    )


def test_glm_regression_original():
    train, test = import_data(no_NA=True)
    helper_test_all(
        H2OGeneralizedLinearEstimator, "fare", train, test, "original", standardize=True
    )


def test_glm_not_standardized_binomial_original():
    train, test = import_data(no_NA=True)
    helper_test_all(
        H2OGeneralizedLinearEstimator,
        "survived",
        train,
        test,
        "original",
        link=True,
        standardize=False,
    )


def test_glm_not_standardized_regression_original():
    train, test = import_data(no_NA=True)
    helper_test_all(
        H2OGeneralizedLinearEstimator,
        "fare",
        train,
        test,
        "original",
        standardize=False,
    )


def test_glm_not_regularized_binomial_compact():
    train, test = import_data(no_NA=True)
    helper_test_all(
        H2OGeneralizedLinearEstimator,
        "survived",
        train,
        test,
        "compact",
        link=True,
        lambda_=0,
    )


def test_glm_not_regularized_regression_compact():
    train, test = import_data(no_NA=True)
    helper_test_all(
        H2OGeneralizedLinearEstimator, "fare", train, test, "compact", lambda_=0
    )


def test_deeplearning_5hidden_tanh_regression_original():
    train, test = import_data()
    helper_test_all(
        H2ODeepLearningEstimator, "fare", train, test, "original", skip_naive=True, reproducible=True,
        activation="tanh", hidden=[5] * 5
    )


def test_deeplearning_5hidden_tanh_regression_compact():
    train, test = import_data()
    helper_test_all(
        H2ODeepLearningEstimator, "fare", train, test, "compact", skip_naive=True, reproducible=True, activation="tanh",
        hidden=[5] * 5
    )


def test_deeplearning_5hidden_tanh_binomial_original():
    train, test = import_data()
    helper_test_all(
        H2ODeepLearningEstimator, "survived", train, test, "original", skip_naive=True, reproducible=True,
        activation="tanh",
        hidden=[5] * 5
    )


def test_deeplearning_5hidden_tanh_binomial_compact():
    train, test = import_data()
    helper_test_all(
        H2ODeepLearningEstimator, "survived", train, test, "compact", skip_naive=True, reproducible=True,
        activation="tanh", hidden=[5] * 5
    )


def test_deeplearning_5hidden_tanh_with_dropout_regression_original():
    train, test = import_data()
    helper_test_all(
        H2ODeepLearningEstimator, "fare", train, test, "original", skip_naive=True, reproducible=True,
        activation="tanh_with_dropout", input_dropout_ratio=0.2,
        hidden=[5] * 5, hidden_dropout_ratios=[0.3, 0.5, 0.1, 0.4, 0.6]
    )


def test_deeplearning_5hidden_tanh_with_dropout_regression_compact():
    train, test = import_data()
    helper_test_all(
        H2ODeepLearningEstimator, "fare", train, test, "compact", skip_naive=True, reproducible=True,
        activation="tanh_with_dropout", input_dropout_ratio=0.2,
        hidden=[5] * 5, hidden_dropout_ratios=[0.3, 0.5, 0.1, 0.4, 0.6]
    )


def test_deeplearning_5hidden_tanh_with_dropout_binomial_original():
    train, test = import_data()
    helper_test_all(
        H2ODeepLearningEstimator, "survived", train, test, "original", skip_naive=True, reproducible=True,
        activation="tanh_with_dropout", input_dropout_ratio=0.2,
        hidden=[5] * 5, hidden_dropout_ratios=[0.3, 0.5, 0.1, 0.4, 0.6]
    )


def test_deeplearning_5hidden_tanh_with_dropout_binomial_compact():
    train, test = import_data()
    helper_test_all(
        H2ODeepLearningEstimator, "survived", train, test, "compact", skip_naive=True, reproducible=True,
        activation="tanh_with_dropout", input_dropout_ratio=0.2,
        hidden=[5] * 5, hidden_dropout_ratios=[0.3, 0.5, 0.1, 0.4, 0.6]
    )


def test_deeplearning_5hidden_relu_regression_original():
    train, test = import_data()
    helper_test_all(
        H2ODeepLearningEstimator, "fare", train, test, "original", skip_naive=True, reproducible=True,
        activation="rectifier",
        hidden=[5] * 5
    )


def test_deeplearning_5hidden_relu_regression_compact():
    train, test = import_data()
    helper_test_all(
        H2ODeepLearningEstimator, "fare", train, test, "compact", skip_naive=True, reproducible=True,
        activation="rectifier",
        hidden=[5] * 5
    )


def test_deeplearning_5hidden_relu_binomial_original():
    train, test = import_data()
    helper_test_all(
        H2ODeepLearningEstimator, "survived", train, test, "original", skip_naive=True, reproducible=True,
        activation="tanh",
        hidden=[5] * 5
    )


def test_deeplearning_5hidden_relu_binomial_compact():
    train, test = import_data()
    helper_test_all(
        H2ODeepLearningEstimator, "survived", train, test, "compact", skip_naive=True, reproducible=True,
        activation="tanh", hidden=[5] * 5
    )


def test_deeplearning_5hidden_relu_with_dropout_regression_original():
    train, test = import_data()
    helper_test_all(
        H2ODeepLearningEstimator, "fare", train, test, "original", skip_naive=True, reproducible=True,
        activation="rectifier_with_dropout", input_dropout_ratio=0.2,
        hidden=[5] * 5, hidden_dropout_ratios=[0.3, 0.5, 0.1, 0.4, 0.6]
    )


def test_deeplearning_5hidden_relu_with_dropout_regression_compact():
    train, test = import_data()
    helper_test_all(
        H2ODeepLearningEstimator, "fare", train, test, "compact", skip_naive=True, reproducible=True,
        activation="rectifier_with_dropout", input_dropout_ratio=0.2,
        hidden=[5] * 5, hidden_dropout_ratios=[0.3, 0.5, 0.1, 0.4, 0.6]
    )


def test_deeplearning_5hidden_relu_with_dropout_binomial_original():
    train, test = import_data()
    helper_test_all(
        H2ODeepLearningEstimator, "survived", train, test, "original", skip_naive=True, reproducible=True,
        activation="rectifier_with_dropout", input_dropout_ratio=0.2, hidden=[5] * 5,
        hidden_dropout_ratios=[0.3, 0.5, 0.1, 0.4, 0.6]
    )


def test_deeplearning_5hidden_relu_with_dropout_binomial_compact():
    train, test = import_data()
    helper_test_all(
        H2ODeepLearningEstimator, "survived", train, test, "compact", skip_naive=True, reproducible=True,
        activation="rectifier_with_dropout", input_dropout_ratio=0.2, hidden=[5] * 5,
        hidden_dropout_ratios=[0.3, 0.5, 0.1, 0.4, 0.6]
    )


def test_deeplearning_5hidden_maxout_regression_original():
    train, test = import_data()
    helper_test_all(
        H2ODeepLearningEstimator, "fare", train, test, "original", skip_naive=True, reproducible=True,
        activation="maxout",
        hidden=[5] * 5
    )


def test_deeplearning_5hidden_maxout_regression_compact():
    train, test = import_data()
    helper_test_all(
        H2ODeepLearningEstimator, "fare", train, test, "compact", skip_naive=True, reproducible=True,
        activation="maxout",
        hidden=[5] * 5
    )


def test_deeplearning_5hidden_maxout_binomial_original():
    train, test = import_data()
    helper_test_all(
        H2ODeepLearningEstimator, "survived", train, test, "original", skip_naive=True, reproducible=True,
        activation="tanh",
        hidden=[5] * 5
    )


def test_deeplearning_5hidden_maxout_binomial_compact():
    train, test = import_data()
    helper_test_all(
        H2ODeepLearningEstimator, "survived", train, test, "compact", skip_naive=True, reproducible=True,
        activation="tanh", hidden=[5] * 5
    )


def test_deeplearning_5hidden_maxout_with_dropout_regression_original():
    train, test = import_data()
    helper_test_all(
        H2ODeepLearningEstimator, "fare", train, test, "original", skip_naive=True, reproducible=True,
        activation="maxout_with_dropout", input_dropout_ratio=0.2,
        hidden=[5] * 5, hidden_dropout_ratios=[0.3, 0.5, 0.1, 0.4, 0.6]
    )


def test_deeplearning_5hidden_maxout_with_dropout_regression_compact():
    train, test = import_data()
    helper_test_all(
        H2ODeepLearningEstimator, "fare", train, test, "compact", skip_naive=True, reproducible=True,
        activation="maxout_with_dropout", input_dropout_ratio=0.2,
        hidden=[5] * 5, hidden_dropout_ratios=[0.3, 0.5, 0.1, 0.4, 0.6]
    )


def test_deeplearning_5hidden_maxout_with_dropout_binomial_original():
    train, test = import_data()
    helper_test_all(
        H2ODeepLearningEstimator, "survived", train, test, "original", skip_naive=True, reproducible=True,
        activation="maxout_with_dropout", input_dropout_ratio=0.2, hidden=[5] * 5,
        hidden_dropout_ratios=[0.3, 0.5, 0.1, 0.4, 0.6]
    )


def test_deeplearning_5hidden_maxout_with_dropout_binomial_compact():
    train, test = import_data()
    helper_test_all(
        H2ODeepLearningEstimator, "survived", train, test, "compact", skip_naive=True, reproducible=True,
        activation="maxout_with_dropout", input_dropout_ratio=0.2, hidden=[5] * 5,
        hidden_dropout_ratios=[0.3, 0.5, 0.1, 0.4, 0.6]
    )


def test_se_gaussian_linear_models_exact_original():
    # SHAP for SE made from gaussian GLMs should be exact
    train, test = import_data(no_NA=True)
    y = "fare"
    kw = dict(nfolds=5, keep_cross_validation_predictions=True, seed=seed)
    glm1 = H2OGeneralizedLinearEstimator(lambda_search=True, **kw)
    glm1.train(y=y, training_frame=train)
    glm2 = H2OGeneralizedLinearEstimator(lambda_=1, **kw)
    glm2.train(y=y, training_frame=train)
    glm3 = H2OGeneralizedLinearEstimator(**kw)
    glm3.train(y=y, training_frame=train)
    glm4 = H2OGeneralizedLinearEstimator(alpha=0.4, lambda_=0.6, **kw)
    glm4.train(y=y, training_frame=train)

    helper_test_all(
        H2OStackedEnsembleEstimator, y, train, test, "original", skip_naive=False, base_models=[glm1, glm2, glm3, glm4]
    )


def test_se_gaussian_linear_models_exact_compact():
    # SHAP for SE made from gaussian GLMs should be exact
    train, test = import_data()
    y = "fare"
    kw = dict(nfolds=5, keep_cross_validation_predictions=True, seed=seed)
    glm1 = H2OGeneralizedLinearEstimator(lambda_search=True, **kw)
    glm1.train(y=y, training_frame=train)
    glm2 = H2OGeneralizedLinearEstimator(lambda_=1, **kw)
    glm2.train(y=y, training_frame=train)
    glm3 = H2OGeneralizedLinearEstimator(**kw)
    glm3.train(y=y, training_frame=train)
    glm4 = H2OGeneralizedLinearEstimator(alpha=0.4, lambda_=0.6, **kw)
    glm4.train(y=y, training_frame=train)

    helper_test_all(
        H2OStackedEnsembleEstimator, y, train, test, "compact", skip_naive=False, base_models=[glm1, glm2, glm3, glm4]
    )


def test_se_all_models_with_default_config_binomial_original():
    train, test = import_data(no_NA=True)
    y = "survived"
    kw = dict(nfolds=5, keep_cross_validation_predictions=True, seed=seed)
    # GLM doesn't have .missing(NA) for missing categories
    #    glm = H2OGeneralizedLinearEstimator(**kw)
    #    glm.train(y=y, training_frame=train)

    gbm = H2OGradientBoostingEstimator(**kw)
    gbm.train(y=y, training_frame=train)

    drf = H2ORandomForestEstimator(**kw)
    drf.train(y=y, training_frame=train)

    xgb = H2OXGBoostEstimator(**kw)
    xgb.train(y=y, training_frame=train)

    dl = H2ODeepLearningEstimator(reproducible=True, **kw)
    dl.train(y=y, training_frame=train)

    helper_test_all(
        H2OStackedEnsembleEstimator, y, train, test, "original", link=True, skip_naive=True,
        base_models=[gbm, drf, xgb, dl]
    )


def test_se_all_models_with_default_config_binomial_compact():
    train, test = import_data()
    y = "survived"
    kw = dict(nfolds=5, keep_cross_validation_predictions=True, seed=seed)

    glm = H2OGeneralizedLinearEstimator(**kw)
    glm.train(y=y, training_frame=train)

    gbm = H2OGradientBoostingEstimator(**kw)
    gbm.train(y=y, training_frame=train)

    drf = H2ORandomForestEstimator(**kw)
    drf.train(y=y, training_frame=train)

    xgb = H2OXGBoostEstimator(**kw)
    xgb.train(y=y, training_frame=train)

    dl = H2ODeepLearningEstimator(reproducible=True, **kw)
    dl.train(y=y, training_frame=train)

    helper_test_all(
        H2OStackedEnsembleEstimator, y, train, test, "compact", link=True, skip_naive=True,
        base_models=[glm, gbm, drf, xgb, dl]
    )


def test_se_all_models_with_default_config_binomial_with_logit_transform_original():
    train, test = import_data(no_NA=True)
    y = "survived"
    kw = dict(nfolds=5, keep_cross_validation_predictions=True, seed=seed)

    # GLM doesn't have .missing(NA) for missing categories
    #    glm = H2OGeneralizedLinearEstimator(**kw)
    #    glm.train(y=y, training_frame=train)

    gbm = H2OGradientBoostingEstimator(**kw)
    gbm.train(y=y, training_frame=train)

    drf = H2ORandomForestEstimator(**kw)
    drf.train(y=y, training_frame=train)

    xgb = H2OXGBoostEstimator(**kw)
    xgb.train(y=y, training_frame=train)

    dl = H2ODeepLearningEstimator(reproducible=True, **kw)
    dl.train(y=y, training_frame=train)

    helper_test_all(
        H2OStackedEnsembleEstimator, y, train, test, "original", link=True, skip_naive=True,
        base_models=[gbm, drf, xgb, dl],
        metalearner_transform="logit"
    )


def test_se_all_models_with_default_config_binomial_with_logit_transform_compact():
    train, test = import_data()
    y = "survived"
    kw = dict(nfolds=5, keep_cross_validation_predictions=True, seed=seed)

    # GLM doesn't have .missing(NA) for missing categories
    #    glm = H2OGeneralizedLinearEstimator(**kw)
    #    glm.train(y=y, training_frame=train)

    gbm = H2OGradientBoostingEstimator(**kw)
    gbm.train(y=y, training_frame=train)

    drf = H2ORandomForestEstimator(**kw)
    drf.train(y=y, training_frame=train)

    xgb = H2OXGBoostEstimator(**kw)
    xgb.train(y=y, training_frame=train)

    dl = H2ODeepLearningEstimator(reproducible=True, **kw)
    dl.train(y=y, training_frame=train)

    helper_test_all(
        H2OStackedEnsembleEstimator, y, train, test, "compact", link=True, skip_naive=True,
        base_models=[gbm, drf, xgb, dl],
        metalearner_transform="logit"
    )


def test_se_all_models_with_default_config_regression_compact():
    train, test = import_data()
    y = "fare"
    kw = dict(nfolds=5, keep_cross_validation_predictions=True, seed=seed)

    glm = H2OGeneralizedLinearEstimator(**kw)
    glm.train(y=y, training_frame=train)

    gbm = H2OGradientBoostingEstimator(**kw)
    gbm.train(y=y, training_frame=train)

    drf = H2ORandomForestEstimator(**kw)
    drf.train(y=y, training_frame=train)

    xgb = H2OXGBoostEstimator(**kw)
    xgb.train(y=y, training_frame=train)

    dl = H2ODeepLearningEstimator(reproducible=True, **kw)
    dl.train(y=y, training_frame=train)

    helper_test_all(
        H2OStackedEnsembleEstimator, y, train, test, "compact", skip_naive=True, base_models=[glm, gbm, drf, xgb, dl]
    )


def test_se_all_models_with_default_config_regression_original():
    train, test = import_data(no_NA=True)
    y = "fare"
    kw = dict(nfolds=5, keep_cross_validation_predictions=True, seed=seed)

    # GLM doesn't have .missing(NA) for missing categories
    #    glm = H2OGeneralizedLinearEstimator(**kw)
    #    glm.train(y=y, training_frame=train)

    gbm = H2OGradientBoostingEstimator(**kw)
    gbm.train(y=y, training_frame=train)

    drf = H2ORandomForestEstimator(**kw)
    drf.train(y=y, training_frame=train)

    xgb = H2OXGBoostEstimator(**kw)
    xgb.train(y=y, training_frame=train)

    dl = H2ODeepLearningEstimator(reproducible=True, **kw)
    dl.train(y=y, training_frame=train)

    helper_test_all(
        H2OStackedEnsembleEstimator, y, train, test, "original", skip_naive=True, skip_symmetry=True,
        base_models=[gbm, drf, xgb, dl]
    )


TESTS = [
    test_drf_binomial_compact,
    test_drf_regression_original,
    test_xrt_binomial_original,
    test_xrt_regression_compact,
    test_gbm_binomial_compact,
    test_gbm_regression_original,
    test_xgboost_binomial_original,
    test_xgboost_regression_compact,
    test_glm_binomial_compact,
    test_glm_regression_original,
    test_glm_not_standardized_binomial_original,
    test_glm_not_standardized_regression_original,
    test_glm_not_regularized_binomial_compact,
    test_glm_not_regularized_regression_compact,
    test_deeplearning_5hidden_tanh_regression_original,
    test_deeplearning_5hidden_tanh_regression_compact,
    test_deeplearning_5hidden_tanh_binomial_original,
    test_deeplearning_5hidden_tanh_binomial_compact,
    test_deeplearning_5hidden_tanh_with_dropout_regression_original,
    test_deeplearning_5hidden_tanh_with_dropout_regression_compact,
    test_deeplearning_5hidden_tanh_with_dropout_binomial_original,
    test_deeplearning_5hidden_tanh_with_dropout_binomial_compact,
    test_deeplearning_5hidden_relu_regression_original,
    test_deeplearning_5hidden_relu_regression_compact,
    test_deeplearning_5hidden_relu_binomial_original,
    test_deeplearning_5hidden_relu_binomial_compact,
    test_deeplearning_5hidden_relu_with_dropout_regression_original,
    test_deeplearning_5hidden_relu_with_dropout_regression_compact,
    test_deeplearning_5hidden_relu_with_dropout_binomial_original,
    test_deeplearning_5hidden_relu_with_dropout_binomial_compact,
    test_deeplearning_5hidden_maxout_regression_original,
    test_deeplearning_5hidden_maxout_regression_compact,
    test_deeplearning_5hidden_maxout_binomial_original,
    test_deeplearning_5hidden_maxout_binomial_compact,
    test_deeplearning_5hidden_maxout_with_dropout_regression_original,
    test_deeplearning_5hidden_maxout_with_dropout_regression_compact,
    test_deeplearning_5hidden_maxout_with_dropout_binomial_original,
    test_deeplearning_5hidden_maxout_with_dropout_binomial_compact,
    test_se_gaussian_linear_models_exact_original,
    test_se_gaussian_linear_models_exact_compact,
    test_se_all_models_with_default_config_binomial_original,
    test_se_all_models_with_default_config_binomial_compact,
    test_se_all_models_with_default_config_binomial_with_logit_transform_original,
    test_se_all_models_with_default_config_binomial_with_logit_transform_compact,
    test_se_all_models_with_default_config_regression_original,
    test_se_all_models_with_default_config_regression_compact,
]


if __name__ == "__main__":
    pyunit_utils.run_tests(TESTS)
