package water.rapids.ast.prims.mungers;

import water.fvec.*;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValNums;
import java.util.ArrayList;

/**
 * Get column indexes of an H2OFrame that are of a certain data type.
 * <p/>
 * This will take an H2OFrame and return all column indexes based on a specific data type (numeric, categorical,
 * string,time, uuid, and bad)
 * <p/>
 *
 * @author navdeepgill
 * @version 3.10
 * @since  3.10
 *
 */
public class AstColumnsByType extends AstPrimitive {
    @Override
    public String[] args() {
        return new String[]{"ary","type"};
    }

    private enum DType {Numeric,Categorical,String,Time,UUID,Bad}

    @Override
    public String str() {
        return "columnsByType";
    }

    @Override
    public int nargs() {
        return 1 + 2;
    } //ary type

    @Override
    public ValNums apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
        Frame fr = stk.track(asts[1].exec(env)).getFrame();
        String type = stk.track(asts[2].exec(env)).getStr();
        DType dtype;
        switch (type) {
            case "numeric": // Numeric, but not categorical or time
                dtype = DType.Numeric;
                break;
            case "categorical": // Integer, with a categorical/factor String mapping
                dtype = DType.Categorical;
                break;
            case "string": // String
                dtype = DType.String;
                break;
            case "time": // Long msec since the Unix Epoch - with a variety of display/parse options
                dtype = DType.Time;
                break;
            case "uuid": // UUID
                dtype = DType.UUID;
                break;
            case "bad": // No none-NA rows (triple negative! all NAs or zero rows)
                dtype = DType.Bad;
                break;
            default:
                throw new IllegalArgumentException("unknown data type to filter by: " + type);
        }
        Vec vecs[] = fr.vecs();
        ArrayList<Double> idxs = new ArrayList<>();
        for (double i = 0; i < fr.numCols(); i++)
            if (dtype.equals(DType.Numeric) && vecs[(int) i].isNumeric()){
                    idxs.add(i);
            }
            else if (dtype.equals(DType.Categorical) && vecs[(int) i].isCategorical()){
                idxs.add(i);
            }
            else if (dtype.equals(DType.String) && vecs[(int) i].isString()){
                idxs.add(i);
            }
            else if (dtype.equals(DType.Time) && vecs[(int) i].isTime()){
                idxs.add(i);
            }
            else if (dtype.equals(DType.UUID) && vecs[(int) i].isUUID()){
                idxs.add(i);
            } else if (dtype.equals(DType.Bad) && vecs[(int) i].isBad()){
                idxs.add(i);
            }

        double[] include_cols = new double[idxs.size()];
        int i = 0;
        for (double d : idxs)
            include_cols[i++] = (int) d;
        return new ValNums(include_cols);
    }
}

