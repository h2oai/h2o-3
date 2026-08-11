# H2O-3 Secure messaging shown by the open-source R client.
#
# Mirrors h2o-py/h2o/enterprise.py: a single place for the OSS-vs-Secure and
# "blocked" notices so the wording and box style stay consistent across the
# cluster banner and the MOJO entry points. These are the friendly client-side
# messages; the actual paywall is enforced server-side (water.EnterpriseGate)
# so the REST API cannot be bypassed.

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

# Print the OSS-vs-Secure notice shown by h2o.init() / h2o.connect().
.h2o.enterprise.show_cluster_banner <- function() {
  msg <- .h2o.enterprise.box(c(
    "You are running the community edition of H2O-3 OSS.",
    "",
    "For commercial use, H2O-3 Secure is now recommended.",
    "This includes production support, CVE fixes, multi-node scaling,",
    "model artifact extraction, and more.",
    "See h2o.ai/h2o-3/oss-vs-secure for additional details.",
    paste0("Contact ", .h2o.enterprise.email, " to upgrade.")
  ))
  cat("\n", msg, "\n\n", sep = "")
}

# Print the H2O-3 Secure "blocked" notice, then stop(): MOJO is Secure-only.
.h2o.enterprise.block <- function(operation) {
  msg <- .h2o.enterprise.box(c(
    "H2O-3 SECURE REQUIRED  -  THIS ACTION IS BLOCKED",
    "",
    paste0(operation, " is a production capability available only in"),
    "H2O-3 Secure, the commercially supported tier of H2O-3.",
    "",
    "You are running self-managed H2O-3 OSS.",
    "",
    "You must upgrade to H2O-3 Secure for:",
    "  - Hadoop and Kubernetes enterprise packages",
    "  - Audit-supporting capabilities (SOC 2, ISO 27001, ISO 42001)",
    "  - Commercial CVE patching & long-term support",
    "  - Premium support with SLAs",
    "",
    "If you need MOJO, we provide a free license for non-commercial",
    "use. See h2o.ai/h2o-3/oss-vs-secure to compare the two tiers.",
    "",
    paste0("Request a license or upgrade:  ", .h2o.enterprise.email)
  ))
  message("\n", msg, "\n")
  stop(paste0(operation, " requires H2O-3 Secure. Contact ", .h2o.enterprise.email),
       call. = FALSE)
}
