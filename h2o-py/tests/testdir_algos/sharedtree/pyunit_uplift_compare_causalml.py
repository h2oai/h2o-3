import sys
import os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators.upliftdrf import H2OUpliftRandomForestEstimator
from causalml.inference.tree import UpliftRandomForestClassifier


def uplift_compare():
    nrow = 10000
    nfeat = 20
    ntree = 1000
    max_depth = 3
    features = h2o.create_frame(
        rows=nrow, cols=nfeat,
        real_fraction=1,
        real_range=100,
        missing_fraction=0,
        has_response=True,
        response_factors=2,
        seed=1234
    )
    control = h2o.create_frame(
        rows=nrow, cols=1,
        binary_fraction=1,
        binary_ones_fraction=0.6,
        missing_fraction=0,
        seed=1234
    )
    control.names = ["GROUP"]
    control[0] = control[0].asfactor()
    df_h2o = features.cbind(control)
    df_h2o.summary()
    
    feature_cols = df_h2o.names[1:nfeat+1]
    uplift_column = df_h2o.names[nfeat+1]
    
    drf = H2OUpliftRandomForestEstimator(
        ntrees=ntree,
        max_depth=max_depth,
        uplift_column=uplift_column,
        uplift_metric="KL",
        distribution="bernoulli",
        gainslift_bins=10,
        min_rows=10,
        nbins=100,
        seed=42
    )
    drf.train(y=0, training_frame=df_h2o)
    
    df = df_h2o.as_data_frame()
    df[uplift_column] = df[uplift_column].astype(str)
    uplift_model = UpliftRandomForestClassifier(
        n_estimators=ntree,
        max_depth=max_depth,
        evaluationFunction='KL',
        control_name='0',
        min_samples_leaf=10,
        min_samples_treatment=10,
        normalization=False,
        random_state=42
    )
    uplift_model.fit(
        df[feature_cols].values,
        treatment=df[uplift_column].values,
        y=df['response'].values
    )
    
    test = h2o.create_frame(
        rows=1000, cols=nfeat,
        categorical_fraction=0,
        integer_fraction=0,
        binary_fraction=0,
        real_range=100,
        missing_fraction=0,
        has_response=False,
        seed=1234
    )
    test_df = test.as_data_frame()
    preds_h2o = drf.predict(test)
    preds_h2o[0] = preds_h2o[1] - preds_h2o[2]
    
    causal_preds = uplift_model.predict(test_df.values)
    
    preds_comp = preds_h2o["uplift_predict"]
    preds_comp.names = ["h2o"]
    preds_comp["causal"] = h2o.H2OFrame(causal_preds)
    preds_comp["diff"] = abs(preds_comp["h2o"] - preds_comp["causal"])
    preds_comp.summary()
    
    max_diff = preds_comp["diff"].max()
    mean_diff = preds_comp["diff"].mean(return_frame=False)[0]
    print("Diffs mean %s max %s" % (mean_diff, max_diff))
    assert max_diff < 0.01, "biggest different should not be higher than 1%"
    assert mean_diff < 0.001, "average difference should not be higher than 0.1%"


if __name__ == "__main__":
    pyunit_utils.standalone_test(uplift_compare)
else:
    uplift_compare()
