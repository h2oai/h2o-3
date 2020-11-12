``blending``
---------------

- Available in: Target Encoding

Description
~~~~~~~~~~~

The ``blending`` parameter defines whether the target average should be weighted based on the count of the group. It is often the case that some groups may have a small number of records and the target average will be unreliable. To prevent this, the blended average takes a weighted average of the group's target value and the global target value.

Related Parameters
~~~~~~~~~~~~~~~~~~
- `inflection_point <inflection_point.html>`__
- `smoothing <smoothing.html>`__
- `data_leakage_handling <data_leakage_handling.html>`__
- `noise <noise.html>`__

Example
~~~~~~~

Refer to the `Target Encoding <../target-encoding.html>`__ topic to view a detailed example.
