.as.survival.coxph.model <- function(model) {
    coef_names <- model$coef_names
    df <- length(model$coef)
    model <- list(
             coefficients = structure(model$coef, names = coef_names),
             var          = do.call(rbind, as.list(model$var_coef)),
             loglik       = c(model$null_loglik, model$loglik),
             score        = model$score_test,
             iter         = model$iter,
             means        = structure(c(unlist(model$x_mean_cat), unlist(model$x_mean_num)), names = coef_names),
             means.offset = structure(unlist(model$mean_offset),
             names        = unlist(model$offset_names)),
             method       = model$ties,
             n            = model$n,
             nevent       = model$total_event,
             wald.test    = structure(model$wald_test,
             names        = if (df == 1L) coef_names else NULL),
             call         = model$rcall)
    return(model)

}

.as.survival.coxph.summary <- function(model) {
    coef_names <- model$coef_names
    df <- length(model$coef)
    summary <- list(
             call         = model$rcall,
             n            = model$n,
             loglik       = model$loglik,
             nevent       = model$nevent,
             coefficients = structure(cbind(model$coef, model$exp_coef, model$se_coef, model$z_coef, 1 - stats::pchisq(model$z_coef^2, 1)),
             dimnames     = list(coef_names, c("coef", "exp(coef)", "se(coef)", "z", "Pr(>|z|)"))),
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
