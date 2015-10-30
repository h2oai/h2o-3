#manually define our threshold for predictions to be 0.3
import pandas as pd
pred = binomial_fit.predict(h2o_df).as_data_frame(use_pandas=True)
pred['predict'] = (pred.p1 > 0.3).astype(int)

