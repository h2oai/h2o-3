``data_leakage_handling``
-------------------------

- Available in: Target Encoding

Description
~~~~~~~~~~~
The ``data_leakage_handling`` parameter defines the strategy used to compute the target average in order to avoid data leakage. Overfitting can be prevented by removing some holdout data when calculating the target average on the training data.

The following strategies can be specified:

- ``none``: The mean is calculated on all rows of data (no holdout). 

	- This should be used for validation or test data.
	
- ``leave_one_out``: The mean is calculated on all rows of data excluding the row itself.
	
	- This can be used for the training data. The target of the row itself is not included in the average to prevent overfitting.

- ``k_fold``: The mean is calculated on out-of-fold data only. This options requires a fold column.

	- This can be used for the training data. The target average is calculated on the out-of-fold data to prevent overfitting.

Related Parameters
~~~~~~~~~~~~~~~~~~
- `blending <blending.html>`__
- `inflection_point <inflection_point.html>`__
- `smoothing <smoothing.html>`__
- `noise <noise.html>`__

Example
~~~~~~~

Refer to the `Target Encoding <../target-encoding.html>`__ topic to view a detailed example.
