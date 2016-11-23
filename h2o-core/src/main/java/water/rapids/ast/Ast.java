package water.rapids.ast;

/**
 * Base class for all nodes in Rapids language Abstract Syntax Tree.
 *
 * (Replacement for the {@link AstRoot} class).
 */
public abstract class Ast<T extends Ast<T>> extends AstRoot<T> {

}
