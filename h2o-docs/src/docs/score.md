# Score

## Scoring Models

Detailed information on the specification for scoring models generated
by a particular Algorithm can be found in the Algorithms section of
the H2O documentation.

In general, models can be scored by choosing the appropriate algorithm
from the Score drop down menu. Each of these scoring processes require
that the testing data be in the same format as the training data.


## Predicting

Often the objective is not just to build and test a model, but to
leverage that model's predictive capability. This feature can be
accessed by going to the drop down menu **Score** and selecting
**Predict**. Data need not include a column for the predicted variable,
but should correspond to the training data in all other
dimensions. Prediction output is a single column vector that
corresponds row for row with the data set submitted for
prediction. This column is assigned a .hex key, which can be found in
the Admin menu under jobs. Prediction output can also be downloaded to
the users working directory as a .csv file by simply clicking the CSV
link at the top of the **Prediction Results** page.

In this section:

- [Multi-model Comparison](comparison)
- [PCA Scoring](pca)
- [Area Under Curve](auc)
- [Confusion Matrix](confusion-matrix)
- [Hit Ratio](hit-ratio)
- [Predict](predict)

