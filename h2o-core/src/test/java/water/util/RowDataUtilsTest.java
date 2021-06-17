package water.util;

import hex.genmodel.easy.RowData;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.Scope;
import water.TestFrameCatalog;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static org.junit.Assert.*;
import static water.TestUtil.ar;
import static water.TestUtil.ard;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class RowDataUtilsTest {

    @Test
    public void extractChunkRow() {
        Scope.enter();
        try {
            long now = System.currentTimeMillis();
            long later = now + 42;
            Frame fr = new TestFrameBuilder()
                    .withColNames("NumCol", "TimeCol", "CatCol", "StrCol")
                    .withVecTypes(Vec.T_NUM, Vec.T_TIME, Vec.T_CAT, Vec.T_STR)
                    .withDataForCol(0, ard(Math.PI, Math.E, Double.NaN))
                    .withDataForCol(1, ard(now, later, Double.NaN))
                    .withDataForCol(2, ar("B", "A", null))
                    .withDataForCol(3, ar("Terry", "Pratchett", null))
                    .build();

            RowData row0 = new RowData() {{
                put("NumCol", Math.PI);
                put("TimeCol", (double) now);
                put("CatCol", "B");
                put("StrCol", "Terry");
            }};
            RowData row1 = new RowData() {{
                put("NumCol", Math.E);
                put("TimeCol", (double) later);
                put("CatCol", "A");
                put("StrCol", "Pratchett");
            }};
            RowData rowNAs = new RowData() {{
                put("NumCol", Double.NaN);
                put("TimeCol", Double.NaN);
                put("CatCol", Double.NaN);
                put("StrCol", Double.NaN);
            }};
            RowData[] rows = new RowData[]{row0, row1, rowNAs}; 
            
            Chunk[] cs = FrameUtils.extractChunks(fr, 0, false);
            RowData rowData = new RowData(); // create once and keep mutating in the loop
            for (int i = 0; i < rows.length; i++) {
                RowDataUtils.extractChunkRow(cs, fr.names(), fr.types(), i, rowData);
                assertEquals(rows[i], rowData);
            }
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void extractChunkRow_unsupportedTypes() {
        Scope.enter();
        try {
            Frame fr = TestFrameCatalog.unusualTypes();
            for (String name : fr.names()) {
                Vec v = fr.vec(name);
                try {
                    RowDataUtils.extractChunkRow(new Chunk[]{v.chunkForRow(0)}, new String[]{name}, new byte[]{v.get_type()}, 0, new RowData());
                    fail("Expected to fail on type " + v.get_type_str());
                } catch (Exception e) {
                    assertEquals("Cannot convert column of type " + v.get_type_str(), e.getMessage());
                }
            }
        } finally {
            Scope.exit();
        }
    }

}
