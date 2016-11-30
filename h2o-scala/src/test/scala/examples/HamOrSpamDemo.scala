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

import hex.deeplearning.{DeepLearning, DeepLearningModel}
import hex.deeplearning.DeepLearningModel.DeepLearningParameters
import water.fvec.{AppendableVec, Frame, NewChunk, Vec}
import water.{TestUtil, Futures, Key}

import scala.io.Source
import scala.language.postfixOps
import examples.Murmur._

/**
 * Demo for NYC meetup and MLConf 2015.
 *
 * It predicts spam text messages.
 * Training dataset is available in the file smalldata/smsData.txt.
 */
object HamOrSpamDemo extends TestUtil
//  extends SparkContextSupport 
//    with H2OFrameSupport 
{
  
  val numFeatures = 1024
  // type Vector = Array[Double] // does not work with Spark, catalyst reflection is not good enough
  
  val DATAFILE="smsData.txt"
  val TEST_MSGS = Seq(
    "Michal, beer tonight in MV?",
    "penis enlargement, our exclusive offer of penis enlargement, enlarge one, enlarge one free",
    "We tried to contact you re your reply to our offer of a Video Handset? 750 anytime any networks mins? UNLIMITED TEXT?"
  )

  def main(args: Array[String]) {
    TestUtil.stall_till_cloudsize(1)
//    val conf: SparkConf = configure("Sparkling Water Meetup: Ham or Spam (spam text messages detector)")
//    // Create SparkContext to execute application on Spark cluster
//    val sc = new SparkContext(conf)
//    val conf1: H2OConf = new H2OConf(sc)
//    val h2oContext = new H2OContext(sc, conf1)
//// Initialize H2O context
//      H2OContext.instantiatedContext.set(h2oContext)
//      h2oContext.init()
    
    try {
      // Data load
      val lines = readSamples("smalldata/" + DATAFILE)
      val size = lines.size
      val hs = lines map (_ (0))
      val msgs = lines map (_ (1))
      val spamModel = new SpamModel(msgs)

      val trainingRows = hs zip spamModel.weights map TrainingRow.tupled

      val categorizedSMSs = trainingRows map (new CatSMS(_))
      val cutoff = (categorizedSMSs.length * 0.8).toInt
      // Split table
      val (before, after) = categorizedSMSs.splitAt(cutoff)
      val train = buildTable("train", before)
      val valid = buildTable("valid", after)

      val dlModel = buildDLModel(train, valid)

//      // Collect model metrics
//      val trainMetrics = modelMetrics[ModelMetricsBinomial](dlModel, train)
//      val validMetrics = modelMetrics[ModelMetricsBinomial](dlModel, valid)
//      println(
//        """
//         |AUC on train data = ${trainMetrics.auc}
//         |AUC on valid data = ${validMetrics.auc}
//      """.stripMargin)

    val isSpam = spamModel.isSpam(dlModel)
    TEST_MSGS.foreach(msg => { 
      val whatitis = if (isSpam(msg)) "SPAM" else "HAM"
      println(s"$msg is $whatitis")
    })
    } finally {
      // Shutdown Spark cluster and H2O
//      h2oContext.stop(stopSparkContext = true)
    }
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
    val words =smsText split " " filter (w => !IgnoreWords(w) && w.length>2)

    words.toSeq
  }
  
  case class SpamModel(msgs: List[String]) {
    val minDocFreq:Int = 4

    lazy val tf: List[Array[Double]] = msgs map weigh
    
    // Build term frequency-inverse document frequency
    lazy val idf0:DocumentFrequencyAggregator = 
      (new DocumentFrequencyAggregator(numFeatures) /: tf)(_ + _)
    
    lazy val modelIdf: Array[Double] = idf0.idf(minDocFreq)

    private val normalize: (Array[Double]) => Array[Double] = idfNormalize(modelIdf)
    lazy val weights: List[Array[Double]] = tf map normalize
    
    def weigh(msg: String): Array[Double] = weighWords(tokenize(msg).toList)

    /** Spam detector */
    def isSpam(dlModel: DeepLearningModel) = (msg: String) => {
      val weights = weigh(msg)
      val normalizedWeights = normalize(weights)
      val sampleFrame = VectorOfDoubles(normalizedWeights).frame
      val prediction = dlModel.score(sampleFrame)
      val estimates = prediction.vecs() map (_.at(0)) toList
      val estimate: Double = estimates(1)
      println(s"$msg -> $estimate // $estimates")
      estimate < .5
    }

  }

  /**
    * Transforms a term frequency (TF) vector to a TF-IDF vector with a IDF vector
    *
    * @param idf an IDF vector
    * @param values a term frequency vector
    * @return a TF-IDF vector
    */
  def idfNormalize(idf: Array[Double])(values: Array[Double]): Array[Double] = {
    values zip idf map {case(x,y) => x*y}
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
    
    // Create a job
    val dl = new DeepLearning(dlParams, water.Key.make("dlModel.hex"))
    dl.trainModel.get
  }
  
  def hash(s: String) = murmurMod(numFeatures)(s)
  
  def arrayFrom(map: Map[Int, Double], size: Int): Array[Double] = {
    0 until size map (i => map.getOrElse(i, 0.0)) toArray
  }

  def weighWords(document: Iterable[String]): Array[Double] = {
    val hashes = document map hash

    val termFrequencies = scala.collection.mutable.Map.empty[Int, Double]

    hashes.foreach { i =>
      val count = termFrequencies.getOrElse(i, 0.0) + 1.0
      termFrequencies.put(i, count)
    }

    arrayFrom(termFrequencies.toMap, numFeatures)
  }


  /** A numeric Vec from an array of doubles */
  def dvec(values: Iterable[Double]): Vec = {
    val k: Key[Vec] = Vec.VectorGroup.VG_LEN1.addVec()
    val avec: AppendableVec = new AppendableVec(k, Vec.T_NUM)
    val chunk: NewChunk = new NewChunk(avec, 0)
    for (r <- values) chunk.addNum(r)
//    assert(chunk == avec.chunkForChunkIdx(0))
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

  case class CatSMS(target: Int, fv: Array[Double]) {
    def this(sms: TrainingRow) = this(CatDomain indexOf sms.target, sms.fv)
    def name(i: Int) = "fv" + i
    def names: Array[String] = ("target" :: (fv.indices map name).toList) toArray
    def xx = "1"
  }

  def catVecs(rows:Iterable[CatSMS]): Array[Vec] = {
    val row0 = rows.head
    val targetVec = vec(CatDomain, rows map (_.target))
    val vecs = row0.fv.indices.map(
      i => dvec(rows map (_.fv(i))))
    
    (targetVec :: vecs.toList) toArray
  }

  /** Training message representation. */
  case class TrainingRow(target: String, fv: Array[Double]) {
    def name(i: Int) = "fv" + i
    def names: Array[String] = ("target" :: (fv.indices map name).toList) toArray
  }

  case class VectorOfDoubles(fv: Array[Double]) {
    def name(i: Int) = "fv" + i

    def names: Array[String] = fv.indices map name toArray
    
    def vecs: Array[Vec] = fv map (x => dvec(x::Nil))
    
    def frame: Frame = new Frame(names, vecs)
  }
}


