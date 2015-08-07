import sys
sys.path.insert(1, "../../../")
import h2o

def offset_bernoulli_cars(ip,port):
    # Connect to a pre-existing cluster
    h2o.init(ip,port)

    cars = h2o.upload_file(h2o.locate("smalldata/junit/cars_20mpg.csv"))
    cars = cars[cars["economy_20mpg"].isna() == 0]
    cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
    offset = h2o.H2OFrame(python_obj=[[.5] for x in range(398)])
    offset.set_names(["x1"])
    cars = cars.cbind(offset)

    gbm = h2o.gbm(x=cars[2:8], y=cars["economy_20mpg"], distribution="bernoulli", ntrees=1, max_depth=1, min_rows=1,
                  learn_rate=1, offset_column=cars["x1"], training_frame=cars)

    predictions = gbm.predict(cars)

    # Comparison result generated from R's gbm:
    #	gg = gbm(formula = economy_20mpg~cylinders+displacement+power+weight+acceleration+year+offset(rep(.5,398)),
    #            distribution = "bernoulli",data = df,n.trees = 1,interaction.depth = 1,n.minobsinnode = 1,shrinkage = 1,
    #            train.fraction = 1,bag.fraction = 1)
    #   pr = predict.gbm(object = gg,newdata = df,n.trees = 1,type = "link")
    #   pr = 1/(1+exp(-df$x1 - pr))

    print "init_f: abs(-0.1041234 - gbm._model_json['output']['init_f'])"
    print abs(-0.1041234 - gbm._model_json['output']['init_f'])

    print "pred mean: abs(0.577326 - predictions[:,2].mean())"
    print abs(0.577326 - predictions[:,2].mean())

    print "pred min:  abs(0.1621461 - predictions[:,2].min())"
    print  abs(0.1621461 - predictions[:,2].min())

    print "pred max: abs(0.8506528 - predictions[:,2].max())"
    print abs(0.8506528 - predictions[:,2].max())

if __name__ == "__main__":
    h2o.run_test(sys.argv, offset_bernoulli_cars)
