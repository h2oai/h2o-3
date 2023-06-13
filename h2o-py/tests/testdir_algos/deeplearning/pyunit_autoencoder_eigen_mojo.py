#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import h2o
from tests import pyunit_utils
from h2o.estimators.deeplearning import H2OAutoEncoderEstimator
from pandas.testing import assert_frame_equal


def test_high_cardinality_eigen():
    df = h2o.create_frame(
        rows=10000,
        cols=10,
        categorical_fraction=0.6,
        integer_fraction=0,
        binary_fraction=0,
        real_range=100,
        integer_range=100,
        missing_fraction=0,
        factors=10,
        seed=1234
    )
    autoencoder = H2OAutoEncoderEstimator(categorical_encoding="eigen", reproducible=True, 
                                          hidden=[50, 30], epochs=5, seed=42)
    autoencoder.train(training_frame=df)

    mojo = pyunit_utils.download_mojo(autoencoder)
    autoencoder_mojo = h2o.import_mojo(mojo["mojo_zip_path"])

    preds_ae_h2o = autoencoder.predict(df)
    preds_ae_mojo = autoencoder_mojo.predict(df)
    assert_frame_equal(preds_ae_mojo.as_data_frame(), preds_ae_h2o.as_data_frame())


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_high_cardinality_eigen)
else:
    test_high_cardinality_eigen()
