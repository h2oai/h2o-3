import sys
import os
sys.path.insert(1, os.path.join("..", "..", ".."))
import h2o
from tests import pyunit_utils
from h2o.estimators.uplift_random_forest import H2OUpliftRandomForestEstimator
import unittest

import pandas as pd


class CompareUpliftDrfWithCausalMl(unittest.TestCase):

    @unittest.skipIf(sys.version_info[0] < 3 or (sys.version_info[0] == 3 and sys.version_info[1] <= 5), 
                     "Tested only on >3.5, causalml is not supported on lower python version")
    def test_uplift_compare(self):
        from causalml.inference.tree import UpliftRandomForestClassifier
        from causalml.metrics import auuc_score
        h2o.init(strict_version_check=False)
        treatment_column = "treatment"
        response_column = "outcome"
        feature_cols = ["feature_"+str(x) for x in range(1,13)]

        train_df = pd.read_csv(pyunit_utils.locate("smalldata/uplift/upliftml_train.csv"))
        test_df = pd.read_csv(pyunit_utils.locate("smalldata/uplift/upliftml_test.csv"))
    
        train = h2o.H2OFrame(train_df)
        train[treatment_column] = train[treatment_column].asfactor()
        train[response_column] = train[response_column].asfactor()

        test = h2o.H2OFrame(test_df)
        test[treatment_column] = test[treatment_column].asfactor()
        test[response_column] = test[response_column].asfactor()

        train_df[treatment_column].replace({1: "treatment", 0: "control"}, inplace=True)
        test_df[treatment_column].replace({1: "treatment", 0: "control"}, inplace=True)

        ntree = 30
        max_depth = 10

        auuc_types = ["qini", "lift", "gain"]
        h2o_drfs = [None] * len(auuc_types)
        for i in range(len(auuc_types)):
            drf = H2OUpliftRandomForestEstimator(
                ntrees=ntree,
                max_depth=max_depth,
                treatment_column=treatment_column,
                uplift_metric="euclidean",
                distribution="bernoulli",
                min_rows=5,
                nbins=1000,
                seed=42,
                auuc_type=auuc_types[i],
                sample_rate=0.9
            )
            drf.train(y=response_column, x=feature_cols, training_frame=train)
            h2o_drfs[i] = drf

        uplift_model = UpliftRandomForestClassifier(
            n_estimators=ntree,
            max_depth=max_depth,
            evaluationFunction="ED",
            control_name="control",
            min_samples_leaf=5,
            min_samples_treatment=5,
            normalization=False,
            random_state=42,
        )
        uplift_model.fit(
            train_df[feature_cols].values,
            treatment=train_df[treatment_column].values,
            y=train_df[response_column].values
        )

        testing_df = test
        causal_preds = uplift_model.predict(test_df.values)

        for i in range(len(h2o_drfs)):
            preds_h2o = h2o_drfs[i].predict(testing_df)

            preds_comp = preds_h2o["uplift_predict"]
            preds_comp.names = ["h2o"]
            preds_comp["causal"] = h2o.H2OFrame(causal_preds)
            preds_comp["diff"] = abs(preds_comp["h2o"] - preds_comp["causal"])
            preds_comp[treatment_column] = testing_df[treatment_column]
            preds_comp[response_column] = testing_df[response_column]
            preds_comp.summary()

            mean_diff = preds_comp["diff"].mean(return_frame=False)[0]
            print("Average difference: %f" % mean_diff)

        results = preds_comp.as_data_frame()
        results = results[["h2o", "causal", response_column, treatment_column]]
        mapping = {'control': 0, 'treatment': 1}
        results = results.replace({treatment_column: mapping})

        # calculate auuc using CausalML package
        auuc = auuc_score(results, outcome_col=response_column, treatment_col=treatment_column, normalize=False)

        # compare AUUC calculation with CausalML
        h2o_auuc_qain_test = h2o_drfs[2].model_performance(testing_df).auuc()
        print("AUUC calculation:")
        diff = abs(auuc["h2o"] - h2o_auuc_qain_test)
        print("CausalML H2O: %f H2O: %f diff: %f" % (auuc["h2o"], h2o_auuc_qain_test, diff))
        assert diff < 2, \
            "Absolute difference between causalML package and H2O AUUC calculation is higher than is expected: %f" % diff

        diff = abs(auuc["causal"] - auuc["h2o"])
        print("CausalML: %f H2O: %f diff: %f" % (auuc["causal"], auuc["h2o"], diff))

        # test plot_auuc
        perf = h2o_drfs[0].model_performance(testing_df)
        n, uplift = perf.plot_uplift(metric="gain", plot=False)
        print(uplift)

        print(perf.auuc())
        print(h2o_drfs[0].auuc())
        print(perf.auuc_table())
        print(h2o_drfs[0].auuc_table())


suite = unittest.TestLoader().loadTestsFromTestCase(CompareUpliftDrfWithCausalMl)
unittest.TextTestRunner().run(suite)
