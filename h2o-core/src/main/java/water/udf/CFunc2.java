package water.udf;

public interface CFunc2 extends CFunc {
  double apply(CBlock.CRow row1, CBlock.CRow row2);
}
