# H2O-3 Enterprise messaging shown by the open-source R client.
#
# Mirrors h2o-py/h2o/enterprise.py: a single place for the "blocked" notice so
# the wording and box style stay consistent across the MOJO entry points. This
# is the friendly client-side message; the actual paywall is enforced
# server-side (water.EnterpriseGate) so the REST API cannot be bypassed.

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

# Print the Enterprise "blocked" notice, then stop(): MOJO is Enterprise-only.
.h2o.enterprise.block <- function(operation) {
  msg <- .h2o.enterprise.box(c(
    "H2O-3 ENTERPRISE REQUIRED  -  THIS ACTION IS BLOCKED",
    "",
    paste0(operation, " is a production capability available only in"),
    "H2O-3 Enterprise, the commercially supported tier of H2O-3.",
    "",
    "You are running H2O-3 OSS: built for experimentation and",
    "research, not production.",
    "",
    "You must upgrade to H2O-3 Enterprise for:",
    "  - Multi-node production deployment (Hadoop, Spark, Kubernetes)",
    "  - Audit-ready governance (SOC 2, ISO 27001, ISO 42001)",
    "  - Prioritized CVE patching and long-term security maintenance",
    "  - Premium support with SLAs",
    "",
    "H2O-3 Enterprise is a drop-in replacement for H2O-3 OSS -",
    "your existing code, APIs, and pipelines run unchanged.",
    "",
    "Learn more: h2o.ai/h2o-3/oss-vs-enterprise",
    paste0("Contact:    ", .h2o.enterprise.email)
  ))
  message("\n", msg, "\n")
  stop(paste0(operation, " requires H2O-3 Enterprise. Contact ", .h2o.enterprise.email),
       call. = FALSE)
}
