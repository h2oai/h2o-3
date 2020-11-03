from h2o.estimators.xgboost import *
from tests import pyunit_utils

import unittest


class TestGammaWrongResponseType(unittest.TestCase):
    def test_numeric_response_error(self):
        assert H2OXGBoostEstimator.available()

        prostate_frame = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate_complete.csv.zip"))

        x = ["ID", "AGE", "RACE", "GLEASON", "DCAPS", "PSA", "VOL", "CAPSULE"]
        y = 'DPROS'
        prostate_frame[y] = prostate_frame[y].asfactor()

        model = H2OXGBoostEstimator(training_frame=prostate_frame, learn_rate=1,
                                    booster='gbtree', distribution='gamma')

        with self.assertRaises(h2o.exceptions.H2OResponseError) as outcome:
            model.train(x=x, y=y, training_frame=prostate_frame)

            assert str(outcome.exception).__contains__(
                "ERRR on field: _distribution: Gamma requires the response to be numeric.")


if __name__ == "__main__":
    suite = unittest.TestLoader().loadTestsFromTestCase(TestGammaWrongResponseType)
    unittest.TextTestRunner().run(suite)