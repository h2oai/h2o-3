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

import examples.Frequencies.Data
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
  ClassLoader.getSystemClassLoader.setDefaultAssertionStatus(true)
  
  val numFeatures = 1024

  val minDocFreq: Int = 4

  val freqModel = new Frequencies(numFeatures, minDocFreq)

  val DATAFILE = "smsData.txt"
  val TEST_MSGS = Seq(
    "Michal, beer tonight in MV?",
    "penis enlargement, our exclusive offer of penis enlargement, enlarge one, enlarge one free",
    "We tried to contact you re your reply to our offer of a Video Handset? 750 anytime any networks mins? UNLIMITED TEXT?"
  )

  def main(args: Array[String]) {
    TestUtil.stall_till_cloudsize(1)

    try {
      val (hs: List[String], msgs: List[String]) = readSamples
      val spamModel = new SpamModel(hs, msgs)

      TEST_MSGS.foreach(msg => {
        val whatitis = ("HAM"::"SPAM"::Nil)(spamModel.spamness(msg))
        println(s"$msg is $whatitis")
      })
    } finally {
      // Shutdown H2O
      //      h2oContext.stop(stopSparkContext = true)
    }
  }

  def readSamples: (List[String], List[String]) = {
    val lines = readSamples("smalldata/" + DATAFILE)
    val size = lines.size
    val hs = lines map (_ (0))
    val msgs = lines map (_ (1))
    (hs, msgs)
  }

  def buildTable(id: String, trainingRows: List[CatSMS]): Frame = {
    val fr = new Frame(trainingRows.head.names, catVecs(trainingRows))
    new water.fvec.H2OFrame(fr)
  }

  def readSamples(dataFile: String): List[Array[String]] = {
    val lines: Iterator[String] = Source.fromFile(dataFile, "ISO-8859-1").getLines()
    val pairs: Iterator[Array[String]] = lines.map(_.split("\t", 2))
    val goodOnes: Iterator[Array[String]] = pairs.filter(!_ (0).isEmpty)
    goodOnes.toList
  }

  val IgnoreWords = Set("the", "not", "for")
  val IgnoreChars = "[,:;/<>\".()?\\-\\\'!01 ]"

  def tokenize(s: String) = {
    var smsText = s.toLowerCase.replaceAll(IgnoreChars, " ").replaceAll("  +", " ").trim
    val words = smsText split " " filter (w => !IgnoreWords(w) && w.length > 2)

    words.toSeq
  }

  case class SpamModel(hs: List[String], msgs: List[String]) {

    lazy val tf: List[Data] = msgs map freqModel.weigh

    // Build term frequency-inverse document frequency
    lazy val idf: freqModel.IDF = (new freqModel.IDF() /: tf) (_ + _)

    lazy val weights: List[Array[Double]] = tf map idf.normalize

    lazy val trainingRows = hs zip weights map TrainingRow.tupled
    
    lazy val categorizedSMSs = trainingRows map (new CatSMS(_))
    
    lazy val cutoff = (categorizedSMSs.length * 0.8).toInt
    // Split table
    lazy val (before, after) = categorizedSMSs.splitAt(cutoff)
    lazy val train = buildTable("train", before)
    lazy val valid = buildTable("valid", after)

    lazy val dlModel = buildDLModel(train, valid)

    /** Spam detector */
    def spamness(msg: String) = {
      val weights = freqModel.weigh(msg)
      val normalizedWeights = idf.normalize(weights)
      val sampleFrame = VectorOfDoubles(normalizedWeights).frame
      val prediction = dlModel.scoreSample(sampleFrame)
      val estimates = prediction.vecs() map (_.at(0)) toList
      val estimate = estimates(0).toInt
      estimate
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
    dlParams._ignore_const_cols = false // TODO(vlad): figure out how important is it

    val jobKey: Key[DeepLearningModel] = water.Key.make("dlModel.hex")
    val dl = new DeepLearning(dlParams, jobKey)
//    val tmi = dl.trainModelImpl()
    val tm = dl.trainModel()
    tm.waitTillFinish()
    tm._result.get()

  }

  def arrayFrom(map: Map[Int, Double], size: Int): Array[Double] = {
    0 until size map (i => map.getOrElse(i, 0.0)) toArray
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

  val CatDomain = "ham" :: "spam" :: Nil toArray

  case class CatSMS(sms: TrainingRow) {
    def target: Int = CatDomain indexOf sms.target

    def name(i: Int) = "fv" + i

    def names: Array[String] = ("target" :: (sms.fv.indices map name).toList) toArray

    def xx = "1"
  }

  def catVecs(rows: Iterable[CatSMS]): Array[Vec] = {
    val row0 = rows.head
    val targetVec = vec(CatDomain, rows map (_.target))
    val vecs = row0.sms.fv.indices.map(
      i => dvec(rows map (_.sms.fv(i))))

    (targetVec :: vecs.toList) toArray
  }

  /** Training message representation. */
  case class TrainingRow(target: String, fv: Data) {
    def name(i: Int) = "fv" + i

    def names: Array[String] = ("target" :: (fv.indices map name).toList) toArray
  }

  case class VectorOfDoubles(fv: Data) {
    def name(i: Int) = "fv" + i

    def names: Array[String] = fv.indices map name toArray

    def vecs: Array[Vec] = fv map (x => dvec(x :: Nil))

    def frame: Frame = new Frame(names, vecs)
  }

}


