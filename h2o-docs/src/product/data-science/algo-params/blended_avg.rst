``blended_avg``
---------------

- Available in Data Preparation for Target Encoding

Description
~~~~~~~~~~~

The ``blended_avg`` parameter defines whether the target average should be weighted based on the count of the group. It is often the case, that some groups may have a small number of records and the target average will be unreliable. To prevent this, the blended average takes a weighted average of the group's target value and the global target value.

Related Parameters
~~~~~~~~~~~~~~~~~~
- `holdout_type <holdout_type.html>`__
- `noise <noise.html>`__
- `smoothing <smoothing.html>`__
- `inflection_point <inflection_point.html>`__

Example
~~~~~~~

Refer to the `Target Encoding <../../data-munging/target-encoding.html>`__ data munging topic to view a detailed example.