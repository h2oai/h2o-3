package hex.glm;

import Jama.Matrix;
import hex.DataInfo;
import water.DKV;
import water.Iced;
import water.Key;
import water.fvec.Frame;
import water.util.ArrayUtils;
import water.util.IcedHashMap;
import water.util.TwoDimTable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Arrays.stream;
import static water.util.ArrayUtils.innerProduct;

public class ConstrainedGLMUtils {
  // constant setting refer to Michel Bierlaire, Optimization: Principles and Algorithms, Chapter 19, EPEL Press,
  // second edition, 2018.
  public static final double EPS = 1e-15;
  public static final double EPS2 = 1e-12;
  
  public static class LinearConstraints extends Iced { // store one linear constraint
    public IcedHashMap<String, Double> _constraints; // column names, coefficient of constraints
    public double _constraintsVal; // contains evaluated constraint values
    public boolean _active = true; // only applied to less than and equal to zero constraints
    
    public LinearConstraints() {
      _constraints = new IcedHashMap<>();
      _constraintsVal = Double.NaN; // represent constraint not evaluated.
    }
  }
  
  public static class ConstraintsDerivatives extends Iced {
    public IcedHashMap<Integer, Double> _constraintsDerivative;
    public boolean _active;
    
    public ConstraintsDerivatives(boolean active) {
      _constraintsDerivative = new IcedHashMap<>();
      _active = active;
    }
  }
  
  public static class ConstraintsGram extends Iced {
    public IcedHashMap<CoefIndices, Double> _coefIndicesValue;
    public boolean _active;
    
    public ConstraintsGram() {
      _coefIndicesValue = new IcedHashMap<>();
    }
  }
  
  public static class CoefIndices implements hex.glm.CoefIndices {
    final int _firstCoefIndex;
    final int _secondCoefIndex;
    
    public CoefIndices(int firstInd, int secondInd) {
      _firstCoefIndex = firstInd;
      _secondCoefIndex = secondInd;
    }
    
    @Override
    public boolean equals(Object o) {
      if (this == o)
        return true;
      else if (o == null)
        return false;
      else if (this._firstCoefIndex == ((CoefIndices) o)._firstCoefIndex && 
              this._secondCoefIndex == ((CoefIndices) o)._secondCoefIndex)
        return true;
      return false;
    }
    
    public String toString() {
      return "first coefficient index: " + _firstCoefIndex + ", second coefficient index " + _secondCoefIndex;
    }
  }
  
  public static class ConstraintGLMStates {
    double _ckCS;
    double _ckCSHalf; // = ck/2
    double _epsilonkCS;
    double _epsilonkCSSquare;
    double _etakCS;
    double _etakCSSquare;
    double _epsilon0;
    String[] _constraintNames;
    double[][] _initCSMatrix;
    double _gradientMagSquare;
    double _constraintMagSquare;
    
    public ConstraintGLMStates(String[] constrainNames, double[][] initMatrix, GLMModel.GLMParameters parms) {
      _constraintNames = constrainNames;
      _initCSMatrix = initMatrix;
      _ckCS = parms._constraint_c0;
      _ckCSHalf = parms._constraint_c0*0.5;
      _epsilonkCS = 1.0/parms._constraint_c0;
      _epsilonkCSSquare =_epsilonkCS*_epsilonkCS;
      _etakCS = parms._constraint_eta0/Math.pow(parms._constraint_c0, parms._constraint_alpha);
      _etakCSSquare = _etakCS*_etakCS;
      _epsilon0 = 1.0/parms._constraint_c0;
    }
  }
  
  public static LinearConstraints[] combineConstraints(LinearConstraints[] const1, LinearConstraints[] const2) {
    List<LinearConstraints> allList = new ArrayList<>();
    if (const1 != null)
      allList.addAll(stream(const1).collect(Collectors.toList()));
    if (const2 != null)
      allList.addAll(stream(const2).collect(Collectors.toList()));
    return allList.size()==0 ? null : allList.stream().toArray(LinearConstraints[]::new);
  }

  /***
   *
   * This method will extract the constraints specified in beta constraint and combine it with the linear constraints
   * later.  Note that the linear constraints are only accepted in standard form, meaning we only accept the following
   * constraint forms:  2*beta_1-3*beta_4-3 == 0 or 2*beta_1-3*beta_4-3 <= 0.
   * 
   * The beta constraints on the other hand is specified in several forms:
   * 1): -Infinity <= beta <= Infinity: ignored, no constrain here;
   * 2): -Infinity <= beta <= high_val: transformed to beta - high_val <= 0, add to lessThanEqualTo constraint;
   * 3): low_val <= beta <= Infinity: transformed to low_val - beta <= 0, add to lessThanEqualTo constraint;
   * 4): low_val <= beta <= high_val: transformed to two constraints, low_val-beta <= 0, beta-high_val <= 0, add to 
   *   lessThanEqualTo constraint.
   * 5): val <= beta <= val: transformed to beta-val == 0, add to equalTo constraint.
   * 
   * The newly extracted constraints will be added to fields in state.
   * 
   */
  public static int[] extractBetaConstraints(ComputationState state, String[] coefNames) {
    GLM.BetaConstraint betaC = state.activeBC();
    List<LinearConstraints> equalityC = new ArrayList<>();
    List<LinearConstraints> lessThanEqualToC = new ArrayList<>();
    List<Integer> betaIndexOnOff = new ArrayList<>();
    if (betaC._betaLB != null) {
      int numCons = betaC._betaLB.length-1;
      for (int index=0; index<numCons; index++) {
        if (!Double.isInfinite(betaC._betaUB[index]) && (betaC._betaLB[index] == betaC._betaUB[index])) { // equality constraint
          addBCEqualityConstraint(equalityC, betaC, coefNames, index);
          betaIndexOnOff.add(1);
        } else if (!Double.isInfinite(betaC._betaUB[index]) && !Double.isInfinite(betaC._betaLB[index]) && 
                (betaC._betaLB[index] < betaC._betaUB[index])) { // low < beta < high, generate two lessThanEqualTo constraints
          addBCGreaterThanConstraint(lessThanEqualToC, betaC, coefNames, index);
          addBCLessThanConstraint(lessThanEqualToC, betaC, coefNames, index);
          betaIndexOnOff.add(1);
          betaIndexOnOff.add(0);
        } else if (Double.isInfinite(betaC._betaUB[index]) && !Double.isInfinite(betaC._betaLB[index])) {  // low < beta < infinity
          addBCGreaterThanConstraint(lessThanEqualToC, betaC, coefNames, index);
          betaIndexOnOff.add(1);
        } else if (!Double.isInfinite(betaC._betaUB[index]) && Double.isInfinite(betaC._betaLB[index])) { // -infinity < beta < high
          addBCLessThanConstraint(lessThanEqualToC, betaC, coefNames, index);
          betaIndexOnOff.add(1);
        }
      }
    }
    state.setLinearConstraints(equalityC.toArray(new LinearConstraints[0]),
            lessThanEqualToC.toArray(new LinearConstraints[0]), true);
    return betaIndexOnOff.size()==0 ? null : betaIndexOnOff.stream().mapToInt(x->x).toArray();
  }

  /***
   * This method will extract the equality constraint and add to equalityC from beta constraint by doing the following
   * transformation: val <= beta <= val: transformed to beta-val == 0, add to equalTo constraint.
   */
  public static void addBCEqualityConstraint(List<LinearConstraints> equalityC, GLM.BetaConstraint betaC,
                                           String[] coefNames, int index) {
    LinearConstraints oneEqualityConstraint = new LinearConstraints();
    oneEqualityConstraint._constraints.put(coefNames[index], 1.0);
    oneEqualityConstraint._constraints.put("constant", -betaC._betaLB[index]);
    equalityC.add(oneEqualityConstraint);
  }

  /***
   * This method will extract the greater than constraint and add to lessThanC from beta constraint by doing the following
   * transformation: low_val <= beta <= Infinity: transformed to low_val - beta <= 0.
   */
  public static void addBCGreaterThanConstraint(List<LinearConstraints> lessThanC, GLM.BetaConstraint betaC,
                                             String[] coefNames, int index) {
    LinearConstraints lessThanEqualToConstraint = new LinearConstraints();
    lessThanEqualToConstraint._constraints.put(coefNames[index], -1.0);
    lessThanEqualToConstraint._constraints.put("constant", betaC._betaLB[index]);
    lessThanC.add(lessThanEqualToConstraint);
  }

  /***
   * This method will extract the less than constraint and add to lessThanC from beta constraint by doing the following
   * transformation: -Infinity <= beta <= high_val: transformed to beta - high_val <= 0.
   */
  public static void addBCLessThanConstraint(List<LinearConstraints> lessThanC, GLM.BetaConstraint betaC,
                                             String[] coefNames, int index) {
    LinearConstraints greaterThanConstraint = new LinearConstraints();
    greaterThanConstraint._constraints.put(coefNames[index], 1.0);
    greaterThanConstraint._constraints.put("constant", -betaC._betaUB[index]);
    lessThanC.add(greaterThanConstraint);  
  }

  /***
   * This method will extract the constraints specified in the Frame with key linearConstraintFrameKey.  For example,
   * the following constraints a*beta_1+b*beta_2-c*beta_5 == 0, d*beta_2+e*beta_6-f <= 0 can be specified as the
   * following rows:
   *  names           values            Type            constraint_numbers
   *  beta_1             a              Equal                   0
   *  beta_2             b              Equal                   0
   *  beta_5            -c              Equal                   0
   *  beta_2             d              LessThanEqual           1
   *  beta_6             e              LessThanEqual           1
   *  constant          -f              LessThanEqual           1
   */
  public static void extractLinearConstraints(ComputationState state, Key<Frame> linearConstraintFrameKey, DataInfo dinfo) {
    List<LinearConstraints> equalityC = new ArrayList<>();
    List<LinearConstraints> lessThanEqualToC = new ArrayList<>();
    Frame linearConstraintF = DKV.getGet(linearConstraintFrameKey);
    List<String> colNamesList = Stream.of(dinfo._adaptedFrame.names()).collect(Collectors.toList());
    List<String> coefNamesList = Stream.of(dinfo.coefNames()).collect(Collectors.toList());
    int numberOfConstraints = linearConstraintF.vec("constraint_numbers").toCategoricalVec().domain().length;
    int numRow = (int) linearConstraintF.numRows();
    List<Integer> rowIndices = IntStream.range(0,numRow).boxed().collect(Collectors.toList());
    String constraintType;
    int rowIndex;
    for (int conInd = 0; conInd < numberOfConstraints; conInd++) {
      if (!rowIndices.isEmpty()) {
        rowIndex = rowIndices.get(0);
        constraintType = linearConstraintF.vec("types").stringAt(rowIndex).toLowerCase();
        if ("equal".equals(constraintType)) {
          extractConstraint(linearConstraintF, rowIndices, equalityC, dinfo, coefNamesList, colNamesList);
        } else if ("lessthanequal".equals(constraintType)) {
          extractConstraint(linearConstraintF, rowIndices, lessThanEqualToC, dinfo, coefNamesList,
                  colNamesList);
        } else {
          throw new IllegalArgumentException("Type of linear constraints can only be Equal to LessThanEqualTo.");
        }
      }
    }
    state.setLinearConstraints(equalityC.toArray(new LinearConstraints[0]), 
            lessThanEqualToC.toArray(new LinearConstraints[0]), false);
  }
  
  public static void extractConstraint(Frame constraintF, List<Integer> rowIndices, List<LinearConstraints> equalC,
                                       DataInfo dinfo, List<String> coefNames, List<String> colNames) {
    List<Integer> processedRowIndices = new ArrayList<>();
    int constraintNumberFrame = (int) constraintF.vec("constraint_numbers").at(rowIndices.get(0));
    LinearConstraints currentConstraint = new LinearConstraints();
    String constraintType = constraintF.vec("types").stringAt(rowIndices.get(0)).toLowerCase();
    boolean standardize = dinfo._normMul != null;
    boolean constantFound = false;
    for (Integer rowIndex : rowIndices) {
      String coefName = constraintF.vec("names").stringAt(rowIndex);
      String currType = constraintF.vec("types").stringAt(rowIndex).toLowerCase();
      if (!coefNames.contains(coefName) && !"constant".equals(coefName))
        throw new IllegalArgumentException("Coefficient name " + coefName + " is not a valid coefficient name.  It " +
                "be a valid coefficient name or it can be constant");
      if ((int) constraintF.vec("constraint_numbers").at(rowIndex) == constraintNumberFrame) {
        if (!constraintType.equals(currType))
          throw new IllegalArgumentException("Constraint type "+" of the same constraint must be the same but is not." +
                  "  Expected type: "+constraintType+".  Actual type: "+currType);
        if ("constant".equals(coefName))
          constantFound = true;
        processedRowIndices.add(rowIndex);
        // coefNames is valid
        int colInd = colNames.indexOf(coefName)-dinfo._cats;
        if (standardize && colNames.contains(coefName) && colInd >= 0) {  // numerical column with standardization
          currentConstraint._constraints.put(coefName, constraintF.vec("values").at(rowIndex)*dinfo._normMul[colInd]);
        } else {  // categorical column, constant or numerical column without standardization
          currentConstraint._constraints.put(coefName, constraintF.vec("values").at(rowIndex));
        }
      }
    }
    if (!constantFound)
      currentConstraint._constraints.put("constant", 0.0);  // put constant of 0.0
    if (currentConstraint._constraints.size() < 3)
      throw new IllegalArgumentException("Linear constraint must have at least two coefficients.  For constraints on" +
              " just one coefficient: "+ constraintF.vec("names").stringAt(0)+", use betaConstraints instead.");
    equalC.add(currentConstraint);
    rowIndices.removeAll(processedRowIndices);
  }
  
  public static double[][] formConstraintMatrix(ComputationState state, List<String> constraintNamesList, int[] betaEqualLessThanInd) {
    // extract coefficient names from constraints
    constraintNamesList.addAll(extractConstraintCoeffs(state));
    // form double matrix
    int numRow = (betaEqualLessThanInd == null ? 0 : ArrayUtils.sum(betaEqualLessThanInd)) +
            (state._equalityConstraintsLinear == null ? 0 : state._equalityConstraintsLinear.length) + 
            (state._lessThanEqualToConstraintsLinear == null ? 0 : state._lessThanEqualToConstraintsLinear.length);
    double[][] initConstraintMatrix = new double[numRow][constraintNamesList.size()];
    fillConstraintValues(state, constraintNamesList, initConstraintMatrix, betaEqualLessThanInd);
    return initConstraintMatrix;
  }
  
  public static void fillConstraintValues(ComputationState state, List<String> constraintNamesList, 
                                          double[][] initCMatrix, int[] betaLessThan) {
    int rowIndex = 0;
    if (state._equalityConstraintsBeta != null)
      rowIndex = extractConstraintValues(state._equalityConstraintsBeta, constraintNamesList, initCMatrix, rowIndex, 
              null);
    if (state._lessThanEqualToConstraintsBeta != null)
      rowIndex= extractConstraintValues(state._lessThanEqualToConstraintsBeta, constraintNamesList, initCMatrix, 
              rowIndex, betaLessThan);
    if (state._equalityConstraintsLinear != null)
      rowIndex = extractConstraintValues(state._equalityConstraintsLinear, constraintNamesList, initCMatrix, rowIndex, null);
    if (state._lessThanEqualToConstraintsLinear != null)
      extractConstraintValues(state._lessThanEqualToConstraintsLinear, constraintNamesList, initCMatrix, rowIndex, null);
  }
  
  public static int extractConstraintValues(LinearConstraints[] constraints, List<String> constraintNamesList, 
                                            double[][] initCMatrix, int rowIndex, int[] betaLessThan) {
    int numConstr = constraints.length;
    for (int index=0; index<numConstr; index++) {
      if (betaLessThan == null || betaLessThan[index] == 1) {
        Set<String> coeffKeys = constraints[index]._constraints.keySet();
        for (String oneKey : coeffKeys) {
          if (constraintNamesList.contains(oneKey))
            initCMatrix[rowIndex][constraintNamesList.indexOf(oneKey)] = constraints[index]._constraints.get(oneKey);
        }
        rowIndex++;
      }
    }
    return rowIndex;
  }
  
  public static void printConstraintSummary(GLMModel model, ComputationState state, String[] coefNames) {
    LinearConstraintConditions cCond = printConstraintSummary(state, coefNames);
    model._output._linear_constraint_states = cCond._constraintDescriptions;
    model._output._all_constraints_satisfied = cCond._allConstraintsSatisfied;
    makeConstraintSummaryTable(model, cCond);
  }
  
  public static void makeConstraintSummaryTable(GLMModel model, LinearConstraintConditions cCond) {
    int numRow = cCond._constraintBounds.length;
    String[] colHeaders = new String[]{"constraint", "values", "condition", "condition_satisfied"};
    String[] colTypes = new String[]{"string", "double", "string", "string"};
    String[] colFormats = new String[]{"%s", "%5.2f", "%s", "%s"};
    TwoDimTable cTable = new TwoDimTable("Beta (if exists) and Linear Constraints Table", null, 
            new String[numRow], colHeaders, colTypes, colFormats, "constraint");
    for (int index=0; index<numRow; index++) {
      cTable.set(index, 0, cCond._constraintNValues[index]);
      cTable.set(index, 1, cCond._constraintValues[index]);
      cTable.set(index, 2, cCond._constraintBounds[index]);
      cTable.set(index, 3, cCond._constraintSatisfied[index]);
    }
    model._output._linear_constraints_table = cTable;
  }
  
  public static LinearConstraintConditions printConstraintSummary(ComputationState state, String[] coefNames) {
    double[] beta = state.beta();
    boolean constraintsSatisfied = true;
    List<String> coefNameList = Arrays.stream(coefNames).collect(Collectors.toList());
    List<String> constraintConditions = new ArrayList<>();
    List<String> cSatisfied = new ArrayList<>();
    List<Double> cValues = new ArrayList<>();
    List<String> cConditions = new ArrayList<>();
    List<String> constraintStrings = new ArrayList<>();
    
    if (state._equalityConstraintsBeta != null)
      constraintsSatisfied = evaluateConstraint(state, state._equalityConstraintsBeta, true, beta,
              coefNameList, "Beta equality constraint: ", constraintConditions, cSatisfied, cValues, 
              cConditions, constraintStrings) && constraintsSatisfied;

    if (state._lessThanEqualToConstraintsBeta != null)
      constraintsSatisfied =  evaluateConstraint(state, state._lessThanEqualToConstraintsBeta, false, 
              beta, coefNameList, "Beta inequality constraint: ", constraintConditions, cSatisfied, cValues,
              cConditions, constraintStrings) && constraintsSatisfied;

      if (state._equalityConstraintsLinear != null)
        constraintsSatisfied = evaluateConstraint(state, state._equalityConstraintsLinear, true, beta, 
                coefNameList, "Linear equality constraint: ", constraintConditions, cSatisfied, cValues,
                cConditions, constraintStrings) && constraintsSatisfied;

      if (state._lessThanEqualToConstraints != null)
        constraintsSatisfied = evaluateConstraint(state, state._lessThanEqualToConstraints, false, beta, 
                coefNameList, "Linear inequality constraint: ", constraintConditions, cSatisfied, cValues,
                cConditions, constraintStrings) && constraintsSatisfied;

      return new LinearConstraintConditions(constraintConditions.stream().toArray(String[]::new), 
              cSatisfied.stream().toArray(String[]::new), cValues.stream().mapToDouble(x->x).toArray(), 
              cConditions.stream().toArray(String[]::new), constraintStrings.stream().toArray(String[]::new), 
              constraintsSatisfied);
  }

  /**
   * Print constraints without any standardization applied so that people can see the setting in their original
   * form without standardization.  The beta coefficients are non-standardized.  However, if standardized, the
   * constraint values are changed to accommodate the standardized coefficients.
   */
  public static boolean evaluateConstraint(ComputationState state, LinearConstraints[] constraints, boolean equalityConstr, 
                                        double[] beta, List<String> coefNames, String startStr, 
                                           List<String> constraintCond, List<String> cSatisfied, List<Double> cValues, 
                                           List<String> cConditions, List<String> constraintsStrings) {
    int constLen = constraints.length;
    LinearConstraints oneC;
    String constrainStr;
    boolean allSatisfied = true;
    for (int index=0; index<constLen; index++) {
      oneC = constraints[index];
      constrainStr = constraint2Str(oneC, startStr, state);
      evalOneConstraint(oneC, beta, coefNames);
      constraintsStrings.add(constrainStr + " = " + oneC._constraintsVal);
      if (equalityConstr) {
        if (Math.abs(oneC._constraintsVal) <= EPS) { // constraint satisfied
          constraintCond.add( constrainStr + " == 0 is statisfied.");
          cSatisfied.add("true");
        } else {
          constraintCond.add(constrainStr + " = " + oneC._constraintsVal + " and does not satisfy" +
                  " the condition == 0.");
          cSatisfied.add("false");
          allSatisfied = false;
        }
        cConditions.add("== 0");
      } else {
        if (oneC._constraintsVal <= 0) { // constraint satisfied
          constraintCond.add(constrainStr + " <= "  +oneC._constraintsVal + " which satisfies the" +
                  " constraint <= 0.");
          cSatisfied.add("true");
        } else {
          constraintCond.add(constrainStr+" = " + oneC._constraintsVal + " and does not satisfy the" +
                  " condition <= 0");
          cSatisfied.add("false");
          allSatisfied = false;
        }
        cConditions.add("<= 0");
      }
      cValues.add(oneC._constraintsVal);
    }
    return allSatisfied;
  }

  public static String constraint2Str(LinearConstraints oneConst, String startStr, ComputationState state) {
      boolean isBetaConstraint = oneConst._constraints.size() < 3;
      StringBuilder sb = new StringBuilder();
      sb.append(startStr);
      DataInfo dinfo = state.activeData();
      boolean standardize = dinfo._normMul != null;
      List<String> trainNames = stream(dinfo.coefNames()).collect(Collectors.toList());
      double constantVal = 0;
      int colInd = -1;
      int coefOffset = (dinfo._catOffsets == null || dinfo._catOffsets.length == 0) ? 0 : dinfo._catOffsets[dinfo._catOffsets.length - 1];
      for (String coefName : oneConst._constraints.keySet()) {
        double constrVal = oneConst._constraints.get(coefName);
        if (constrVal != 0) {
          if ("constant".equals(coefName)) {
            constantVal = constrVal;
          } else if (trainNames.contains(coefName)) {
            colInd = trainNames.indexOf(coefName) - coefOffset;
            if (standardize && colInd >= 0 && !isBetaConstraint) {
              if (constrVal > 0)
                sb.append('+');
              sb.append(constrVal / dinfo._normMul[colInd]);
            } else {
              sb.append(constrVal);
            }
            sb.append('*');
            sb.append(coefName);
          }
        }
      }
      // add constant value here
      // add constant value to the end
      if (constantVal != 0) {
        if (constantVal > 0)
          sb.append("+");
        if (isBetaConstraint && colInd >= 0 && standardize)
          sb.append(constantVal * dinfo._normMul[colInd]);
        else
          sb.append(constantVal);
      }
      return sb.toString();
  }
  
  public static List<String> extractConstraintCoeffs(ComputationState state) {
    List<String> tConstraintCoeffName = new ArrayList<>();
    boolean nonZeroConstant = false;
    if (state._equalityConstraintsBeta != null)
      nonZeroConstant = extractCoeffNames(tConstraintCoeffName, state._equalityConstraintsBeta);

    if (state._lessThanEqualToConstraintsBeta != null)
      nonZeroConstant = extractCoeffNames(tConstraintCoeffName, state._lessThanEqualToConstraintsBeta) || nonZeroConstant;

    if (state._equalityConstraintsLinear != null)
      nonZeroConstant = extractCoeffNames(tConstraintCoeffName, state._equalityConstraintsLinear) || nonZeroConstant;

    if (state._lessThanEqualToConstraintsLinear != null)
      nonZeroConstant = extractCoeffNames(tConstraintCoeffName, state._lessThanEqualToConstraintsLinear) || nonZeroConstant;
    
    // remove duplicates in the constraints names
    Set<String> noDuplicateNames = new HashSet<>(tConstraintCoeffName);
    if (!nonZeroConstant) // no non-Zero constant present
      noDuplicateNames.remove("constant");
    return new ArrayList<>(noDuplicateNames);
  }
  
  public static boolean extractCoeffNames(List<String> coeffList, LinearConstraints[] constraints) {
    int numConst = constraints.length;
    boolean nonZeroConstant = false;
    for (int index=0; index<numConst; index++) {
      Set<String> keys = constraints[index]._constraints.keySet();
      coeffList.addAll(keys);
      if (keys.contains("constant"))
        nonZeroConstant = constraints[index]._constraints.get("constant") != 0.0;
    }
    return nonZeroConstant;
  }
  
  public static List<String> foundRedundantConstraints(ComputationState state, final double[][] initConstraintMatrix) {
    Matrix constMatrix = new Matrix(initConstraintMatrix);
    Matrix constMatrixLessConstant = constMatrix.getMatrix(0, constMatrix.getRowDimension() -1, 1, constMatrix.getColumnDimension()-1);
    Matrix constMatrixTConstMatrix = constMatrixLessConstant.times(constMatrixLessConstant.transpose());
    int rank = constMatrixLessConstant.rank();
    if (rank < constMatrix.getRowDimension()) { // redundant constraints are specified
      double[][] rMatVal = constMatrixTConstMatrix.qr().getR().getArray();
      List<Double> diag = IntStream.range(0, rMatVal.length).mapToDouble(x->Math.abs(rMatVal[x][x])).boxed().collect(Collectors.toList());
      int[] sortedIndices = IntStream.range(0, diag.size()).boxed().sorted((i, j) -> diag.get(i).compareTo(diag.get(j))).mapToInt(ele->ele).toArray();
      List<Integer> duplicatedEleIndice = IntStream.range(0, diag.size()-rank).map(x -> sortedIndices[x]).boxed().collect(Collectors.toList());
      return genRedundantConstraint(state, duplicatedEleIndice);
    }
    return null;
  }
  
  public static List<String> genRedundantConstraint(ComputationState state, List<Integer> duplicatedEleIndics) {
    List<String> redundantConstraint = new ArrayList<>();
    for (Integer redIndex : duplicatedEleIndics)
      redundantConstraint.add(grabRedundantConstraintMessage(state, redIndex));

    return redundantConstraint;
  }

  public static String grabRedundantConstraintMessage(ComputationState state, Integer constraintIndex) {
    // figure out which constraint among state._fromBetaConstraints, state._equalityConstraints, 
    // state._lessThanEqualToConstraints is actually redundant
    LinearConstraints redundantConst = getConstraintFromIndex(state, constraintIndex);
    if (redundantConst != null) {
      boolean standardize = state.activeData()._normMul != null ? true : false;
      boolean isBetaConstraint = redundantConst._constraints.size() < 3;
      StringBuilder sb = new StringBuilder();
      DataInfo dinfo = state.activeData();
      List<String> trainNames = stream(dinfo.coefNames()).collect(Collectors.toList());
      sb.append("This constraint is redundant ");
      double constantVal = 0;
      int colInd = -1;
      int coefOffset = (dinfo._catOffsets == null || dinfo._catOffsets.length == 0) ? 0 : dinfo._catOffsets[dinfo._catOffsets.length - 1];
      for (String coefName : redundantConst._constraints.keySet()) {
        double constrVal = redundantConst._constraints.get(coefName);
        if (constrVal != 0) {
          if ("constant".equals(coefName)) {
            constantVal = constrVal;
          } else if (trainNames.contains(coefName)) {
            colInd = trainNames.indexOf(coefName) - coefOffset;
            if (standardize && colInd >= 0 && !isBetaConstraint) {
              if (constrVal > 0)
                sb.append('+');
              sb.append(constrVal * dinfo._normMul[colInd]);
            } else {
              sb.append(constrVal);
            }
            sb.append('*');
            sb.append(coefName);
          }
        }
      }
      // add constant value here
      // add constant value to the end
      if (constantVal != 0) {
        if (constantVal > 0)
          sb.append("+");
        if (isBetaConstraint && colInd >= 0)
          sb.append(constantVal * dinfo._normMul[colInd]);
        else
          sb.append(constantVal);
      }
      sb.append(" <= or == 0.");
      sb.append(" Please remove it from your beta/linear constraints.");
      return sb.toString();
    } else {
      return null;
    }
  }
  
  public static LinearConstraints getConstraintFromIndex(ComputationState state, Integer constraintIndex) {
    int constIndexWOffset = constraintIndex;
    if (state._equalityConstraintsBeta != null) {
      if (constIndexWOffset < state._equalityConstraintsBeta.length) {
        return state._equalityConstraintsBeta[constIndexWOffset];
      } else {
        constIndexWOffset -= state._equalityConstraintsBeta.length;
      }
    }
    
    if (state._lessThanEqualToConstraintsBeta != null) {
      if (constIndexWOffset < state._lessThanEqualToConstraintsBeta.length) {
        return state._lessThanEqualToConstraintsBeta[constIndexWOffset];
      } else {
        constIndexWOffset -= state._lessThanEqualToConstraintsBeta.length;
      }
    }
    
    if (state._equalityConstraintsLinear != null) {
      if (constIndexWOffset < state._equalityConstraintsLinear.length) {
        return state._equalityConstraintsLinear[constIndexWOffset];
      } else {
        constIndexWOffset -= state._equalityConstraintsLinear.length;
      }
    }
    
    if (state._lessThanEqualToConstraints != null && constIndexWOffset < state._lessThanEqualToConstraints.length) {
      return state._lessThanEqualToConstraints[constIndexWOffset];
    }
    return null;
  }

  /***
   *
   * This method will evaluate the value of a constraint given the GLM coefficients and the coefficicent name list.
   * Note that the beta should be the normalized beta if standardize = true and the coefficients to the coefficients
   * are set correctly for the standardized coefficients.
   */
  public static void evalOneConstraint(LinearConstraints constraint, double[] beta, List<String> coefNames) {
    double sumV = 0.0;
    Map<String, Double> constraints = constraint._constraints;
    for (String coef : constraints.keySet()) {
      if ("constant".equals(coef))
        sumV += constraints.get(coef);
      else
        sumV += constraints.get(coef)*beta[coefNames.indexOf(coef)];
    }
    constraint._constraintsVal = sumV;
  }

  /***
   *
   * The initial value of lambda values really do not matter that much.  The lambda update will take care of making
   * sure it is the right sign at the end of the iteration.
   * 
   */
  public static void genInitialLambda(Random randObj, LinearConstraints[] constraints, double[] lambda) {
    int numC = constraints.length;
    LinearConstraints oneC;
    for (int index=0; index<numC; index++) {
      lambda[index] = Math.abs(randObj.nextGaussian());
      oneC = constraints[index];
      if (oneC._active && oneC._constraintsVal < 0)
        lambda[index] *= -1;
    }
  }
  
  
  public static double[][] sumGramConstribution(ConstraintsGram[] gramConstraints, int numCoefs) {
    if (gramConstraints == null)
      return null;
    double[][] gramContr = new double[numCoefs][numCoefs];  // includes intercept terms
    int cGramSize = gramConstraints.length;
    ConstraintsGram oneGram;
    int coef1, coef2;
    for (int index=0; index < cGramSize; index++) {
      oneGram = gramConstraints[index];
      if (oneGram._active) {  // only process the contribution if the constraint is active
        for (CoefIndices key : oneGram._coefIndicesValue.keySet()) {
          coef1 = key._firstCoefIndex;
          coef2 = key._secondCoefIndex;
          gramContr[coef1][coef2] += oneGram._coefIndicesValue.get(key);
          if (coef1 != coef2)
            gramContr[coef2][coef1] = gramContr[coef1][coef2];
        }
      }
    }
    return gramContr;
  }

  /***
   * 
   * Add contribution of constraints to objective/likelihood/gradient.
   *
   */
  public static void addConstraintGradient(double[] lambda, ConstraintsDerivatives[] constraintD,
                                           GLM.GLMGradientInfo gradientInfo) {
    int numConstraints = lambda.length;
    ConstraintsDerivatives oneC;
    for (int index=0; index<numConstraints; index++) {
      oneC = constraintD[index];
      if (oneC._active) {
        for (Integer key: oneC._constraintsDerivative.keySet()) {
          gradientInfo._gradient[key] += lambda[index]*oneC._constraintsDerivative.get(key);
        }
      }
    }
  }

  /***
   * This method adds the contribution to the gradient from the penalty term ck/2*transpose(h(beta))*h(beta)
   */
  public static void addPenaltyGradient(ConstraintsDerivatives[] constraintDeriv, LinearConstraints[] constraintD,
                                           GLM.GLMGradientInfo gradientInfo, double ck) {
    int numConstraints = constraintDeriv.length;
    ConstraintsDerivatives oneD;
    LinearConstraints oneConts;
    for (int index=0; index<numConstraints; index++) {
      oneD = constraintDeriv[index];
      if (oneD._active) {
        oneConts = constraintD[index];
        for (Integer coefK : oneD._constraintsDerivative.keySet()) {
          gradientInfo._gradient[coefK] += ck*oneConts._constraintsVal*oneD._constraintsDerivative.get(coefK);
        }
      }
    }
  }

  /***
   *  This method will update the constraint parameter values cKCS, epsilonkCS, etakCS.  Refer to the doc, Algorithm 
   *  19.1
   */
  public static void updateConstraintParameters(ComputationState state, double[] lambdaEqual, double[]lambdaLessThan, 
                                                LinearConstraints[] equalConst, LinearConstraints[] lessThanConst, 
                                                GLMModel.GLMParameters parms) {
    // calculate ||h(beta)|| square, ||gradient|| square
    double hBetaMag = state._csGLMState._constraintMagSquare;
    if (hBetaMag <= state._csGLMState._etakCSSquare) {  // implement line 26 to line 29 of Algorithm 19.1
      if (equalConst != null)
        updateLambda(lambdaEqual, state._csGLMState._ckCS, equalConst);
      if (lessThanConst != null)
        updateLambda(lambdaLessThan, state._csGLMState._ckCS, lessThanConst);
      state._csGLMState._epsilonkCS = state._csGLMState._epsilonkCS/state._csGLMState._ckCS;
      state ._csGLMState._etakCS = state._csGLMState._etakCS/Math.pow(state._csGLMState._ckCS, parms._constraint_beta);
    } else {  // implement line 31 to 34 of Algorithm 19.1
      state._csGLMState._ckCS = state._csGLMState._ckCS*parms._constraint_tau;
      state._csGLMState._ckCSHalf = state._csGLMState._ckCS*0.5;
      state._csGLMState._epsilonkCS = state._csGLMState._epsilon0/state._csGLMState._ckCS;
      state._csGLMState._etakCS = parms._constraint_eta0/Math.pow(state._csGLMState._ckCS, parms._constraint_alpha);
    }
    state._csGLMState._epsilonkCSSquare = state._csGLMState._epsilonkCS*state._csGLMState._epsilonkCS;
    state._csGLMState._etakCSSquare = state ._csGLMState._etakCS*state._csGLMState._etakCS;
  }
  
  public static void calculateConstraintSquare(ComputationState state, LinearConstraints[] equalConst, 
                                               LinearConstraints[] lessThanConst) {
    double sumSquare = 0;
    if (equalConst != null)
      sumSquare += stream(equalConst).mapToDouble(x -> x._constraintsVal*x._constraintsVal).sum();
    if (lessThanConst != null)  // only counts magnitude when the constraint is active
      sumSquare += stream(lessThanConst).filter(x -> x._active).mapToDouble(x -> x._constraintsVal*x._constraintsVal).sum();
    state._csGLMState._constraintMagSquare = sumSquare;
  }
  
  public static void updateLambda(double[] lambda, double ckCS, LinearConstraints[] constraints) {
    int numC = constraints.length;
    LinearConstraints oneC;
    for (int index=0; index<numC; index++) {
      oneC = constraints[index];
      if (oneC._active)
        lambda[index] += ckCS*oneC._constraintsVal;
    }
  }

  /***
   * This method will check if the stopping conditions for constraint GLM are met and they are namely:
   * 1. ||gradient of L with respect to beta and with respect to lambda|| <= epsilon
   * 2. ||h(beta)|| square <= epsilon if satisfied is false and ||h(beta)|| square == 0 if satisfied is true
   * 
   * If the stopping conditions are met, it will return true, else it will return false.
   * See the doc, Algorithm 19.1, line 36.
   */
  public static boolean constraintsStop(GLM.GLMGradientInfo gradientInfo, ComputationState state) {
    state._csGLMState._gradientMagSquare = innerProduct(gradientInfo._gradient, gradientInfo._gradient);
    if (state._csGLMState._constraintMagSquare <= ComputationState.EPS_CS && 
            state._csGLMState._gradientMagSquare <= ComputationState.EPS_CS_SQUARE)
      return true;
    return false;
  }
  
  public static boolean activeConstraints(LinearConstraints[] equalityC, LinearConstraints[] lessThanEqualToC) {
    if (equalityC != null)
      return true;
    return stream(lessThanEqualToC).filter(x -> x._active).count() > 0;
  }

  /***
   * This method calls getGradient to calculate the gradient, likelhood and objective function values.  In addition,
   * it will add to the gradient and objective function the contribution from the linear constraints.
   */
  public static GLM.GLMGradientInfo calGradient(double[] betaCnd, ComputationState state, GLM.GLMGradientSolver ginfo,
                                                double[] lambdaE, double[] lambdaL, LinearConstraints[] constraintE,
                                                LinearConstraints[] constraintL) {
    // todo: need to add support for predictors removed for whatever reason
    // calculate gradients
    GLM.GLMGradientInfo gradientInfo = ginfo.getGradient(betaCnd, state); // gradient without constraints
    boolean hasEqualConstraints = constraintE != null;
    boolean hasLessConstraints = constraintL != null;
    // add gradient, objective and likelihood contribution from constraints
    if (hasEqualConstraints) {
      addConstraintGradient(lambdaE, state._derivativeEqual, gradientInfo);
      addPenaltyGradient(state._derivativeEqual, constraintE, gradientInfo, state._csGLMState._ckCS);
      gradientInfo._objVal += state.addConstraintObj(lambdaE, constraintE, state._csGLMState._ckCSHalf);
    }
    if (hasLessConstraints) {
      addConstraintGradient(lambdaL, state._derivativeLess, gradientInfo);
      addPenaltyGradient(state._derivativeLess, constraintL, gradientInfo, state._csGLMState._ckCS);
      gradientInfo._objVal += state.addConstraintObj(lambdaL, constraintL, state._csGLMState._ckCSHalf);
    }
    return gradientInfo;
  }

  /**
   * Simple method to all linear constraints given the coefficient values.  In addition, it will determine if a 
   * linear constraint is active.  Only active constraints are included in the objective function calculations.
   * 
   * For an equality constraint, any constraint value not equal to zero is active.
   * For a less than or equality constraint, any constraint value that exceed zero is active.
   */
  public static void updateConstraintValues(double[] betaCnd, List<String> coefNames, 
                                            LinearConstraints[] equalityConstraints, 
                                            LinearConstraints[] lessThanEqualToConstraints) {
    if (equalityConstraints != null) // equality constraints
      Arrays.stream(equalityConstraints).forEach(constraint -> {
        evalOneConstraint(constraint, betaCnd, coefNames);
        constraint._active = (Math.abs(constraint._constraintsVal) > EPS2);
      });

    if (lessThanEqualToConstraints != null) // less than or equal to constraints
      Arrays.stream(lessThanEqualToConstraints).forEach(constraint -> {
        evalOneConstraint(constraint, betaCnd, coefNames);
        constraint._active = constraint._constraintsVal > 0;
      });
  }
  
  public static String[] collinearInConstraints(String[] collinear_cols, String[] constraintNames) {
    List<String> cNames = Arrays.stream(constraintNames).collect(Collectors.toList());
    return Arrays.stream(collinear_cols).filter(x -> (cNames.contains(x))).toArray(String[]::new);
  }
  
  public static int countNumConst(ComputationState state) {
    int numConst = 0;
    // check constraints from beta constrains
    numConst += state._equalityConstraintsBeta == null ? 0 : state._equalityConstraintsBeta.length;
    numConst += state._lessThanEqualToConstraintsBeta == null ? 0 : state._lessThanEqualToConstraintsBeta.length/2;
    numConst += state._equalityConstraintsLinear == null ? 0 : state._equalityConstraintsLinear.length;
    numConst += state._lessThanEqualToConstraints == null ? 0 : state._lessThanEqualToConstraints.length;
    return numConst;
  }
  
  public static class LinearConstraintConditions {
    final String[] _constraintDescriptions; // 0.5C2 + 1.3C2+3
    final String[] _constraintSatisfied;
    final double[] _constraintValues;
    final String[] _constraintBounds;  // == 0 for equality constraint, <= 0 for lessThanEqual to constraint
    final String[] _constraintNValues; // 0.5C2+1.4C2-0.5 = 2.0
    final boolean _allConstraintsSatisfied;
    
    public LinearConstraintConditions(String[] constraintC, String[] cSatisfied, double[] cValues, String[] cBounds, 
                                      String[] cNV, boolean conditionS) {
      _constraintDescriptions = constraintC;
      _constraintSatisfied = cSatisfied;
      _constraintValues = cValues;
      _constraintBounds = cBounds;
      _constraintNValues = cNV;
      _allConstraintsSatisfied = conditionS;
    }
  }
}
