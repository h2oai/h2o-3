import sys
import os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators.uplift_random_forest import H2OUpliftRandomForestEstimator
from causalml.inference.tree import UpliftRandomForestClassifier
from causalml.metrics import plot_gain, plot_qini, plot_lift
from causalml.metrics import auuc_score


def uplift_compare():

    data = h2o.import_file(path=pyunit_utils.locate("smalldata/uplift/criteo_uplift_13k.csv"))
    feature_cols = [f'f{i}' for i in range(0, 11)]
    uplift_column = "treatment"
    response_column = "conversion"
    data[uplift_column] = data[uplift_column].asfactor()
    data[response_column] = data[response_column].asfactor()

    split = data.split_frame(ratios=[0.75], seed=42)
    train = split[0]
    test = split[1]
    
    ntree = 100
    max_depth = 3
    
    auuc_types = ["qini", "lift", "gain"]
    h2o_drfs = [None] * len(auuc_types)
    for i in range(len(auuc_types)):
        drf = H2OUpliftRandomForestEstimator(
            ntrees=ntree,
            max_depth=max_depth,
            uplift_column=uplift_column,
            uplift_metric="KL",
            distribution="bernoulli",
            gainslift_bins=10,
            min_rows=10,
            nbins=1000,
            seed=42,
            auuc_type=auuc_types[i]
        )
        drf.train(y=response_column, x=feature_cols, training_frame=train)
        h2o_drfs[i] = drf
    
    df = train.as_data_frame()
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
        y=df[response_column].values
    )
    testing_df = train
    test_df = testing_df.as_data_frame()
    causal_preds = uplift_model.predict(test_df.values)

    for i in range(len(h2o_drfs)):
        preds_h2o = h2o_drfs[i].predict(testing_df)
        preds_h2o[0] = preds_h2o[1] - preds_h2o[2]
    
        preds_comp = preds_h2o["uplift_predict"]
        preds_comp.names = ["h2o"]
        preds_comp["causal"] = h2o.H2OFrame(causal_preds)
        preds_comp["diff"] = abs(preds_comp["h2o"] - preds_comp["causal"])
        preds_comp[uplift_column] = testing_df[uplift_column]
        preds_comp[response_column] = testing_df[response_column]
        preds_comp.summary()
    
        min_diff = preds_comp["diff"].min()
        max_diff = preds_comp["diff"].max()
        mean_diff = preds_comp["diff"].mean(return_frame=False)[0]
        print("min: %f max: %f mean: %f" % (min_diff, max_diff, mean_diff))

        # assert min_diff < 0.01, str(min_diff)+": smallest different should not be higher than 1%"
        # assert max_diff < 0.01, str(max_diff)+": biggest different should not be higher than 1%"
        # assert mean_diff < 0.001, str(mean_diff)+": average difference should not be higher than 0.1%"

        results = preds_comp.as_data_frame()
        results = results[["h2o", "causal", response_column, uplift_column]]
        plot_qini(results, outcome_col=response_column, treatment_col=uplift_column)
        plot_lift(results, outcome_col=response_column, treatment_col=uplift_column)

    auuc_qiny = h2o_drfs[0].training_model_metrics()["AUUC"]
    auuc_lift = h2o_drfs[1].training_model_metrics()["AUUC"]
    auuc_gain = h2o_drfs[2].training_model_metrics()["AUUC"]
    
    print("H2O metrics AUUC Qini: "+str(auuc_qiny))
    print("H2O metrics AUUC Lift: "+str(auuc_lift))
    print("H2O metrics AUUC Gain: "+str(auuc_gain))

    auuc = auuc_score(results, outcome_col=response_column, treatment_col=uplift_column)
    print("H2O AUUC:")
    print(auuc["h2o"])
    print("CauslML AUUC:")
    print(auuc["causal"])
    print("Random AUUC:")
    print(auuc["Random"])
    
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(uplift_compare)
else:
    uplift_compare()
