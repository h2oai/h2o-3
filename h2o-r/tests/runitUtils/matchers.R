library(rlang)

#TODO How to make it visible globally?
expect_defined <- function (object, info = NULL, label = NULL)
{
    act <- quasi_label(enquo(object), label)
    expect(!is.null(act$val), sprintf("%s is not defined.", act$lab),
    info = info)
    invisible(act$val)
}
