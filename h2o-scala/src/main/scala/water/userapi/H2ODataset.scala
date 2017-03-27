package water.userapi

import java.io.{File, IOException}

import water.fvec.{Frame, NFSFileVec, Vec}
import water.parser.ParseDataset
import water.rapids.ast.prims.advmath.AstStratifiedSplit
import water.rapids.ast.prims.advmath.AstStratifiedSplit._
import water.udf.specialized.Enums
import water.util.{FileUtils, FrameUtils}
import water.{DKV, Iced, Key, Scope}

/**
  * Simplified Frame wrapper for simple usages in Scala world
  * Created by vpatryshev on 3/26/17.
  */
case class H2ODataset(private val frameKey: Key[Frame]) extends Iced[H2ODataset] {
  def this(frame: Frame) = this(FrameUtils.save(frame))

  def frame: Option[Frame] = Option(DKV.getGet(frameKey))
  
  def oneHotEncode(ignore: String*): H2ODataset = {
    try {
      val res = for {
        f <- frame
        hotFrame <- Option(Enums.oneHotEncoding(f, ignore.toArray))
      } yield new H2ODataset(hotFrame)
      
      res getOrElse {throw DataException("Failed to build oneHotEncoding")}
    } catch {
      case ioe: IOException =>
        throw DataException("Failed to do oneHotEncoding", Some(ioe))
    }
  }

  private def frameClone: Option[Frame] = {
    val newFrame = frame map (_.clone)
    newFrame foreach { _._key = null}
    newFrame foreach (Scope.track(_))
    newFrame
  }
  
  // the new vec is named (hard-coded so far) "test_train_split"
  def addSplittingColumn(colName: String, ratio: Double, seed: Long): Option[H2ODataset] = {
      for {
        f <- frame
        vec <- Option(f.vec(colName))
        splitter = Scope.track(AstStratifiedSplit.split(vec, ratio, seed, SplittingDom))
        newFrame <- frameClone
        _ = newFrame.add(splitter.names(), splitter.vecs)
      } yield new H2ODataset(newFrame)
    }
  }

object H2ODataset {
  
  def readFile(path: String): H2ODataset =
    read(FileUtils.locateFile(path.trim()))

  def read(file: File): H2ODataset = {
    try {
      FileUtils.checkFile(file, file.getCanonicalPath)
      val nfs: NFSFileVec = NFSFileVec.make(file)
      new H2ODataset(Scope.track(ParseDataset.parse(Key.make(), nfs._key)))
    } catch {
      case ioe: IOException => throw DataException("Could not read " + file, Some(ioe));
    }
  }

  def onVecs(map: Map[String, Vec]): H2ODataset = 
    new H2ODataset(new Frame(map.keySet.toArray, map.values.toArray))
}