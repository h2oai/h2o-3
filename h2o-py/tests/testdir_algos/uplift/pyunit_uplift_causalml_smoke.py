import sys
import unittest


class TestCausalmlIsWorking(unittest.TestCase):

    @unittest.skipIf(sys.version_info[0] < 3 or (sys.version_info[0] == 3 and sys.version_info[1] <= 5), "Tested only on >3.5, causalml is not supported on lower python version")
    def test_causalml_smoke(self):
        from causalml.inference.tree import UpliftRandomForestClassifier
        from causalml.dataset import make_uplift_classification
        import causalml

        print("Causalml library smoke test")
        print("Version of causalml: {0}".format(causalml.__version__))
        n_samples = 500
        seed = 12345

        train, x_names = make_uplift_classification(n_samples=n_samples,
                                                    treatment_name=['control', 'treatment'],
                                                    n_classification_features=10,
                                                    n_classification_informative=10,
                                                    random_seed=seed
                                                    )
        assert not train.empty
        assert x_names

        treatment_column = "treatment_group_key"
        response_column = "conversion"
        train[treatment_column] = train[treatment_column].astype(str)
        uplift_model = UpliftRandomForestClassifier(
            n_estimators=5,
            max_depth=8,
            evaluationFunction="KL",
            control_name="control",
            min_samples_leaf=10,
            min_samples_treatment=10,
            normalization=False,
            random_state=42,
        )
        uplift_model.fit(
            train[x_names].values,
            treatment=train[treatment_column].values,
            y=train[response_column].values
        )

        assert uplift_model


suite = unittest.TestLoader().loadTestsFromTestCase(TestCausalmlIsWorking)
unittest.TextTestRunner().run(suite)
