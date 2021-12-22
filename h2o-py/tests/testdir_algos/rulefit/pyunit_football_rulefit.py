import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.rulefit import H2ORuleFitEstimator


def football():
    df = h2o.import_file("https://h2o-public-test-data.s3.amazonaws.com/mli-testing/manual-test/small-dataset/binomial/football_prediction.csv")
    df["FTR"] = df["FTR"].asfactor()
    x = df.columns
    y = "FTR"
    x.remove(y)
    x.remove("Date")

    # Split the dataset into train and test
    train, test = df.split_frame(ratios=[.8], seed=1234)

    rfit = H2ORuleFitEstimator(min_rule_length=1, max_rule_length=3, max_num_rules=10, seed=1234, model_type="rules_and_linear")
    rfit.train(training_frame=train, x=x, y=y, validation_frame=test)

    df[y] = df[y].append_levels(["extra_level"])

    # Split the dataset into train and test
    train, test = df.split_frame(ratios=[.8], seed=1234)

    rfit_multi = H2ORuleFitEstimator(min_rule_length=1, max_rule_length=3, max_num_rules=10, seed=1234, 
                                     model_type="rules_and_linear", distribution="multinomial")
    rfit_multi.train(training_frame=train, x=x, y=y, validation_frame=test)

    # Print rules and metrics for comparision:
    print("Binomial model rules:")
    print(rfit.rule_importance())

    print("Multinomial model rules:")
    print(rfit_multi.rule_importance())
    
    print("Binomial train RMSE vs. multinomial train RMSE:")
    print(str(rfit.rmse()) + " vs. " + str(rfit_multi.rmse()))
    print("Binomial train MSE vs. multinomial train MSE: ")
    print(str(rfit.mse()) + " vs. " + str(rfit_multi.mse()))
    print("Binomial valid RMSE vs. multinomial valid RMSE: ")
    print(str(rfit.rmse(valid=True)) + " vs. " + str(rfit_multi.rmse(valid=True)))
    print("Binomial valid MSE vs. multinomial valid MSE: ")
    print(str(rfit.mse(valid=True)) + " vs. " + str(rfit_multi.mse(valid=True)))



if __name__ == "__main__":
  pyunit_utils.standalone_test(football)
else:
    football()
