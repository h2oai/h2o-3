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

- **min_word_freq**: Specify an integer for the minimum word frequency. Word2vec will discard words that appear less than this number of times. This value defaults to 5.

- **word_model**: Specify "SkipGram" (default) to use the Skip-Gram model when producing a distributed representation of words. When enabled, the model uses each word to predict the surrounding window of context words. The skip-gram architecture weighs close context words more heavily than more distant context words. Using Skip-Gram can increase model build time but performs better for infrequently used words. Specify "CBOW" to use continuous bag-of-words model, in which case the surrounding context words are used without taking the distance into account.

- **norm_model**: Specify "HSM" to use Hierarchical Softmax. When enabled, Word2vec uses a `Huffman tree <https://en.wikipedia.org/wiki/Huffman_coding>`__ to reduce calculations when approximating the conditional log-likelihood that the model is attempting to maximize. This option is useful for infrequent words, but this option becomes less useful as training epochs increase. **NOTE**: This option is specified by default and cannot be disabled. It is currently the only approach supported in H2O. 

- **vec_size**: Specify the size of word vectors (defaults to 100).

- **window_size**: This specifies the size of the context window around a given word (defaults to 5). For example, consider the following string:

   "Lorem ipsum (dolor sit amet, quot hendrerit) pri cu,..."

  For a target word, "amet" and ``window size=2``, the context is made of words: dolor, sit, quot, hendrerit.

- **sent_sample_rate**: Set the threshold for the occurrence of words. Those words that appear with higher frequency in the training data will be randomly down-sampled. An ideal range for this option 0, 1e-5. This value defaults to 0.001.

- **init_learning_rate**: Set the starting learning rate (defaults to 0.025).

- **epochs**: Specify the number of training iterations to run (defaults to 5).

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

Examples
~~~~~~~~

This example illustrates how to build a Word2vec model using H2O-3 to analyze job titles from a dataset. It begins by importing the dataset and defining a function to tokenize the job titles, filtering out stop words and short tokens. The Word2vec model is trained on the tokenized job titles to generate word vectors. A gradient boosting model is then created using these vectors to predict job categories. The model also finds synonyms for specific words and provides predictions for various job titles, demonstrating its utility in understanding job title semantics.

.. tabs::
   .. code-tab:: r R

   	library(h2o)
   	h2o.init()

   	# Import the craigslist dataset into H2O:
   	job_title <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/craigslistJobTitles.csv",
                                     col.names = c("category", "jobtitle"), 
                                     col.types = c("Enum", "String"), 
                                     header = TRUE)
   	STOP_WORDS = c("ax", "i", "you", "edu", "s", "t", "m", "subject", "can", 
                       "lines", "re", "what", "there", "all", "we", "one", "the", 
                       "a", "an", "of", "or", "in", "for", "by", "on", "but", "is", 
                       "in", "a", "not", "with", "as", "was", "if", "they", "are",
                       "this", "and", "it", "have", "from", "at", "my", "be", "by",
                       "not", "that", "to", "from", "com", "org", "like", "likes",
                       "so")

   	# Make the 'tokenize' function:
   	tokenize <- function(sentences, stop.words = STOP_WORDS) {
   		tokenized <- h2o.tokenize(sentences, "\\\\W+")
   		tokenized.lower <- h2o.tolower(tokenized)
   		tokenized.lengths <- h2o.nchar(tokenized.lower)
   		tokenized.filtered <- tokenized.lower[is.na(tokenized.lengths) || tokenized.lengths >= 2,]
   		tokenized.words <- tokenized.filtered[h2o.grep("[0-9]", tokenized.filtered, invert = TRUE, output.logical = TRUE),]
   		tokenized.words[is.na(tokenized.words) || (! tokenized.words %in% STOP_WORDS),]
   	}

   	# Make the 'predict' function:
   	.predict <- function(job_title, w2v, gbm) {
   		words <- tokenize(as.character(as.h2o(job_title)))
   		job_title_vec <- h2o.transform(w2v, words, aggregate_method = "AVERAGE")
   		h2o.predict(gbm, job_title_vec)
   	}

   	# Break job titles into sequence of words:
   	words <- tokenize(job_titles$jobtitle)

   	# Build the word2vec model:
   	w2v_model <- h2o.word2vec(words, sent_sample_rate = 0, epochs = 10)

   	# Find synonyms for the word "teacher":
   	print(h2o.findSynonyms(w2v_model, "teacher", count = 5))

   	# Calculate a vector for each job title:
   	job_title_vecs <- h2o.transform(w2v_model, words, aggregate_method = "AVERAGE")

   	# Prepare training & validation data (keep only job titles made of known words):
   	valid_job_titles <- ! is.na(job_title_vecs$C1)
   	data <- h2o.cbind(job.titles[valid_job_titles, "category"], job_title_vecs[valid_job_titles, ])
   	data_split <- h2o.splitFrame(data, ratios = 0.8)

   	# Build a basic GBM model:
   	gbm_model <- h2o.gbm(x = names(job_title_vecs), 
                             y = "category", 
                             training_frame = data_split[[1]], 
                             validation_frame = data_split[[2]])

   	# Predict:
   	print(.predict("school teacher having holidays every month", w2v_model, gbm_model))
   	print(.predict("developer with 3+ Java experience, jumping", w2v_model, gbm_model))
   	print(.predict("Financial accountant CPA preferred", w2v_model, gbm_model))


   .. code-tab:: python

    import h2o
    from h2o.estimators import H2OWord2vecEstimator, H2OGradientBoostingEstimator
    h2o.init()

    # Import the craigslist dataset into H2O:
    job_titles = h2o.import_file(("https://s3.amazonaws.com/h2o-public-test-data/smalldata/craigslistJobTitles.csv"), 
                                  col_names = ["category", "jobtitle"], 
                                  col_types = ["string", "string"], 
                                  header = 1)
    STOP_WORDS = ["ax","i","you","edu","s","t","m","subject","can",
                  "lines","re","what","there","all","we","one","the",
                  "a","an","of","or","in","for","by","on","but","is",
                  "in","a","not","with","as","was","if","they","are",
                  "this","and","it","have","from","at","my","be","by",
                  "not","that","to","from","com","org","like","likes",
                  "so"]

    # Make the 'tokenize' function:
    def tokenize(sentences, stop_word = STOP_WORDS):
    	tokenized = sentences.tokenize("\\W+")
    	tokenized_lower = tokenized.tolower()
    	tokenized_filtered = tokenized_lower[(tokenized_lower.nchar() >= 2) | (tokenized_lower.isna()),:]
    	tokenized_words = tokenized_filtered[tokenized_filtered.grep("[0-9]",invert=True,output_logical=True),:]
    	tokenized_words = tokenized_words[(tokenized_words.isna()) | (~ tokenized_words.isin(STOP_WORDS)),:]
    	return tokenized_words

    # Make the `predict` function:
    def predict(job_title,w2v, gbm):
    	words = tokenize(h2o.H2OFrame(job_title).ascharacter())
    	job_title_vec = w2v.transform(words, aggregate_method="AVERAGE")
    	print(gbm.predict(test_data=job_title_vec))

    # Break job titles into a sequence of words:
    words = tokenize(job_titles["jobtitle"])

    # Build word2vec model:
    w2v_model = H2OWord2vecEstimator(sent_sample_rate = 0.0, epochs = 10)
    w2v_model.train(training_frame=words)

    # Find synonyms for the words "teacher":
    w2v_model.find_synonyms("teacher", count = 5)

    # Calculate a vector for each job title:
    job_title_vecs = w2v_model.transform(words, aggregate_method = "AVERAGE")

    # Prepare training & validation data (keep only job titles made of known words):
    valid_job_titles = ~ job_title_vecs["C1"].isna()
    data = job_titles[valid_job_titles,:].cbind(job_title_vecs[valid_job_titles,:])
    data_split = data.split_frame(ratios=[0.8])

    # Build a basic GBM model:
    gbm_model = H2OGradientBoostingEstimator()
    gbm_model.train(x = job_title_vecs.names, 
                    y="category", 
                    training_frame = data_split[0], 
                    validation_frame = data_split[1])

    # Predict
    print(predict(["school teacher having holidays every month"], w2v_model, gbm_model))
    print(predict(["developer with 3+ Java experience, jumping"], w2v_model, gbm_model))
    print(predict(["Financial accountant CPA preferred"], w2v_model, gbm_model))


References
~~~~~~~~~~

`Tomas Mikolov, Kai Chen, Greg Corrado, and Jeffrey Dean. "Efficient Estimation of Word Representations in Vector Space." In Proceedings of Workshop at ICLR. (Sep 2013) <https://arxiv.org/pdf/1301.3781.pdf>`__

`Tomas Mikolov, Ilya Sutskever, Kai Chen, Greg Corrado, and Jeffrey Dean. "Distributed Representations of Words and Phrases and their Compositionality." In Proceedings of NIPS. (Oct 2013) <https://arxiv.org/pdf/1310.4546.pdf>`__

`Tomas Mikolov, Wen-tau Yih, and Geoffrey Zweig. "Linguistic Regularities in Continuous Space Word Representations." In Proceedings of NAACL HLT. (May 2013) <https://www.microsoft.com/en-us/research/publication/linguistic-regularities-in-continuous-space-word-representations/?from=http%3A%2F%2Fresearch.microsoft.com%2Fpubs%2F189726%2Frvecs.pdf>`__

`Tomas Mikolov, Quoc V. Le and Ilya Sutskever. "Exploiting Similarities among Languages for Machine Translation." (Sep 2013) <https://arxiv.org/pdf/1309.4168.pdf>`__
