package water.userapi

import java.io.{File, IOException}

import water.Scope._
import water.fvec._
import water.parser.ParseDataset
import water.rapids.ast.prims.advmath.AstStratifiedSplit
import water.rapids.ast.prims.advmath.AstStratifiedSplit._
import water.udf.specialized.Enums
import water.util.{FileUtils, VecUtils}
import water.{DKV, Iced, Key}

import scala.language.postfixOps

/**
  * Simplified Frame wrapper for simple usages in Scala world
  * Created by vpatryshev on 3/26/17.
  */
case class H2ODataset(frame: Frame) extends Iced[H2ODataset] {

  def oneHotEncode: H2ODataset = oneHotEncodeExcluding()
  
  def oneHotEncodeExcluding(ignore: String*): H2ODataset = {
    try {
      val res = for {
        hotFrame <- Option(Enums.oneHotEncoding(frame, ignore.toArray))
      } yield H2ODataset(hotFrame)
      
      res getOrElse {throw DataException("Failed to build oneHotEncoding")}
    } catch {
      case ioe: IOException =>
        throw DataException("Failed to do oneHotEncoding", Some(ioe))
    }
  }

  private def frameClone: Frame = {
    val newFrame = frame.clone
    newFrame._key = null
    trackFrame(newFrame)
  }
  
  // the new vec is named (hard-coded so far) "test_train_split"
  def addSplittingColumn(colName: String, ratio: Double, seed: Long): Option[H2ODataset] = {
      for {
        vec <- vec(colName)
        splitter = trackFrame(AstStratifiedSplit.split(vec, ratio, seed, SplittingDom))
        newFrame = frameClone
        _ = newFrame.add(splitter.names(), splitter.vecs)
      } yield new H2ODataset(newFrame)
    }

  def stratifiedSplit(colName: String, ratio: Double, seed: Long = System.currentTimeMillis()): Option[(Frame, Frame)] = {
    val result = for {
      blend <- addSplittingColumn(colName, ratio, seed)
      splitMap: Map[String, Frame] = blend.splitBy(TestTrainSplitColName, SplittingDom)
      trainAndValid: Array[Option[Frame]] = SplittingDom map splitMap.get
      train <- trainAndValid(0)
      valid <- trainAndValid(1)     
    } yield (train, valid)
    
    result
  }

  private[userapi] def selectCategory(what: String, colName: String): Option[Frame] = {

    def filter(catIndex: Int) = new FrameFilter(frame, colName) {
      def accept(c: Chunk, i: Int) = c.at8(i) == catIndex
    }

    for {
      vec <- vec(colName)
      domArray <- Option(vec.domain)
      domain = domArray.toList 
      catIndex = domain.indexOf(what) if 0 <= catIndex
      selected = filter(catIndex).eval()
    } yield selected
  }

  private[userapi] def splitBy(colName: String, splittingDom: Array[String]): Map[String, Frame] = {
    splittingDom map (cat => cat -> selectCategory(cat, colName)) collect {
      case (k, Some(f)) => k -> f
    } toMap
  }
  
  def domain: Option[Array[String]] = Option(frame.names)

  def vec(name: String): Option[Vec] = Option(frame.vec(name))

  def dropColumn(i: Int) = Option(frame.remove(i))

  def renameColumns(newNames: String*): Unit = {
    System.arraycopy(newNames, 0, frame._names, 0, Math.min(frame.numCols(), newNames.length))
  }

  def makeCategorical(colName: String): Unit = {
    for {
      srcCol <- vec(colName)
      colType = srcCol.get_type
      categorical: Vec = colType match {
        case Vec.T_STR => VecUtils.stringToCategorical(srcCol)
        case Vec.T_NUM => VecUtils.numericToCategorical(srcCol)
        case _ => srcCol
      }
    } {
      frame.replace(colName, track(categorical))
      srcCol.remove()
    }
  }

  def domainOf(colName: String): Option[Array[String]] = vec(colName) map (_.domain)

  def length: Long = Option(frame.anyVec) map (_.length) getOrElse 0

  def removeColumn(names: String*): Unit = {
    for {
      name <- names
      v <- Option(frame.remove(name))
    } {
      v.remove()
      untrack(v._key)
    }
  }

}

object H2ODataset {

  def get(frameKey: Key[Frame]): Option[H2ODataset] =
    Option(DKV.getGet(frameKey)) map (new H2ODataset(_))
  
  private def fileNamed(path: String) = Option(FileUtils.locateFile(path.trim()))

  def readFile(path: String): H2ODataset = {
    fileNamed(path) map read getOrElse (throw DataException(s"$path not found"))
  }

  def read(file: File): H2ODataset = {
    if (file == null) throw DataException("file not found")
    try {
      FileUtils.checkFile(file, file.getCanonicalPath)
      val nfs: NFSFileVec = NFSFileVec.make(file)
      val parsed: Frame = ParseDataset.parse(Key.make(), nfs._key)
      new H2ODataset(trackFrame(parsed))
    } catch {
      case ioe: IOException => 
        throw DataException("Could not read " + file, Some(ioe));
    }
  }

  def onVecs(map: Map[String, Vec]): H2ODataset = 
    new H2ODataset(new Frame(map.keySet.toArray, map.values.toArray))
  
  def onVecs(kvs: (String, Vec)*): H2ODataset = onVecs(kvs.toMap)
}