package water.rapids.ast.prims.models;

import water.fvec.Vec;
import water.util.TwoDimTable;

public class PermutationFeatureImportanceAstHelper {

    Vec [] _vecs;
    String [] _all_names;
            
    Vec[] do_Vecs(TwoDimTable varImp_t){
        _vecs = new Vec[varImp_t.getRowDim() + 1];
        double tmp_row[] = new double [varImp_t.getColDim()];
        for (int i = 0 ; i < varImp_t.getRowDim() ; i++){
            for (int j = 0 ; j < varImp_t.getColDim() ; j++){
                tmp_row[j] = (double) varImp_t.get(i,j);
            }
            _vecs[i] = Vec.makeVec(tmp_row, Vec.newKey());
        }
        _vecs[varImp_t.getRowDim()] = Vec.makeVec(varImp_t.getColHeaders(), Vec.newKey());
        return _vecs;
    }

    String [] do_Names(String [] table_names){
        _all_names = new String [table_names.length + 1];
        for (int i = 0 ; i < table_names.length ; i++){
            _all_names[i] = table_names[i];
        }
        _all_names[table_names.length] = "ID";
        System.out.println(_all_names);
        return _all_names;
    }
}
