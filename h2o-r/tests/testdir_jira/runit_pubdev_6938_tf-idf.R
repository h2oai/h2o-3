setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


get_simple_input_test_frame <- function() {
    doc_ids <- c(0, 1, 2)
    documents <- c('A B C', 'A A A Z', 'C C B C')

    input.r_data <- data.frame(doc_ids, documents, stringsAsFactors=FALSE)
    input.h2o_data <- as.h2o(input.r_data)
}

get_simple_preprocessed_input_test_frame <- function() {
    doc_ids <- c(0, 0, 0, 
                 1, 1, 1, 1, 
                 2, 2, 2, 2)
    words <- c('A', 'B', 'C', 
               'A', 'A', 'A', 'Z',
               'C', 'C', 'B', 'C')

    input.r_data <- data.frame(doc_ids, words, stringsAsFactors=FALSE)
    input.h2o_data <- as.h2o(input.r_data)
}

get_simple_test_frames <- function(preprocess) {
    input.h2o_data <- if (preprocess) get_simple_preprocessed_input_test_frame() else get_simple_input_test_frame()

    out_doc_ids <- c(0, 1, 0, 2, 0, 2, 1)
    out_tokens <- c('A', 'A', 'B', 'B', 'C', 'C', 'Z')
    out_TFs <- c(1, 3, 1, 1, 1, 3, 1)
    out_IDFs <- c(0.28768, 0.28768, 0.28768, 0.28768, 0.28768, 0.28768, 0.69314)
    out_TFIDFs <- c(0.28768, 0.86304, 0.28768, 0.28768, 0.28768, 0.86304, 0.69314)

    expected_out.r_data <- data.frame(out_doc_ids, out_tokens, out_TFs, out_IDFs, out_TFIDFs, stringsAsFactors=FALSE)
    expected_out.h2o_data <- as.h2o(expected_out.r_data)

    c(input.h2o_data, expected_out.h2o_data)
}

testTfIdf <- function(preprocess) {
    Log.info('Constructing test data...')
    test_frames <- get_simple_test_frames(preprocess)
    input_frame <- test_frames[[1]]
    expected_output_frame <- test_frames[[2]]

    Log.info('Computing TF-IDF...')
    output_frame <- h2o.tf_idf(input_frame, preprocess)
    
    Log.info('Checking results...')
    expect_equal(expected_output_frame, output_frame)
}

applytest <- function() {
    Log.info('TF-IDF with pre-processing:')
    testTfIdf(TRUE)
    Log.info('TF-IDF without pre-processing:')
    testTfIdf(FALSE)
}

doTest('TF-IDF', applytest)
