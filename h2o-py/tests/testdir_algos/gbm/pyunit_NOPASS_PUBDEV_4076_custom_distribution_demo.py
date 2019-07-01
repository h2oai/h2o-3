import sys

sys.path.insert(1, "../../../")

import h2o
from tests import pyunit_utils

import pandas as pd

from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.utils.distributions import CustomDistributionGaussian


def prepare_date(data, date_column):
    data[date_column] = pd.to_datetime(data[date_column])
    index = pd.DatetimeIndex(data[date_column])
    data["year"] = index.year
    data["month"] = index.month
    data["day"] = index.day
    return data


def group_by_agg_lags(data):
    g = data.groupby(["store", "item"]).sales

    # lags older than 92 to be aple to prepare test data correctly 
    for i in range(92, 366):
        data["lag_"+str(i)] = g.shift(i)
    data.dropna(inplace=True, axis=0)

    data["mean_last_year_week_7"] = data.iloc[:,266:274].mean(axis=1)
    data["mean_last_year_week_6"] = data.iloc[:,267:275].mean(axis=1)
    data["mean_last_year_week_5"] = data.iloc[:,268:276].mean(axis=1)
    data["mean_last_year_week_4"] = data.iloc[:,269:277].mean(axis=1)
    data["mean_last_year_week_3"] = data.iloc[:,270:278].mean(axis=1)
    data["mean_last_year_week_2"] = data.iloc[:,271:279].mean(axis=1)
    data["mean_last_year_week_1"] = data.iloc[:,272:280].mean(axis=1)
    data["moving_mean_last_year_week"] = data.iloc[:,280:287].mean(axis=1)
    data["sum_last_year_week"] = data.iloc[:,272:280].sum(axis=1)
    return data


# evaluate number of good predictions
def evaluate(test, predictions):
    predictions["actual"] = test.sales.values
    predictions.columns = ["prediction", "actual"]
    predictions["residual"] = predictions.actual - predictions.prediction
    predictions["fit"] = 0
    predictions.loc[predictions.residual < 0, "fit"] = 0
    predictions.loc[predictions.residual >= 0, "fit"] = 1
    items = predictions.shape[0]
    more_or_perfect = sum(predictions.fit)
    less = items - more_or_perfect
    return items, less, more_or_perfect


class AsymmetricLossDistribution(CustomDistributionGaussian):

    def gradient(self, y, f):
        error = y - f
        # smaller predicted sales value is better error than the bigger one
        return 10 * error if error < 0 else 0.5 * error
        # return error


def test_gaussian():
    # load data 
    data = pd.read_csv(pyunit_utils.locate("smalldata/demos/item_demand.csv"))
    # prepare date columns
    data = prepare_date(data, "date")

    # aggregate data, prepare lags, remove null rows
    data = group_by_agg_lags(data)
    print("New data shape:")
    print(data.shape)

    # split to train and test
    train = data[data.date <= "2017-09-30"]
    print("Training data shape:")
    print(train.shape)

    # last 3 month for testing  
    test = data[data.date > "2017-09-30"]
    print("Testing data shape:")
    print(test.shape)

    # load data to H2o
    train_h2o = h2o.H2OFrame(train)
    test_h2o = h2o.H2OFrame(test)

    # Train GBM model with gaussian 
    gbm_gaussian = H2OGradientBoostingEstimator(model_id="sales_model",
                                                ntrees=50,
                                                max_depth=5,
                                                score_each_iteration=True,
                                                distribution="gaussian")
    gbm_gaussian.train(y="sales", x=train_h2o.names, training_frame=train_h2o)
    predictions = gbm_gaussian.predict(test_data=test_h2o).as_data_frame()

    items, less, more_or_perfect = evaluate(test, predictions)
    print("Non customized gaussian loss function")
    print(items, less, more_or_perfect)

    name = "asymmetric"
    distribution_ref = h2o.upload_custom_distribution(AsymmetricLossDistribution, func_name="custom_"+name,
                                                      func_file="custom_"+name+".py")

    gbm_custom = H2OGradientBoostingEstimator(model_id="custom_sales_model",
                                              ntrees=50,
                                              max_depth=5,
                                              score_each_iteration=True,
                                              distribution="custom",
                                              custom_distribution_func=distribution_ref)
    gbm_custom.train(y="sales", x=train_h2o.names, training_frame=train_h2o)

    predictions = gbm_custom.predict(test_data=test_h2o).as_data_frame()
    items, less, more_or_perfect = evaluate(test, predictions)
    print("Customized gaussian loss function")
    print(items, less, more_or_perfect)
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gaussian)
else:
    test_gaussian()
