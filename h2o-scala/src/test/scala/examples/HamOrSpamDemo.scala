/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package examples

import hex.deeplearning.DeepLearningModel.DeepLearningParameters
import hex.deeplearning.{DeepLearning, DeepLearningModel}
import water.fvec.{AppendableVec, Frame, NewChunk, Vec}
import water.{Futures, Key, TestUtil}

import scala.io.Source
import scala.language.postfixOps

/**
  * Demo for NYC meetup and MLConf 2015.
  *
  * It predicts spam text messages.
  * Training dataset is available in the file smalldata/smsData.txt.
  */
object HamOrSpamDemo extends TestUtil {

  val numFeatures = 1024
  val minDocFreq:Int = 4

  val DATAFILE = "smalldata/smsData.txt"
  val TEST_MSGS = Seq(
    "Michal, beer tonight in MV?",
    "penis enlargement, our exclusive offer of penis enlargement, enlarge one, enlarge one free",
    "We tried to contact you re your reply to our offer of a Video Handset? 750 anytime any networks mins? UNLIMITED TEXT?"
  )

  def main(args: Array[String]) {
    TestUtil.stall_till_cloudsize(1)

    try {
      val (hs: List[String], msgs: List[String]) = parseSamples(DATAFILE)
      val spamModel = new SpamModel(msgs)
      val codes: List[Int] = hs map (CatDomain indexOf _)
      val categorizedSMSs = codes zip spamModel.weights map CatSMS.tupled

      val cutoff = (categorizedSMSs.length * 0.8).toInt
      // Split table
      val (before, after) = categorizedSMSs.splitAt(cutoff)
      val train = buildTable("train", before)
      val valid = buildTable("valid", after)

      val dlModel = buildDLModel(train, valid)

      val isSpam = spamModel.isSpam(dlModel)
      TEST_MSGS.foreach(msg => {
        val whatitis = if (isSpam(msg)) "SPAM" else "HAM"
        println(s"$msg is $whatitis")
      })
    } finally {
      // Shutdown H2O
      //      h2oContext.stop(stopSparkContext = true)
    }
  }

  def parseSamples(path: String): (List[String], List[String]) = {
    val lines = readFile(path)
    val size = lines.size
    val hs = lines map (_ (0))
    val msgs = lines map (_ (1))
    (hs, msgs)
  }

  def buildTable(id: String, rows: List[CatSMS]): Frame = {
    val fr = catVecs(rows)
    new water.fvec.H2OFrame(fr)
  }

  def readFile(dataFile: String): List[Array[String]] = {
    val lines: Iterator[String] = Source.fromFile(dataFile, "ISO-8859-1").getLines()
    val pairs: Iterator[Array[String]] = lines.map(_.split("\t", 2))
    val goodOnes: Iterator[Array[String]] = pairs.filter(!_ (0).isEmpty)
    goodOnes.toList
  }

  val freq = new FrequencyModel(numFeatures, minDocFreq)

  case class SpamModel(msgs: List[String]) {

    lazy val tf: List[FrequencyModel.Data] = msgs map freq.weigh

    // Build term frequency-inverse document frequency
    lazy val idf:freq.IDF = (new freq.IDF() /: tf)(_ + _)

    lazy val weights: List[FrequencyModel.Data] = tf map idf.normalize

    /** Spam detector */
    def isSpam(dlModel: DeepLearningModel) = (msg: String) => {
      val weights = freq.weigh(msg)
      val normalizedWeights = idf.normalize(weights)
      val sampleFrame = VectorOfDoubles(normalizedWeights).frame
      val prediction = dlModel.score(sampleFrame)
      val estimates = prediction.vecs() map (_.at(0)) toList
      val estimate: Double = estimates(1)
      println(s"$msg -> $estimate // $estimates")
      estimate < .5
    }

  }

  /** Builds DeepLearning model. */
  def buildDLModel(train: Frame, valid: Frame,
                   epochs: Int = 10, l1: Double = 0.001,
                   hidden: Array[Int] = Array[Int](200, 200)): DeepLearningModel = {
    val dlParams = new DeepLearningParameters()
    dlParams._train = train._key
    dlParams._valid = valid._key
    dlParams._response_column = "target"
    dlParams._epochs = epochs
    dlParams._l1 = l1
    dlParams._hidden = hidden
    //    dlParams._ignore_const_cols = false // TODO(vlad): figure out how important is it

    // Create a job
    val dl = new DeepLearning(dlParams, water.Key.make("dlModel.hex"))
    dl.trainModel.get
  }

  /** A numeric Vec from an array of doubles */
  def dvec(values: Iterable[Double]): Vec = {
    val k: Key[Vec] = Vec.VectorGroup.VG_LEN1.addVec()
    val avec: AppendableVec = new AppendableVec(k, Vec.T_NUM)
    val chunk: NewChunk = new NewChunk(avec, 0)
    for (r <- values) chunk.addNum(r)
    commit(avec, chunk)
  }

  def commit(avec: AppendableVec, chunk: NewChunk): Vec = {
    val fs: Futures = new Futures
    chunk.close(0, fs)
    val vec: Vec = avec.layout_and_close(fs)
    fs.blockForPending()
    vec
  }

  def vec(domain: Array[String], rows: Iterable[Int]): Vec = {
    val k: Key[Vec] = Vec.VectorGroup.VG_LEN1.addVec()
    val avec: AppendableVec = new AppendableVec(k, Vec.T_NUM)
    avec.setDomain(domain)
    val chunk: NewChunk = new NewChunk(avec, 0)
    for (r <- rows) chunk.addNum(r)
    commit(avec, chunk)
  }

  def cvec(domain: Array[String], rows: Iterable[String]): Vec = {
    val indexes: Iterable[Int] = rows map (domain.indexOf(_))
    vec(domain, indexes)
  }

  val CatDomain = "ham"::"spam"::Nil toArray

  case class CatSMS(target: Int, fv: FrequencyModel.Data) {
    def names: Array[String] = namesOfData(fv, "target")
  }

  def catVecs(rows:Iterable[CatSMS]): Frame = {
    //    val allKeys = (Set.empty[Int] /: rows) {case (s, row) => s ++ row.fv.keySet)}
    val row0 = rows.head
    val targetVec = vec(CatDomain, rows map (_.target))
    val data = rows map (_.fv)
    val nels = data filter (_.nonEmpty)
    val vertical = transposeData(data)

    val verticalVecs = vertical mapValues dvec

    val matrix = verticalVecs + ("target" -> targetVec)

    buildFrame(matrix)
  }

  def namesOfData(data: FrequencyModel.Data, prefixes: String*): Array[String] = {
    prefixes.toList ++ data.keys toArray
  }

  def transposeData(datas: Iterable[FrequencyModel.Data]): Map[String, Iterable[Double]] = {
    val keys: Set[String] = (Set.empty[String]/: datas)(_ ++ _.keySet)

    val init: Map[String, List[Double]] = keys map (k => k -> Nil) toMap

    (init /: datas) {
      case (map, row) => map.map {
        case (k, v) => k -> (row.getOrElse(k, 0.0) :: v)
      }
    }
  }

  case class VectorOfDoubles(fv: FrequencyModel.Data) {
    lazy val matrix = transposeData(fv::Nil)
    lazy val vecMatrix = matrix.mapValues(dvec)
    lazy val frame = buildFrame(vecMatrix)
  }

  def buildFrame(map: Map[String, Vec]) = {
    val kvs = map.toList
    new Frame(kvs.map(_._1).toArray, kvs.map(_._2).toArray)
  }
}


