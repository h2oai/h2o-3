import sys
sys.path.insert(1, "../../../")
import h2o

def offsets_and_distributions(ip,port):

    # cars
    cars = h2o.upload_frame(h2o.locate("smalldata/junit/cars_20mpg.csv"))
    cars = cars[cars["economy_20mpg"].isna() == 0]
    cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
    offset = h2o.H2OFrame(python_obj=[[.5] for x in range(398)])
    offset.setNames(["x1"])
    cars = cars.cbind(offset)

    # insurance
    insurance = h2o.import_frame(h2o.locate("smalldata/glm_test/insurance.csv"))
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
    dl = h2o.deeplearning(x=insurance.names()[0:3], y="Claims", distribution="tweedie", offset_column="offset", training_frame=insurance)
    predictions = dl.predict(insurance)

if __name__ == "__main__":
    h2o.run_test(sys.argv, offsets_and_distributions)
