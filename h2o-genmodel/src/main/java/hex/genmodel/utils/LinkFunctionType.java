package hex.genmodel.utils;

/**
 * Link Function type
 * NOTE: The moving to hex.LinkFunctionType is not possible without resolving dependencies between 
 * h2o-genmodel and h2o-algos project
 */
public enum LinkFunctionType {
    log,
    logit,
    identity,
    ologit,
    ologlog,
    oprobit,
    inverse,
    tweedie
}
