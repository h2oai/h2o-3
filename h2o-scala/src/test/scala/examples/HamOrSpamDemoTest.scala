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
import hex.deeplearning._
import org.junit.Assert._
import org.junit.{AfterClass, BeforeClass, Test}
import water.fvec.{AppendableVec, Frame, NewChunk, Vec}
import water.{Futures, H2O, Key, TestUtil}

import scala.io.Source
import scala.language.postfixOps

/**
  * Demo for NYC meetup and MLConf 2015.
  *
  * It predicts spam text messages.
  * Training dataset is available in the file smalldata/smsData.txt.
  */
class HamOrSpamDemoTest extends TestUtil {
  
  val numFeatures = 1024

  val minDocFreq: Int = 4

  val frequenciesCollector = new Frequencies(numFeatures, minDocFreq)

  val DATAFILE = "smsData.txt"
  val HAM = Seq(
    "Michal, beer tonight in MV?"
  )
  val SPAM = Seq(
    "penis enlargement, our exclusive offer of penis enlargement, enlarge one, enlarge one free",
    "We tried to contact you re penis enlargement, our exclusive offer of penis enlargement, enlarge one, enlarge one free",
    "We tried to contact you re UNLIMITED penis enlargement, our exclusive offer of penis enlargement, enlarge one, enlarge one free",
    "We tried to contact you re your reply to our offer of penis enlargement, our exclusive offer of penis enlargement",
    "We tried to contact you re your reply to our offer of penis enlargement, our exclusive offer of penis enlargement, enlarge one, enlarge one free",
    "We tried to contact you re your reply to our offer of a Video Handset? 750 anytime any networks mins? UNLIMITED TEXT?"
  )

  def createModel: SpamModel = {
    val (hs: List[String], msgs: List[String]) = readSamples
    new SpamModel(hs, msgs)
  }

  @Test def testOnSamples(): Unit = {
    val spamModel = createModel

    HAM foreach { m =>
      print(s"$m...")
      val isSpam = spamModel.isSpam(m)
      assertFalse(s"Ham $m failed", isSpam)
      println(s" -> $isSpam")
    }
    println("Now try spam")
    SPAM foreach { m =>
      print(s"$m...")
      val isSpam = spamModel.isSpam(m)
      assertTrue(s"Spam $m failed", isSpam) 
      println(s" -> $isSpam")
    }
  }

  def readSamples: (List[String], List[String]) = {
    val lines = readSamples("smalldata/" + DATAFILE)
    val size = lines.size
    val hs = lines map (_ (0))
    val msgs = lines map (_ (1))
    (hs, msgs)
  }

  def buildTable(id: String, trainingRows: List[CategorizedTexts]): Frame = {
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

    lazy val tf: List[Data] = msgs map frequenciesCollector.eval

    // Build term frequency-inverse document frequency
    lazy val idf: frequenciesCollector.IDF = (new frequenciesCollector.IDF() /: tf) (_ + _)

    lazy val weights: List[Array[Double]] = tf map idf.normalize
    
    lazy val categorizedTexts = hs zip weights map CategorizedTexts.tupled
    
    lazy val cutoff = (categorizedTexts.length * 0.8).toInt
    // Split table
    lazy val (before, after) = categorizedTexts.splitAt(cutoff)
    lazy val train = buildTable("train", before)
    val dlModel = buildDLModel(train, catData("train", before), catData("valid", after))
    
    /** Spam detector */
    def isSpam(msg: String) = {
      val weights = frequenciesCollector.eval(msg)
      val normalizedWeights = idf.normalize(weights)
      val spamness = dlModel.scoreSample(normalizedWeights)
      spamness == 1
    }
  }

  def buildDLModel(train: Frame,
                   trainData: DlInput, 
                   testData: DlInput): DLModel = {
    
    val dlParams = new DeepLearningParameters()
    dlParams._train = train._key
    dlParams.trainData = trainData
    dlParams.testData = testData
    dlParams._response_column = "target"
    dlParams._epochs = 10
    dlParams._l1 = 0.001
    dlParams._hidden = Array[Int](200, 200)
    dlParams._ignore_const_cols = false // TODO(vlad): figure out how important is it
    val jobKey: Key[DLModel] = water.Key.make("ignoreme")
    val mb = new SmallDeepLearningModelBuilder(dlParams, jobKey)
    mb.trainModel()
    mb.get()
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

  val CatDomain = "ham" :: "spam" :: Nil toArray

  import scala.collection.JavaConverters._

  case class CategorizedTexts(targetText: String, weights: Array[Double]) {

    def target: Int = CatDomain indexOf targetText

    def name(i: Int) = "fv" + i

    lazy val names: Array[String] = ("target" :: (weights.indices map name).toList) toArray
  }

  def catData(name: String, rows: List[CategorizedTexts]): DlInput = {
    val row0 = rows.head
    val target:java.util.List[Integer] = (rows map (_.target) map Integer.valueOf) asJava
    
    val columns:List[List[Double]] = row0.weights.indices.map(i => rows.map(_.weights(i)):List[Double]).toList

    val javaColumns: java.util.List[java.util.List[java.lang.Double]] = columns.map(
      column => column.map(java.lang.Double.valueOf).asJava) asJava

    new DlInput(name, target, javaColumns)
  }
  
  def catVecs(rows: Iterable[CategorizedTexts]): Array[Vec] = {
    val row0 = rows.head
    val targetVec = vec(CatDomain, rows map (_.target))
    val vecs = row0.weights.indices.map(
      i => dvec(rows map (_.weights(i))))

    (targetVec :: vecs.toList) toArray
  }

}

object HamOrSpamDemoTest extends HamOrSpamDemoTest {
  @BeforeClass def setup() = TestUtil.stall_till_cloudsize(1)
  @AfterClass  def shutup() = H2O.exit(0)

  ClassLoader.getSystemClassLoader.setDefaultAssertionStatus(true)

  def main(args: Array[String]) {
    TestUtil.stall_till_cloudsize(1)

    try {
      val spamModel = createModel
//      H2O.exit(0)
      spamModel.isSpam("")
      
      HAM foreach { m =>
        print(s"$m...")
        val isSpam = spamModel.isSpam(m)
        val ok = !isSpam
        //        assert(!isSpam, s"Ham $m failed")
        println(s" -> $ok")
      }
      println("Now try spam")
      SPAM foreach { m =>
        println(s"$m...")
        val isSpam = spamModel.isSpam(m)
        val ok = isSpam
        //        assert(isSpam, s"Spam $m failed") 
        println(s" -> $ok")
      }
    } finally {
      H2O.exit(0)
    }
  }

}
