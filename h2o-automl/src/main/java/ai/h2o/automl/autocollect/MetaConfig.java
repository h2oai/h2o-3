package ai.h2o.automl.autocollect;

import water.DKV;
import water.Key;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.fvec.Vec;
import water.parser.ParseSetup;
import water.parser.ParserType;
import water.util.ArrayUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

// Meta file has the following format for each dataset:
//    <datasetName>
//    <relPathToDataset>
//    <parse_type>  // svmlight, csv, arff ?
//    <task>        // binary_classification, multiclass_classification, regression … possibly other types in future
//    <x>
//    <col_types>  // all numerical, unless otherwise specified in svmlight-style syntax
//    <y>
//
public class MetaConfig {
  private static final byte BCLASS=0;  // binary classification
  private static final byte MCLASS=1;  // multiclass classification
  private static final byte REG=2;     // regression

  private File _f;
  private int[] _x;
  private int _y;
  private String _datasetName;
  private ParseSetup _ps;
  private Key _nfskey;
  private Frame _fr;
  private byte[] _colTypes;
  private byte _task;
  private ParserType _parseType;  // GUESS(false), ARFF(true), XLS(false), XLSX(false), CSV(true), SVMLight(true);
  private int _ncol;

  public static MetaConfig[] readMeta(String pathToMetaFile) {
    ArrayList<MetaConfig> metaConfigs = new ArrayList<>();
    try {
      BufferedReader br = new BufferedReader(new FileReader(pathToMetaFile));
      String line;
      while((line=br.readLine())!=null) {
        metaConfigs.add(new MetaConfig().parseLines(
                line          /*datasetName*/,
                br.readLine() /*relPath*/,
                br.readLine() /*parseType*/,
                br.readLine() /*task*/,
                br.readLine() /*x*/,
                br.readLine() /*xTypes*/,
                br.readLine() /*y*/));
      }
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
    return metaConfigs.toArray(new MetaConfig[metaConfigs.size()]);
  }

  public int[] x() { return _x; }
  public int   y() { return _y; }
  public String name() { return _datasetName; }
  public Frame frame() { return _fr; }

  // some stupid public methods for testing
  public void setncol(int n) { _ncol=n; }
  public void setDefaultTypes() { _colTypes = new byte[_ncol]; Arrays.fill(_colTypes,Vec.T_NUM); }
  public void sety() { _y=_ncol-1; }
  public void setx() { _x= ArrayUtils.seq(0, _ncol); }

  private MetaConfig parseLines(String datasetName, String relPathToDataset, String parseType, String task, String x, String types, String y) {
    try {
      readName(datasetName);
      readParseType(parseType);
      readY(y);
      parseSetup(relPathToDataset);
      readTask(task);
      readTypes(types);
      readX(x);
    } catch(Exception ex) {
      ex.printStackTrace();
      return null;
    }
    return this;
  }

  // cases: these are all 0-based indices, with INclusive max.
  //  1,2,3,4,5
  //  1:3, 5:10, 58,99
  //  1
  //  90:100
  public void readX(String line) {
    ArrayList<Integer> preds = new ArrayList<>();
    String []idxs = line.trim().split(",");
    for (String idx : idxs) {
      idx=idx.trim();
      if (idx.contains(":") ) {
        String[] range = idx.split(":");
        int min = Integer.valueOf(range[0]);
        int max;
        try {
          max = Integer.valueOf(range[1]);
        } catch( NumberFormatException ex) {
          int len = range[1].length();
          if (len == "ncols".length() && range[1].equals("ncols"))    max = _ncol-1;
          else if (len == "ncol".length() && range[1].equals("ncol")) max = _ncol-1;
          else throw new IllegalArgumentException("junk found parsing predictor columns: " + idx.substring(2) + ". Expects an integer, or the String `ncol` or `ncols`");
        }
        for (int m = min; m <= max; ++m) preds.add(m);
      } else preds.add(Integer.valueOf(idx));
    }
    _x = new int[preds.size()];
    for(int i=0;i<preds.size();++i) _x[i] = preds.get(i);
  }
  private void readY(String line) { _y = Integer.valueOf(line.trim()); }
  private void readName(String line) { _datasetName = line.trim(); }
  public void parseFrame() {
    if( DKV.getGet(_nfskey)==null )
      _nfskey = NFSFileVec.make(_f)._key;
    _ps.setColumnTypes(_colTypes);
    _fr=AutoCollect.parseFrame(_ps,_nfskey);
  }
  private void readParseType(String line) {
    line = line.trim().toLowerCase();
    switch( line ) {
      case "d":
      case "default":
      case "guess": _parseType = ParserType.GUESS; break;
      case "csv":   _parseType = ParserType.CSV;   break;
      case "xls":   _parseType = ParserType.XLS;   break;
      case "xlsx":  _parseType = ParserType.XLSX;  break;
      case "arff":  _parseType = ParserType.ARFF;  break;
      case "svmlight": _parseType = ParserType.SVMLight; break;
      default:
        throw new IllegalArgumentException("Unknown parse type: " + line + ". Must be one of: <d,default,guess,csv,xls,xlsx,arff,svmlight>");
    }
  }
  private void readTask(String line) {
    line = line.trim().toLowerCase();
    switch( line ) {
      case "b":
      case "binary":
      case "binary_classification": _task=BCLASS; break;
      case "m":
      case "multiclass":
      case "multinomial":
      case "multinomial_classification": _task=MCLASS; break;
      case "r":
      case "reg":
      case "regression": _task=REG; break;
      default:
        throw new IllegalArgumentException("Unknown task type: " + line + ". Must be one of <binary_classification,multinomial_classification,regression>");
    }
    _colTypes[_y]= isClass() ? Vec.T_CAT : Vec.T_NUM;
  }

  // the types of columns
  private void readTypes(String line) {
    line = line.trim().toLowerCase();
    if( !(line.equals("d") || line.equals("default") || line.equals("guess")) ) {  // use the types from ParseSetup guesser
      switch (line) {
        case "n":
        case "num":
        case "numeric":
        case "enum":
        case "c":
        case "cat":
        case "categorical": fillColType(-1,line); break;
        default:  // assumes ',' separated list of pairs <colidx:type>, could be single <colidx:type>
          String[] types = line.split(",");
          if( line.contains(":") ) parseColTypesSVMLightStyle(types);
          else                     parseColTypesCommaSep(types);
      }
    }
  }

  private void fillColType(int idx, String type) {
    switch( type ) {
      case "n":
      case "num":
      case "numeric": {
        if( idx==-1 ) {
          byte ytype=_colTypes[_y];
          Arrays.fill(_colTypes, Vec.T_NUM);
          _colTypes[_y] = ytype;
        } else _colTypes[idx] = Vec.T_NUM;
        break;
      }
      case "c":
      case "cat":
      case "categorical": {
        if( idx==-1 ) {
          byte ytype=_colTypes[_y];
          Arrays.fill(_colTypes, Vec.T_CAT);
          _colTypes[_y] = ytype;
        } else _colTypes[idx] = Vec.T_CAT;
        break;
      }
      default:
        throw new IllegalArgumentException("Unknown type: " + type + (idx==-1?"": " at index " + idx));
    }
  }

  private void parseColTypesCommaSep(String[] types) {
    for(int i=0;i<types.length;++i)
      fillColType(i,types[i]);
  }

  private void parseColTypesSVMLightStyle(String[] pairs) {
    // svmlight-style column types:  <colIdx>:<colType>
    for(String type: pairs) {
      if( !type.contains(":") )
        throw new IllegalArgumentException("Expected \":\"-separated pair <colIdx>:<colType>. Don't know what to do with: " + type);
      String pair[] = type.split(":");
      fillColType(Integer.valueOf(pair[0]), pair[1]);
    }
  }

  private void parseSetup(String line) {
    _f = new File(line);
    assert _f.exists():" file not found.";
    NFSFileVec nfs = NFSFileVec.make(_f);
    _nfskey = nfs._key;
    _ps = AutoCollect.paresSetup(nfs,_parseType);
    _ncol = _ps.getColumnTypes().length;
    _colTypes = _ps.getColumnTypes();
  }

  void delete() { if(_fr!=null) _fr.delete(); _fr=null; }
  boolean isClass() { return _task==MCLASS || _task==BCLASS; }
}
