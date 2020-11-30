setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


get_simple_input_test_frame <- function() {
    doc_ids <- c(0, 1, 2)
    documents <- c('A B C', 'A a a Z', 'C c B C')

    input.r_data <- data.frame(doc_ids, documents, stringsAsFactors=FALSE)
    colnames(input.r_data) <- c('DocID', 'Document')
    input.h2o_data <- as.h2o(input.r_data)
}

get_simple_preprocessed_input_test_frame <- function() {
    doc_ids <- c(0, 0, 0, 
                 1, 1, 1, 1, 
                 2, 2, 2, 2)
    words <- c('A', 'B', 'C', 
               'A', 'a', 'a', 'Z',
               'C', 'c', 'B', 'C')

    input.r_data <- data.frame(doc_ids, words, stringsAsFactors=FALSE)
    colnames(input.r_data) <- c('DocID', 'Word')
    input.h2o_data <- as.h2o(input.r_data)
}

get_expected_output_frame_case_sens <- function() {
    get_expected_output_frame(c(0, 1, 0, 2, 0, 2, 1, 1, 2),
                              c('A', 'A', 'B', 'B', 'C', 'C', 'Z', 'a', 'c'),
                              c(1, 1, 1, 1, 1, 2, 1, 2, 1),
                              c(0.28768, 0.28768, 0.28768, 0.28768, 0.28768, 0.28768, 0.69314, 0.69314, 0.69314),
                              c(0.28768, 0.28768, 0.28768, 0.28768, 0.28768, 0.57536, 0.69314, 1.38629, 0.69314))
}

get_expected_output_frame_case_insens <- function() {
    get_expected_output_frame(c(0, 1, 0, 2, 0, 2, 1),
                              c('a', 'a', 'b', 'b', 'c', 'c', 'z'),
                              c(1, 3, 1, 1, 1, 3, 1),
                              c(0.28768, 0.28768, 0.28768, 0.28768, 0.28768, 0.28768, 0.69314),
                              c(0.28768, 0.86304, 0.28768, 0.28768, 0.28768, 0.86304, 0.69314))
}

get_expected_output_frame <- function(out_doc_ids, out_tokens, out_TFs, out_IDFs, out_TFIDFs) {
    expected_out.r_data <- data.frame(out_doc_ids, out_tokens, out_TFs, out_IDFs, out_TFIDFs, stringsAsFactors=FALSE)
    as.h2o(expected_out.r_data)
}

testTfIdf <- function(preprocess, case_sens, cols=c(0, 1)) {
    Log.info('Constructing test data...')
    input_frame <- if (preprocess) get_simple_input_test_frame() else get_simple_preprocessed_input_test_frame()
    expected_output_frame <- if (case_sens) get_expected_output_frame_case_sens() else get_expected_output_frame_case_insens()

    Log.info('Computing TF-IDF...')
    output_frame <- h2o.tf_idf(input_frame, cols[1], cols[2], preprocess, case_sens)
    
    Log.info('Checking results...')
    expect_equal(expected_output_frame, output_frame)
}

applytest <- function() {
    Log.info('TF-IDF case sensitive with pre-processing:')
    testTfIdf(TRUE, TRUE, c('DocID', 'Document'))
    Log.info('TF-IDF case sensitive without pre-processing:')
    testTfIdf(FALSE, TRUE)
    Log.info('TF-IDF case insensitive with pre-processing:')
    testTfIdf(TRUE, FALSE, c('DocID', 'Word'))
    Log.info('TF-IDF case insensitive without pre-processing:')
    testTfIdf(FALSE, FALSE)
}

doTest('TF-IDF', applytest)
