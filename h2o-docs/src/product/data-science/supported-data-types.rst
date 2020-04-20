Supported Data Types
====================

When building models, the supported data types varies per algorithm.

- All H2O-3 algos accept data as numerical and categorical.  
- Word2Vec accepts data as text.

If your data includes timestamps, we recommend that you either convert the data to numeric (if you plan to use the data) or ignore timestamp columns.