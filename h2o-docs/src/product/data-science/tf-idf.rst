TF-IDF
---------

Introduction
~~~~~~~~~~~~~~~~~~~

TF-IDF (Term Frequency–Inverse Document Frequency) is a statistical measure that aims to reflect how important a word is to a document in a collection of documents (also known as a `corpus <https://en.wikipedia.org/wiki/Corpus_linguistics>`__).

Definition
~~~~~~~~~~~~~~~~~~~

TF-IDF, as its name suggest, is composed from 2 different statistical measures. TF-IDF is equal to a product of TF (term frequency) and IDF (inverse document frequency). Terms used in the following equations:

- :math:`D` - a collection of documents (corpus)
- :math:`d` - a document from corpus :math:`D`
- :math:`w` - a word from some document :math:`d`

TF (Term Frequency)
<<<<<<<<<<<<<<<<<<<
TF is a statistical measure expressing how frequently does word appear in a document. The implementation used in H2O TF-IDF is as follows:

:math:`TF(w,d)` - number of occurrences of a word :math:`w` in a document :math:`d`

IDF (Inverse Document Frequency)
<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
IDF is a statistical measure expressing how much information does the word provide. To put it simply, it expresses whether it is a common or rare word across all documents. IDF is computed using a statistical measure named DF (Document Frequency). The implementation of DF used H2O IDF is as follows:

:math:`DF(w)` - number of documents from :math:`D` which contain a word :math:`w`

.. math::
    DF(w) = |\{d \in D \mid w \in d\}|

The implementation of IDF used in H2O TF-IDF is as follows:

.. math::
    IDF(w,d) = log\frac{|D| + 1}{DF(w) + 1}

where natural logarithm is being used (i.e. the logarithm has a base equal to :math:`e`). Based on the equation above, IDF of a word present in all documents from the corpus is equal to 0, and the fewer documents contain the word, the higher its IDF value.

TF-IDF is defined as a product of the TF and IDF measures explained above:

.. math::
    TFIDF(w,d) = TF(w,d) * IDF(w)

Parameters
~~~~~~~~~~~~~~~~~~~

- **frame**: Documents or words frame for which TF-IDF values should be computed.
- **document_id_col**: Index or name of a column containing document IDs.
- **text_col**: Index or name of a column containing documents if data should be pre-processed or words if input data is already pre-processed (defined by **preprocess** parameter).
- **preprocess**: (Optional) A flag specifying whether input text data should be pre-processed. By default, data is pre-processed.
- **case_sensitive**: (Optional) A flag specifying whether input data should be treated as case sensitive. By default, input data is treated as case sensitive.

Output
~~~~~~~~~~~~~~~~~~~

Output is a H2OFrame with rows consisting of document ID, word and its corresponding TF, IDF and TF-IDF values.

Examples
~~~~~~~~~~~~~~~~~~~

There is a jupyter notebook with Python demo available `here <https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/tf-idf.ipynb>`__.

.. tabs::
   .. code-tab:: r R

    library(h2o)
    
    h2o.init()

    # Construct data
    documents = c(
        'H2O is an in-memory platform for distributed, scalable machine learning. H2O uses familiar interfaces like R, Python, Scala, Java, JSON and the Flow notebook/web interface, and works seamlessly with big data technologies like Hadoop and Spark.',
        'Ice hockey is a contact team sport played on ice, usually in a rink, in which two teams of skaters use their sticks to shoot a vulcanized rubber puck into their opponent\'s net to score goals. The sport is known to be fast-paced and physical.',
        'An antibody (Ab), also known as an immunoglobulin (Ig), is a large, Y-shaped protein produced mainly by plasma cells that is used by the immune system to neutralize pathogens such as pathogenic bacteria and viruses.'
    )
    doc_ids = seq(0, length(documents) - 1)
    
    # Create H2OFrame
    input.r_data <- data.frame(doc_ids, documents, stringsAsFactors=FALSE)
    colnames(input.r_data) <- c('DocID', 'Document')
    input.h2o_data <- as.h2o(input.r_data)
    
    doc_id_col_idx <- 0
    document_col_idx <- 1
    
    # Compute TF-IDF values using non-preprocessed data (pre-processing is done by TF-IDF) - case sensitive
    tf_idf.out <- h2o.tf_idf(input.h2o_data, doc_id_col_idx, document_col_idx)
    
    # Compute TF-IDF values using non-preprocessed data (pre-processing is done by TF-IDF) - case insensitive
    tf_idf.out <- h2o.tf_idf(input.h2o_data, doc_id_col_idx, document_col_idx, case_sensitive=FALSE)
    
    # Construct "preprocessed" data (more complex tokenizing techniques can be used
    words <- c()
    preprocessed_doc_ids <- c()
    for (i in seq(1, length(documents))) {
        document_words <- strsplit(documents[i], '\\s+')[[1]]
        words <- c(words, document_words)
        preprocessed_doc_ids <- c(preprocessed_doc_ids, rep(doc_ids[i], length(document_words)))
    }

    # Create H2OFrame
    preprocessed_input.r_data <- data.frame(preprocessed_doc_ids, words, stringsAsFactors=FALSE)
    colnames(preprocessed_input.r_data) <- c('DocID', 'Word')
    preprocessed_input.h2o_data <- as.h2o(preprocessed_input.r_data)
    
    doc_id_col_idx <- 0
    word_col_idx <- 1
    
    # Compute TF-IDF values using already preprocessed data (pre-processing step in TF-IDF is skipped) - case sensitive    
    tf_idf.out <- h2o.tf_idf(preprocessed_input.h2o_data, doc_id_col_idx, word_col_idx, preprocess=FALSE)
    
    # Compute TF-IDF values using already preprocessed data (pre-processing step in TF-IDF is skipped) - case insensitive    
    tf_idf.out <- h2o.tf_idf(preprocessed_input.h2o_data, doc_id_col_idx, word_col_idx, preprocess=FALSE, case_sensitive=FALSE)

   .. code-tab:: python

    from collections import OrderedDict
    import h2o
    from h2o.information_retrieval.tf_idf import tf_idf
    
    h2o.init()

    # Construct data
    documents = [
        'H2O is an in-memory platform for distributed, scalable machine learning. H2O uses familiar interfaces like R, Python, Scala, Java, JSON and the Flow notebook/web interface, and works seamlessly with big data technologies like Hadoop and Spark.',
        'Ice hockey is a contact team sport played on ice, usually in a rink, in which two teams of skaters use their sticks to shoot a vulcanized rubber puck into their opponent\'s net to score goals. The sport is known to be fast-paced and physical.',
        'An antibody (Ab), also known as an immunoglobulin (Ig), is a large, Y-shaped protein produced mainly by plasma cells that is used by the immune system to neutralize pathogens such as pathogenic bacteria and viruses.'
    ]
    doc_ids = list(range(len(documents)))
    
    # Create H2OFrame
    input_frame = h2o.H2OFrame(OrderedDict([('DocID', doc_ids), ('Document', documents)]),
                                column_types=['numeric', 'string'])

    doc_id_col_idx = 0
    document_col_idx = 1
    
    # Compute TF-IDF values using non-preprocessed data (pre-processing is done by TF-IDF) - case sensitive
    tf_idf_out = tf_idf(input_frame, doc_id_col_idx, document_col_idx)

    # Compute TF-IDF values using non-preprocessed data (pre-processing is done by TF-IDF) - case insensitive
    tf_idf_out = tf_idf(input_frame, doc_id_col_idx, document_col_idx, case_sensitive=False)

    # Construct "preprocessed" data (more complex tokenizing techniques can be used)
    preprocessed_data = [(doc_id, word) for doc_id, document in enumerate(documents) for word in document.split()]

    # Create H2OFrame
    preprocessed_input_frame = h2o.H2OFrame(preprocessed_data,
                                            column_names=['DocID', 'Word'],
                                            column_types=['numeric', 'string'])

    doc_id_col_idx = 0
    word_col_idx = 1

    # Compute TF-IDF values using already preprocessed data (pre-processing step in TF-IDF is skipped) - case sensitive
    tf_idf_out = tf_idf(preprocessed_input_frame, doc_id_col_idx, word_col_idx, preprocess=False)

    # Compute TF-IDF values using already preprocessed data (pre-processing step in TF-IDF is skipped) - case insensitive
    tf_idf_out = tf_idf(preprocessed_input_frame, doc_id_col_idx, word_col_idx, preprocess=False, case_sensitive=False)
