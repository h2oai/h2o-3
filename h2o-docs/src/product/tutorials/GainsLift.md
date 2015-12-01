#Gains/Lift Table

The Gains/Lift Table page uses predicted data to evaluate model performance. The accuracy of the classification model for a random sample is evaluated according to the results when the model is and is not used.

The Gains/Lift Table is particularly useful for direct marketing applications, for example. The gains/lift chart shows the effectiveness of the current model(s) compared to a baseline, allowing users to quickly identify the most useful model.

To create a Gains/Lift table, H2O applies the model to each entry in the original dataset to find the response probability /(^Pi/), then orders the entries according to their predicted response probabilities. Finally, H2O divides the dataset into equal groups and calculates the average response rate for each group.

H2O uses the response rate of the top ten groups to evaluate the model performance; the highest response and greatest variation rates indicate the best model.

The lift is calculated from the gains. H2O uses the following formula to calculate the lift: /(λk=rk/r/)

where /(λk/) is the lift for /(k/), /(rk/) is the response rate for /(k/), and /(r/) is the average response rate for /(k/). In other words, /(λk/) defines how much more likely /(k/) customers are to respond in comparison to the average response rate.

Requirements:

The vactual column must contain actual binary class labels.
The vpredict column must contain probabilities.
To create a Gains/Lift table:

Enter the .hex key of the original dataset in the actual entry field.
From the drop-down vactual list, select the column specified in the original dataset.
Enter the .hex key of the prediction in the predict entry field.
From the drop-down vpredict list, select the column specified in the prediction.
Enter the number of rows to include in the table in the groups field. The default is 10.
The quantiles, response rate, lift, and cumulative lift display in the Gains/Lift table.

The quantiles column defines the group for the row. The response rate column lists the likelihood of response, the lift column lists the lift rate, and the cumulative lift column provides the percentage of increase in response based on the lift.

All rows containing NA values in either the label (response) or the prediction probability are ignored. 


##References

Blattberg, Robert C., Byung-Do Kim, and Scott A. Neslin. “Database Marketing: Analyzing and Managing Customers.” Springer, 2008.