package water.util;

import hex.genmodel.easy.RowData;
import water.fvec.Chunk;
import water.fvec.Vec;
import water.parser.BufferedString;

public class RowDataUtils {

    public static void extractChunkRow(Chunk[] cs, String[] names, byte[] types, int row, RowData rowData) {
        BufferedString str = new BufferedString();
        for (int col = 0; col < cs.length; col++) {
            final Object value;
            final byte type = types[col];
            final Chunk chk = cs[col];
            if (type == Vec.T_CAT || type == Vec.T_STR) {
                if (cs[col].isNA(row)) {
                    value = Double.NaN;
                } else if (type == Vec.T_CAT) {
                    value = chk.vec().domain()[(int) chk.at8(row)];
                } else {
                    value = chk.atStr(str, row).toString();
                }
            } else if (type == Vec.T_NUM || type == Vec.T_TIME){
                value = cs[col].atd(row);
            } else {
                throw new UnsupportedOperationException("Cannot convert column of type " + Vec.TYPE_STR[type]);
            }
            rowData.put(names[col], value);
        }
    }

}
