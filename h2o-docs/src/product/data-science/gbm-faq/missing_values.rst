Missing Values 
^^^^^^^^^^^^^^

**Note**: Unlike in GLM, in GBM numerical values are handled the same way as categorical values. Missing values are not imputed with the mean, as is done by default in GLM.

**Brief Overview of Missing Values Handling**

During training in GBM, the optimal split direction for every feature value (numeric and categorical, including missing values/NAs) is computed for future use during scoring. This means that missing numeric, categorical, or unseen categorical values are turned into NAs.

Specifically, if there are no NAs in the training data, then NAs in the test data follow the majority direction (the direction with the most observations). If there are NAs in the training data, then NAs in the test data follow the direction that is optimal for the NAs of the training data.

- **How does the algorithm handle missing values during training?**

 Missing values are interpreted as containing information (i.e., missing for a reason), rather than missing at random. During tree building, split decisions for every node are found by minimizing the loss function and treating missing values as a separate category that can go either left or right. 

 So how does the algorithm determine what goes left or right? The algo decides which feature and level/number value to split on its decision to make things go left or right is purely based on value (i.e. everything less than the split point value goes left, and everything greater and equal goes right). This means that missing values can go left or right in the same way as any other categorical because NAs are now seen as a new categorical.

 A note on ``nbins_cats``: This parameter specifies the number of bins to use for non-Na categoricals, it does not bin missing values. Regardless of the number of bins a user chooses for ``nbins_cats``, an additional bin will be created specifically for the missing values. 

- **How does the algorithm handle missing values during testing?** 

 During scoring, missing values follow the optimal path that was determined for them during training (minimized loss function).

- **What loss function was used?**

 The loss function is automatically chosen based on the distribution parameter selection. Refer to the :ref:`What loss function is automatically chosen for each of these distributions? <lossfunction>` question in the `About the Data <about_the_data.html>`__ section.

- **How does the algorithm handle missing values during testing?**

 During scoring, missing values follow the optimal path that was determined for them during training (minimized loss function). Note that test-NAs follow the majority direction - the direction through which the most observations flow - if there were no NAs in training.

- **What happens if the response has missing values?**

 No errors will occur, but at the same time, nothing will be learned from rows containing missing values in the response column.

- **What happens when you try to predict on a categorical level not seen during training?**

 Unseen categorical levels are turned into NAs, and thus follow the same behavior as an NA. If there are no NAs in the training data, then unseen categorical levels in the test data follow the majority direction (the direction with the most observations). If there are NAs in the training data, then unseen categorical levels in the test data follow the direction that is optimal for the NAs of the training data.
