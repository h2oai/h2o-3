# H2O-3 Enterprise messaging shown by the open-source R client.
#
# Mirrors h2o-py/h2o/enterprise.py: a single place for the OSS-vs-Enterprise notice
# so the wording and box style stay consistent between the two clients.

.h2o.enterprise.email <- "enterprise@h2o.ai"

# Render the given lines inside an ASCII frame (matches the Python client).
#
# ASCII-only on purpose: box-drawing characters cannot be represented in every console
# encoding, and R CMD check rejects non-ASCII in R code outside comments. Keeping both
# clients on the same frame also keeps the demo output identical.
.h2o.enterprise.box <- function(lines) {
  width <- max(nchar(lines))
  rule <- paste0("+", strrep("-", width + 2L), "+")
  body <- vapply(lines, function(s)
    paste0("| ", formatC(s, width = -width), " |"), character(1L))
  paste(c(rule, body, rule), collapse = "\n")
}

# Print the OSS-vs-Enterprise notice shown by h2o.init() / h2o.connect().
.h2o.enterprise.show_cluster_banner <- function() {
  msg <- .h2o.enterprise.box(c(
    "You are running the community edition of H2O-3 OSS.",
    "",
    "For commercial use, H2O-3 Enterprise is now recommended.",
    "This includes production support, CVE fixes, multi-node scaling,",
    "model artifact extraction, and more.",
    "See h2o.ai/h2o-3/oss-vs-enterprise for additional details.",
    paste0("Contact ", .h2o.enterprise.email, " to upgrade.")
  ))
  cat("\n", msg, "\n\n", sep = "")
}
