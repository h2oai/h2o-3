from builtins import range
import sys, os
sys.path.insert(1, os.path.join("..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators import H2OGradientBoostingEstimator
import numpy as np
import pandas as pd


def prepare_data():
    """
    Generate data with target variable: 

    p(Y) = 1/ (1 + exp(-(-3 + 0.5X1 + 0.5X2 - 0.5X3 + 2X2X3)))
    
    insert Nan into x3

    :return: Dataframe, x, and y
    """

    # Setup the simulation
    n = 1000  # Number of records in the simulated data

    # Simulate four predictors
    np.random.seed(0)
    x1 = np.random.normal(0, 1 ,size=n)
    np.random.seed(1)
    x2 = np.random.normal(0, 1, size=n)
    np.random.seed(2)
    x3 = np.random.normal(0, 1, size=n)
    np.random.seed(3)
    x4 = np.random.binomial(1, 0.5, size=n)
    np.random.seed(4)
    x5 = np.random.binomial(1, 0.5, size=n)

    # Simulate a ranuni for assigning Y
    np.random.seed(5)
    r = np.random.uniform(0, 1, size=n)

    # Put values in dataframe
    df = pd.DataFrame( {'id': range(1, n+1), 'x1': x1, 'x2': x2, 'x3': x3, 'x4': x4, 'x5': x5, 'r': r})

    # Define the linear predictor
    b0 = -3
    b1 = 0.5
    b2 = 0.5
    b3 = -0.5
    b12 = 0
    b13 = 0
    b23 = 2
    df['LP'] = b0 + b1*df['x1'] + b2*df['x2'] + b3*df['x3'] + b12*df['x1']*df['x2'] + b13*df['x1']*df['x3'] + b23*df['x2']*df['x3']

    # Convert it to a probability
    df['P'] = 1/(1+np.exp(-df['LP']))

    # Convert it to a binary target
    df['Y'] = (df['r']<df['P']).astype(int)
    
    # Insert Nan into x3
    df.loc[0, 'x3'] = np.nan
    df.loc[2, 'x3'] = np.nan

    # Define target and predictors
    x = ['x1', 'x2', 'x3', 'x4', 'x5']
    y = 'Y'

    return df, x, y


def h_stats_on_synthetic_data_with_missing_values():
    df, x, target = prepare_data()
    print(df.head())

    # Create the data frame
    train_frame = h2o.H2OFrame(df[x + [target]])
    train_frame[target] = train_frame[target].asfactor()

    # Train model
    gbm_h2o = H2OGradientBoostingEstimator(ntrees=10, learn_rate=0.1, max_depth=2, min_rows=1, seed=1234)
    gbm_h2o.train(x=x, y=target, training_frame=train_frame)
    
    # Calculate H stats with column includes missing values
    h = gbm_h2o.h(train_frame, ['x1', 'x3'])
    print(h)
        

if __name__ == "__main__":
    pyunit_utils.standalone_test(h_stats_on_synthetic_data_with_missing_values)
else:
    h_stats_on_synthetic_data_with_missing_values()
