``class_sampling_factors``
--------------------------

- Available in: GBM, DRF, Deep Learning
- Hyperparameter: yes

Description
~~~~~~~~~~~

When your datasest includes imbalanced data, you may find it necessary to balance the data using the ``balance_classes`` option. When specified, the algorithm will either undersample the majority classes or oversampling the minority classes. 

By default, sampling factors will be automatically computed to obtain class balance during training. You can change this behavior using the ``class_sampling_factors`` option. This option sets an over/under-sampling ratio for each class (in lexicographic order).

Related Parameters
~~~~~~~~~~~~~~~~~~

- `balance_classes <balance_classes.html>`__
- `max_after_balance_size <max_after_balance_size.html>`__