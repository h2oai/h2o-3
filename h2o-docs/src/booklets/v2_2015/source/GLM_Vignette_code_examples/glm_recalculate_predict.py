#manually define threshold for predictions to 0.3
import pandas as pd
pred = binomial_fit.predict(h2o_df)
pred['predict'] = pred['p1']>0.3

