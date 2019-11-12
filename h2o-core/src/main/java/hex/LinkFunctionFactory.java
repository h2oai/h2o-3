package hex;

import hex.genmodel.utils.LinkFunctionType;
import water.H2O;

public class LinkFunctionFactory {

    public static LinkFunction getLinkFunction(String type) {
        return getLinkFunction(LinkFunctionType.valueOf(type));
    }

    public static LinkFunction getLinkFunction(LinkFunctionType type) {
        switch (type) {
            case log:
                return new LogFunction();
            case logit:
                return new LogitFunction();
            case identity:
                return new IdentityFunction();
            case ologit:
                return new OlogitFunction();
            case ologlog:
                return new OloglogFunction();
            case oprobit:
                return new OprobitFunction();
            case inverse:
                return new InverseFunction();
            default:
                throw H2O.unimpl("The" + type + " link function is not implemented.");
        }
    }

}
