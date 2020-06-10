setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.CoxPH.heart <- function() {
    heart.hex <- h2o.importFile(locate("smalldata/coxph_test/heart.csv"))
    heart.df <- as.data.frame(heart.hex)

    coxph.h2o <- h2o.coxph(x="age", event_column="event", start_column="start", stop_column="stop", training_frame=heart.hex)
    coxph.r <- survival::coxph(Surv(start, stop, event) ~ age, data = heart.df)


    output <- coxph.h2o@model
    coefs <- output$coefficients
    coefficients.h2o <- coefs$coefficients
    names(coefficients.h2o) <- coefs$names

    expect_equal(coefficients.h2o, coxph.r$coefficients, tolerance = 1e-8)
    expect_equal(output$var_coef, coxph.r$var, tolerance = 1e-8)
    expect_equal(output$loglik, tail(coxph.r$loglik, 1), tolerance = 1e-8)
    expect_equal(output$score, coxph.r$score, tolerance = 1e-8)
    expect_true(output$iter >= 1)

    expect_equal(output$n, coxph.r$n)
    expect_equal(output$total_event, coxph.r$nevent)

    expect_equal(output$wald.test, coxph.r$wald_test, tolerance = 1e-8)

    # test summary, coef
    summary.info <- summary(coxph.h2o)
    expect_equal(coef(coxph.h2o)[1], c(age = coef(summary.info)[,1]), tolerance = 1e-8)

    # test print summary
    summary.out <- capture.output(print(summary.info))
    print(summary.out)
    summary.out.sanitized <- summary.out[!startsWith(summary.out, "Rsquare")] # older versions have R^2, ignored in output
    summary.out.sanitized <- gsub("[0-9]+", "?", x = summary.out.sanitized)
    summary.out.sanitized <- gsub("[^a-zA-Z~=?|() *>.,-:\"]", "_", x = summary.out.sanitized) #keep only "safe" characters (see #9)
    print(summary.out.sanitized)
    summary.out.expected <- c(
      "Call:",
      "Surv(start, stop, event) ~ age",
      "",
      "  n= ?, number of events= ? ",
      "",
      "       coef exp(coef) se(coef)     z Pr(>|z|)  ",
      "age ?.?   ?.?  ?.? ?.?   ?.? *",
      "---",
      "Signif. codes:  ? _***_ ?.? _**_ ?.? _*_ ?.? _._ ?.? _ _ ?", #9, locale specific (single quotes)
      "",
      "    exp(coef) exp(-coef) lower .? upper .?",
      "age     ?.?     ?.?     ?.?      ?.?",
      "",
      "Likelihood ratio test= ?.?  on ? df,   p=?.?",
      "Wald test            = ?.?  on ? df,   p=?.?",
      "Score (logrank) test = ?.?  on ? df,   p=?.?",
      ""
    )
    expect_equal(summary.out.expected, summary.out.sanitized)

    # smoke tests
    print(extractAIC(coxph.h2o))
    print(logLik(coxph.h2o))
    print(vcov(coxph.h2o))
}

doTest("CoxPH: Heart Test", test.CoxPH.heart)
