package water.userapi

import java.io.{File, IOException}

import water.{Key, Scope}
import water.fvec._
import water.parser.ParseDataset
import water.udf.specialized.Enums
import water.util.FileUtils

import scala.language.postfixOps

/**
  * ETL library for User API
  *
  * Usage: see, e.g. UserapiGBMTest#testStochasticGBMHoldout
  *
  * Created by vpatryshev on 2/27/17.
  */
object ETL {

  def readFile(path: String): Frame = read(FileUtils.locateFile(path.trim()))

  def read(file: File): Frame = {
    try {
      FileUtils.checkFile(file, file.getCanonicalPath)
      val nfs = NFSFileVec.make(file)
      Scope.track(ParseDataset.parse(Key.make(), nfs._key))
    } catch {
      case ioe: IOException =>
          throw new DataException("Could not read " + file, ioe)
    }
  }

  def onVecs(map: Map[String, Vec]): Frame = {
    val n = map.size
    new Frame(
      map.keySet.toArray,
      map.values.toArray)
  }

  def oneHotEncode(frame: Frame, ignore: String*): Frame = {
    try {
      Enums.oneHotEncoding(frame, ignore.toArray)
    } catch  {
      case ioe: IOException =>
      throw new DataException("Failed to do oneHotEncoding", ioe)
    }
  }

  def addSplittingColumn(frame: Frame, colName: String, ratio: Double, seed: Long): Option[Frame] = H2ODataset(frame).addSplittingColumn(colName, ratio, seed) map (_.frame)

  /**
    * From a given frame, select, into a new frame only those rows
    * that satisfy the following condition:
    * The value of a categorical column named `colName` matches
    * the domain value named `what`.
    * E.g. if the domain is String[]{"red", "white", "blue"},
    * and `what`="white",
    * only those rows are accepted where the vec value == 1.
    *
    * @param frame source frame
    * @param what domain value to select
    * @param colName categorical column which we filter
    * @return a new frame with rows selected from the original frame
    */
  private def selectCategory(frame: Frame, what: String, colName: String): Option[Frame] = H2ODataset(frame).selectCategory(what, colName)

  private def splitBy(frame: Frame, colName: String, splittingDom: Array[String]): Map[String, Frame] = H2ODataset(frame).splitBy(colName, splittingDom)
  
  def stratifiedSplit(frame: Frame, colName: String, ratio: Double, seed: Long = System.currentTimeMillis()): Option[(Frame, Frame)] = H2ODataset(frame).stratifiedSplit(colName, ratio, seed)

  def renameColumns(frame: Frame, newNames: String*) = H2ODataset(frame).renameColumns(newNames:_*)

  def makeCategorical(frame: Frame, colName: String) = H2ODataset(frame).makeCategorical(colName)

  def length(f: Frame): Long = H2ODataset(f).length

  def removeColumn(f: Frame, names: String*) = H2ODataset(f).removeColumn(names:_*)

  def domainOf(frame: Frame, colName: String): Option[Array[String]] = H2ODataset(frame).domainOf(colName)
}
