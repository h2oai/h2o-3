Word2vec
--------

Introduction
~~~~~~~~~~~~

The Word2vec algorithm takes a text `corpus <https://en.wikipedia.org/wiki/Corpus_linguistics>`__ as an input and produces the word vectors as output. The algorithm first creates a vocabulary from the training text data and then learns vector representations of the words. The vector space can include hundreds of dimensions, with each unique word in the sample corpus being assigned a corresponding vector in the space. In addition, words that share similar contexts in the corpus are placed in close proximity to one another in the space. The result is an H2O Word2vec model that can be exported as a binary model or as a MOJO. This file can be used as features in many natural language processing and machine learning applications. 

**Note**: This Word2vec implementation is written in Java and is not compatible with other implementations that, for example, are written in C++. In addition, importing models in binary format is not supported.

Demos
~~~~~

- A Word2vec demo in R using a Craigslist job titles dataset available `here <https://github.com/h2oai/h2o-3/blob/master/h2o-r/demos/rdemo.word2vec.craigslistjobtitles.R>`__.
- A Word2vec demo in Python using a Craigslist job titles dataset available `here <https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/word2vec_craigslistjobtitles.ipynb>`__.

Defining a Word2vec Model
~~~~~~~~~~~~~~~~~~~~~~~~~

-  `model_id <algo-params/model_id.html>`__: (Optional) Specify a custom name for the model to use as a reference. By default, H2O automatically generates a destination key.

-  `training_frame <algo-params/training_frame.html>`__: (Required) Specify the dataset used to build the model. The ``training_frame`` should be a single column H2OFrame that is composed of the tokenized text. (Refer to :ref:`tokenize` in the Data Manipulation section for more information.) **NOTE**: In Flow, if you click the **Build a model** button from the ``Parse`` cell, the training frame is entered automatically.

-  `max_runtime_secs <algo-params/max_runtime_secs.html>`__: Maximum allowed runtime in seconds for model training. This option defaults to 0 (disabled) by default.

- **min_word_freq**: Specify an integer for the minimum word frequency. Word2vec will discard words that appear less than this number of times.

- **word_model**: Specify "SkipGram" to use the Skip-Gram model when producing a distributed representation of words. When enabled, the model uses each word to predict the surrounding window of context words. The skip-gram architecture weighs close context words more heavily than more distant context words. Using Skip-Gram can increase model build time but performs better for infrequently used words. Specify "CBOW" to use continuous bag-of-words model, in which case the surrounding context words are used without taking the distance into account.

- **norm_model**: Specify "HSM" to use Hierarchical Softmax. When enabled, Word2vec uses a `Huffman tree <https://en.wikipedia.org/wiki/Huffman_coding>`__ to reduce calculations when approximating the conditional log-likelihood that the model is attempting to maximize. This option is useful for infrequent words, but this option becomes less useful as training epochs increase. **NOTE**: This option is specified by default and cannot be disabled. It is currently the only approach supported in H2O. 

- **vec_size**: Specify the size of word vectors.

- **window_size**: This specifies the size of the context window around a given word. For example, consider the following string:

   "Lorem ipsum (dolor sit amet, quot hendrerit) pri cu,..."

  For a target word, "amet" and ``window size=2``, the context is made of words: dolor, sit, quot, hendrerit.

- **sent_sample_rate**: Set the threshold for the occurrence of words. Those words that appear with higher frequency in the training data will be randomly down-sampled. An ideal range for this option 0, 1e-5.

- **init_learning_rate**: Set the starting learning rate.

- **epochs**: Specify the number of training iterations to run.

- **pre_trained**: Specify the ID of a data frame that contains a pre-trained (external) Word2vec model.

-  `export_checkpoints_dir <algo-params/export_checkpoints_dir.html>`__: Specify a directory to which generated models will automatically be exported.

Interpreting a Word2vec Model
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

By default, the following output displays:

-  Model parameters
-  Output (model category, model summary, cross validation metrics, validation metrics)
-  Column names
-  Domains (for categorical columns)

Tokenize Strings
~~~~~~~~~~~~~~~~

When running a Word2Vec model, you might find it useful to tokenize your text. Tokenizing text converts strings into tokens, then stores the tokenized text into a single column, making it easier for additional processing. Refer to :ref:`tokenize` in the Data Manipulation section for more information. 

Finding Synonyms
~~~~~~~~~~~~~~~~

A ``find_synonyms`` function can be used to find synonyms in a Word2vec model. This function has the following usage:

.. tabs::
   .. code-tab:: r R

    	h2o.findSyonyms(word2vec, word, count)

   .. code-tab:: python

    	h2o.find_synonyms(word2vec, word, count)

- ``word2vec``: A Word2Vec model.
- ``words``: The word for which you want to find synonyms.
- ``count``: The number of synonyms that will be returned. The are the first instances that the function finds, and the function will stop running after this count is met. This value defaults to 20. 

More information about this function can be found in the H2O-3 GitHub repository:

- R: `https://github.com/h2oai/h2o-3/blob/master/h2o-r/h2o-package/R/w2vutils.R#L2 <https://github.com/h2oai/h2o-3/blob/master/h2o-r/h2o-package/R/w2vutils.R#L2>`__
- Python: `https://github.com/h2oai/h2o-3/blob/master/h2o-py/h2o/model/word_embedding.py#L17 <https://github.com/h2oai/h2o-3/blob/master/h2o-py/h2o/model/word_embedding.py#L17>`__

Transforming Words to Vectors
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

A ``transform`` function is available for use with Word2vec. This function transforms words to vectors using an existing Word2Vec model and has the following usage (in both R and Python):

::

  h2o.transform(word2vec, words, aggregate_method)

- ``word2vec``: A Word2Vec model
- ``words``: An H2O Frame made of a single column containing source words. Note that you can specify to include a subset of this frame.
- ``aggregate_method``: Specifies how to aggregate sequences of words. If the method is ``NONE``, then no aggregation is performed, and each input word is mapped to a single word-vector. If the method is ``AVERAGE``, then the input is treated as sequences of words delimited by NA. Each word of a sequences is internally mapped to a vector, and vectors belonging to the same sentence are averaged and returned in the result.

More information about the ``h2o.transform()`` function can be found in the H2O-3 GitHub repository:

- R: `https://github.com/h2oai/h2o-3/blob/master/h2o-r/h2o-package/R/w2vutils.R#L33 <hhttps://github.com/h2oai/h2o-3/blob/master/h2o-r/h2o-package/R/w2vutils.R#L33>`__
- Python: `https://github.com/h2oai/h2o-3/blob/master/h2o-py/h2o/model/word_embedding.py#L41 <https://github.com/h2oai/h2o-3/blob/master/h2o-py/h2o/model/word_embedding.py#L41>`__

References
~~~~~~~~~~

`Tomas Mikolov, Kai Chen, Greg Corrado, and Jeffrey Dean. "Efficient Estimation of Word Representations in Vector Space." In Proceedings of Workshop at ICLR. (Sep 2013) <https://arxiv.org/pdf/1301.3781.pdf>`__

`Tomas Mikolov, Ilya Sutskever, Kai Chen, Greg Corrado, and Jeffrey Dean. "Distributed Representations of Words and Phrases and their Compositionality." In Proceedings of NIPS. (Oct 2013) <https://arxiv.org/pdf/1310.4546.pdf>`__

`Tomas Mikolov, Wen-tau Yih, and Geoffrey Zweig. "Linguistic Regularities in Continuous Space Word Representations." In Proceedings of NAACL HLT. (May 2013) <https://www.microsoft.com/en-us/research/publication/linguistic-regularities-in-continuous-space-word-representations/?from=http%3A%2F%2Fresearch.microsoft.com%2Fpubs%2F189726%2Frvecs.pdf>`__

`Tomas Mikolov, Quoc V. Le and Ilya Sutskever. "Exploiting Similarities among Languages for Machine Translation." (Sep 2013) <https://arxiv.org/pdf/1309.4168.pdf>`__
