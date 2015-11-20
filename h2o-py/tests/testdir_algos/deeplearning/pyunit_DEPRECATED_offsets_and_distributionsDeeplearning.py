import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils


def offsets_and_distributions():

    # cars
    cars = h2o.upload_file(pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    cars = cars[cars["economy_20mpg"].isna() == 0]
    cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
    offset = h2o.H2OFrame([[.5]*398])
    offset.set_name(0,"x1")
    cars = cars.cbind(offset)

    # insurance
    insurance = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/insurance.csv"))
    insurance["offset"] = insurance["Holders"].log()

    # bernoulli - offset not supported
    #dl = h2o.deeplearning(x=cars[2:8], y=cars["economy_20mpg"], distribution="bernoulli", offset_column="x1",
    #                       training_frame=cars)
    #predictions = dl.predict(cars)

    # gamma
    dl = h2o.deeplearning(x=insurance[0:3], y=insurance["Claims"], distribution="gamma", offset_column="offset", training_frame=insurance)
    predictions = dl.predict(insurance)

    # gaussian
    dl = h2o.deeplearning(x=insurance[0:3], y=insurance["Claims"], distribution="gaussian", offset_column="offset", training_frame=insurance)
    predictions = dl.predict(insurance)

    # poisson
    dl = h2o.deeplearning(x=insurance[0:3], y=insurance["Claims"], distribution="poisson", offset_column="offset", training_frame=insurance)
    predictions = dl.predict(insurance)

    # tweedie
    dl = h2o.deeplearning(x=insurance.names[0:3], y="Claims", distribution="tweedie", offset_column="offset", training_frame=insurance)
    predictions = dl.predict(insurance)



if __name__ == "__main__":
    pyunit_utils.standalone_test(offsets_and_distributions)
else:
    offsets_and_distributions()
