.as.survival.coxph.model <- function(model) {
    coefs <- model$coefficients_table
    df <- length(coefs$names)
    model <- list(
             coefficients = structure(coefs$coefficients, names = coefs$names),
             var          = model$var_coef,
             loglik       = c(model$null_loglik, model$loglik),
             score        = model$score_test,
             iter         = model$iter,
             method       = model$ties,
             n            = model$n,
             nevent       = model$total_event,
             wald.test    = structure(model$wald_test,
             names        = if (df == 1L) coefs$names else NULL),
             call         = model$formula)
    return(model)

}

.as.survival.coxph.summary <- function(model) {
    coefs <- model$coefficients_table
    df <- length(coefs$names)
    summary <- list(
             call         = model$formula,
             n            = model$n,
             loglik       = model$loglik,
             nevent       = model$nevent,
             coefficients = structure(cbind(coefs$coefficients, coefs$exp_coef, coefs$se_coef, coefs$z_coef, 1 - stats::pchisq(coefs$z_coef^2, 1)),
             dimnames     = list(coefs$names, c("coef", "exp(coef)", "se(coef)", "z", "Pr(>|z|)"))),
             conf.int     = NULL,
             logtest      = c(
                                test   = model$loglik_test,
                                df     = df,
                                pvalue = 1 - stats::pchisq(model$loglik_test, df)),
             sctest       = c(
                                test   = model$score_test,
                                df     = df,
                                pvalue = 1 - stats::pchisq(model$score_test, df)),
             rsq          = c(rsq = model$rsq, maxrsq = model$maxrsq),
             waldtest     = c(
                                test = model$wald_test,
                                df = df,
                                pvalue = 1 - stats::pchisq(model$wald_test, df)),
             used.robust  = FALSE)
    return(new("H2OCoxPHModelSummary", summary = summary))
}
