import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator as gam
import pandas as pd

def generate_graphs():
    #   train = h2o.import_file("/Users/wendycwong/temp/gam_1Col_2000rows.csv")
    input = h2o.import_file("good-dataset.csv")
    frames = input.split_frame(ratios=[0.05])   # can change the ratio to higher, I was running out of memory
    train = frames[0]
    response = 'response'
    scale_parameter = [1e-5, 0.0001, 0.001, 0.01, 0.1, 1, 10, 1e2, 1e3, 1e4]
    # num_knots = [int(0.1*train.nrow), int(train.nrow*0.2), int(0.3*train.nrow), int(0.4*train.nrow), int(train.nrow*0.5)]
    # scale_parameter = [1e-5]
    num_knots = [int(0.5*train.nrow)]
    xval_mse_total = []
    val_mse_total = []
    for numKnots in num_knots:
        xval_mse = []
        val_mse = []
        for scale in scale_parameter:
            gam_model = gam(family = "gaussian", alpha = 0, Lambda = 0, gam_columns = ["C1"], scale = [scale],
                            nfolds = train.nrow, fold_assignment="modulo", num_knots=[numKnots])
            gam_model.train(x=[], y=response, training_frame = train, validation_frame=frames[1])
            xval_mse.append(gam_model.mse(xval=True))
            val_mse.append(gam_model.mse(valid=True))
        xval_mse_total.append(xval_mse)
        val_mse_total.append(val_mse)
        print(xval_mse_total)
        print(val_mse_total)
    
    df = pd.DataFrame.from_dict(xval_mse_total[0])
    df.to_csv("bowl5.csv")
    print("done")
    # print(xval_mse_total[0])
    # print(val_mse_total)

if __name__ == "__main__":
    h2o.init(ip = "192.168.1.4", port = 54321, strict_version_check=False)
    pyunit_utils.standalone_test(generate_graphs())
else:
    h2o.init(ip = "192.168.1.4", port = 54321, strict_version_check=False)
    generate_graphs()
