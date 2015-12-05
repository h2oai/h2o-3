#Gains/Lift Table

The Gains/Lift table evaluates the prediction ability of a binary classification model. The accuracy of the classification model for a random sample is evaluated according to the results when the model is and is not used.

The Gains/Lift Table is particularly useful for direct marketing applications, for example. The gains/lift chart shows the effectiveness of the current model(s) compared to a baseline, allowing users to quickly identify the most useful model.


By default, H2O reports the Gains/Lift table for all binary classification models, except for GLM, which requires an explicit predict call on the dataset. 

The Gains/Lift table is computed using the prediction probability and the true response (class) labels. 

To create a Gains/Lift table, H2O applies the model to the original dataset to find the response probability. 

The data is divided into groups by quantile thresholds of the response probability. The default number of groups is 20; if there are fewer than 20 unique probability values, then the number of groups is reduced to the number of unique quantile thresholds. 

For each group, the lift is calculated as the proportion of observations that are events (targets) in the group to the overall proportion of events (targets). 

The Gains/Lift table also reports for each group the threshold probability value, cumulative data fractions, response rates (proportion of observations that are events in a group), cumulative response rate, event capture rate, cumulative capture rate, gain (difference in percentages between the overall proportion of events and the observed proportion of observations that are events in the group), and cumulative gain. 

During the Gains/Lift calculations, all rows containing missing values (NAs) in either the label (response) or the prediction probability are ignored. 


##Requirements:

The training frame column must contain actual binary class labels.
The prediction column used as the response must contain probabilities.

##Creating a Gains/Lift table

0. Specify the original dataset in the `training_frame` entry field.
0. From the drop-down vactual list, select the column specified in the original dataset.
0. Enter the .hex key of the prediction in the predict entry field.
0. From the drop-down vpredict list, select the column specified in the prediction.
0. Enter the number of rows to include in the table in the groups field. The default is 10.

The quantiles, response rate, lift, and cumulative lift display in the Gains/Lift table.

The quantiles column defines the group for the row. The response rate column lists the likelihood of response, the lift column lists the lift rate, and the cumulative lift column provides the percentage of increase in response based on the lift.

All rows containing NA values in either the label (response) or the prediction probability are ignored. 


##References

Blattberg, Robert C., Byung-Do Kim, and Scott A. Neslin. “Database Marketing: Analyzing and Managing Customers.” Springer, 2008.