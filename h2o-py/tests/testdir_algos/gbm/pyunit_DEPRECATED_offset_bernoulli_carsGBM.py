import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils




def offset_bernoulli_cars():
    # Connect to a pre-existing cluster


    cars = h2o.upload_file(pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    cars = cars[cars["economy_20mpg"].isna() == 0]
    cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
    offset = h2o.H2OFrame([[.5 for x in range(398)]])
    offset.set_names(["x1"])
    cars = cars.cbind(offset)

    gbm = h2o.gbm(x=cars[2:8], y=cars["economy_20mpg"], distribution="bernoulli", ntrees=1, max_depth=1, min_rows=1,
                  learn_rate=1, offset_column="x1", training_frame=cars)

    predictions = gbm.predict(cars)

    # Comparison result generated from R's gbm:
    #	gg = gbm(formula = economy_20mpg~cylinders+displacement+power+weight+acceleration+year+offset(rep(.5,398)),
    #            distribution = "bernoulli",data = df,n.trees = 1,interaction.depth = 1,n.minobsinnode = 1,shrinkage = 1,
    #            train.fraction = 1,bag.fraction = 1)
    #   pr = predict.gbm(object = gg,newdata = df,n.trees = 1,type = "link")
    #   pr = 1/(1+exp(-df$x1 - pr))
    assert abs(-0.1041234 - gbm._model_json['output']['init_f']) < 1e-6, "expected init_f to be {0}, but got {1}". \
        format(-0.1041234, gbm._model_json['output']['init_f'])
    assert abs(0.577326 - predictions[:,2].mean()[0]) < 1e-6, "expected prediction mean to be {0}, but got {1}". \
        format(0.577326, predictions[:,2].mean()[0])
    assert abs(0.1621461 - predictions[:,2].min()) < 1e-6, "expected prediction min to be {0}, but got {1}". \
        format(0.1621461, predictions[:,2].min())
    assert abs(0.8506528 - predictions[:,2].max()) < 1e-6, "expected prediction max to be {0}, but got {1}". \
        format(0.8506528, predictions[:,2].max())



if __name__ == "__main__":
    pyunit_utils.standalone_test(offset_bernoulli_cars)
else:
    offset_bernoulli_cars()
