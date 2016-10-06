``max_after_balance_size``
--------------------------

- Available in: GBM, DRF, Deep Learning
- Hyperparameter: yes

Description
~~~~~~~~~~~

When your datasest includes imbalanced data, you may find it necessary to balance the data using the ``balance_classes`` option. When specified, the algorithm will either undersample the majority classes or oversampling the minority classes. In most cases, though, enabling the ``balance_classes`` option will increase the data frame size. To reduce the data frame size, you can use the ``max_after_balance_size`` option. This specifies the maximum relative size of the training data after balancing class counts. 

Related Parameters
~~~~~~~~~~~~~~~~~~

- `balance_classes <balance_classes.html>`__
- `class_sampling_factors <class_sampling_factors.html>`__