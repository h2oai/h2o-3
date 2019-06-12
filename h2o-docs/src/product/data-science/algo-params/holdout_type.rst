``holdout_type``
----------------

- Available in Data Preparation for Target Encoding

Description
~~~~~~~~~~~
The ``holdout_type`` parameter defines whether the target average should be constructed on all rows of data. Overfitting can be prevented by removing some holdout data when calculating the target average on the training data.

The following holdout types can be specified:

-  ``none``: no holdout. The mean is calculating on all rows of data \*\*. This should be used for test data
-  ``loo``: mean is calculating on all rows of data excluding the row itself.

   -  This can be used for the training data. The target of the row itself is not included in the average to prevent overfitting.

-  ``kfold``: The mean is calculating on out-of-fold data only. (This options requires a fold column.)

   -  This can be used for the training data. The target average is calculated on the out of fold data to prevent overfitting

Related Parameters
~~~~~~~~~~~~~~~~~~
- `blended_avg <blended_avg.html>`__
- `noise <noise.html>`__
- `smoothing <smoothing.html>`__
- `inflection_point <inflection_point.html>`__

Example
~~~~~~~

Refer to the `Target Encoding <../../data-munging/target-encoding.html>`__ data munging topic to view a detailed example.