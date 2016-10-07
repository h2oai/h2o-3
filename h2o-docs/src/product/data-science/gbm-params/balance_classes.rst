``balance_classes``
-------------------

- Available in: GBM, DRF, Deep Learning
- Hyperparameter: yes

Description
~~~~~~~~~~~

During model training, you might find that the majority of your data belongs in a single class. For example, consider a binary classification model that has 100 rows, with 80 rows labeled as class 1 and the remaining 20 rows labeled as class 2. This is a common scenario, given that machine learning attempts to predict class 1 with the highest accuracy. It can also be an example of an imbalanced dataset, in this case, with a ratio of 4:1. 

The ``balance_classes`` option can be used to balance the class distribution. When enabled, H2O will either undersample the majority classes or oversample the minority classes. In addition, if this option is enabled, then you can also specify a value for the ``class_sampling_factors`` and ``max_after_balance_size`` options. 

**Notes**:

- This option is disabled by default. 
- This option only applies to classification problems. 
- Enabling this option can increase the size of the data frame.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `class_sampling_factors <class_sampling_factors.html>`__
- `max_after_balance_size <max_after_balance_size.html>`__