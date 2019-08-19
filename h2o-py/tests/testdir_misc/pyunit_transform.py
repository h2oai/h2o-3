import sys

sys.path.insert(1, "../../")

import h2o
from h2o.estimators import H2OGradientBoostingEstimator, H2ORandomForestEstimator, H2OIsolationForestEstimator, \
    H2OXGBoostEstimator, H2OGeneralizedLinearEstimator
from tests import pyunit_utils


def pyunit_transform():
    x = ["Origin", "Dest"]
    y = "IsDepDelayed"
    data = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/airlines_train.csv"))

    # GBM
    model = H2OGradientBoostingEstimator(ntrees=1)
    model.train(x=x, y=y, training_frame=data, validation_frame=data)
    transformation = model.transform(data)
    prediction = model.predict(data)
    pyunit_utils.compare_frames_local(transformation, prediction)

    # DRF
    model = H2ORandomForestEstimator(ntrees=1)
    model.train(x=x, y=y, training_frame=data, validation_frame=data)
    transformation = model.transform(data)
    prediction = model.predict(data)
    pyunit_utils.compare_frames_local(transformation, prediction)

    # Isolation Forest
    model = H2OIsolationForestEstimator()
    model.train(x=x, y=y, training_frame=data)
    transformation = model.transform(data)
    prediction = model.predict(data)
    pyunit_utils.compare_frames_local(transformation, prediction)

    # XGBoost
    model = H2OXGBoostEstimator()
    model.train(x=x, y=y, training_frame=data)
    transformation = model.transform(data)
    prediction = model.predict(data)
    pyunit_utils.compare_frames_local(transformation, prediction)

    # GLM
    model = H2OGeneralizedLinearEstimator(family="binomial")
    model.train(x=x, y=y, training_frame=data)
    transformation = model.transform(data)
    prediction = model.predict(data)
    pyunit_utils.compare_frames_local(transformation, prediction)


if __name__ == "__main__":
    pyunit_utils.standalone_test(pyunit_transform)
else:
    pyunit_transform()
