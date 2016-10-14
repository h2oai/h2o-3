Missing Values (Categorical/Factors)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- **How does the algorithm handle missing values during training?**

 Missing values are interpreted as containing information (i.e., missing for a reason), rather than missing at random. During tree building, split decisions for every node are found by minimizing the loss function and treating missing values as a separate category that can go either left or right. 

 So how does the algorithm determine what goes left or right? The algo decides which feature and level/number value to split on its decision to make things go left or right is purely based on value (i.e. everything less than the split point value goes left and everything greater and equal goes right). This means that missing values can go left or right, in the same way as any other categorical, since Nas are now seen as a new categorical.

 A note on ``nbins_cats``: This parameter specifies the number of bins to use for non-Na categoricals, it does not bin missing values. Regardless of the number of bins a user chooses for ``nbins_cats``, an additional bin will be created specifically for the missing values. 

- **What loss function was used?**

 During scoring, missing values follow the optimal path that was determined for them during training (minimized loss function). Note that test-NAs follow the majority direction - the direction through which the most observations flow - if there were no NAs in training.

- **What happens if the response has missing values?**

 No errors will occur, but at the same time, nothing will be learned from rows containing missing the response.

- **What happens when you try to predict on a categorical level not seen during training?**

 Unseen categorical levels follow the majority path for each node. The tree structure remembers for each split whether the left or right side had more observations going down that path during training.