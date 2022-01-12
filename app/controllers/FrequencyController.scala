package controllers

import ai.lum.common.ConfigFactory
import ai.lum.common.ConfigUtils._
import ai.lum.odinson.Mention
import com.typesafe.config.Config
import ai.lum.odinson.rest.{ FrequencyTable, TermsAndFreqs }
import ai.lum.odinson.rest.utils._
import ai.lum.odinson.rest.json._
import ai.lum.odinson.rest.requests._
import org.apache.lucene.util.automaton.{ CompiledAutomaton, RegExp }
import play.api.Configuration
import play.api.cache._
import play.api.libs.json._
import play.api.mvc._

import javax.inject._
import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.math._

@Singleton
class FrequencyController @Inject() (
  config: Config = ConfigFactory.load(),
  playConfig: Configuration,
  cache: AsyncCacheApi,
  cc: ControllerComponents
)(
  implicit ec: ExecutionContext
) extends AbstractController(cc) {

  import ExceptionUtils._
  import ExtractorEngineUtils._

  val vocabularyExpiry     = playConfig.get[Duration]("play.cache.vocabularyExpiry")

  val posTagTokenField     = config.apply[String]("odinson.index.posTagTokenField")

  /** Convenience method to determine if a string matches a given regular expression.
  * @param s The String to be searched.
  * @param regex The regular expression against which `s` should be compared.
  * @return True if the whole expression matches.
  */
  private def isMatch(s: String, regex: Option[String]): Boolean = s.matches(regex.getOrElse(".*"))

  /** For a given term field, find the terms ranked min to max (inclusive, 0-indexed)
  * @param field The field to count (e.g., raw, token, lemma, tag, etc.)
  * @param group Optional second field to condition the field counts on.
  * @param filter Optional regular expression filter for terms within `field`
  * @param order "freq" for greatest-to least frequency (default), "alpha" for alphanumeric order
  * @param min Highest rank to return (0 is highest possible value).
  * @param max Lowest rank to return (e.g., 9).
  * @param scale "count" for raw frequencies (default), "log10" for log-transform, "percent" for percent of total.
  * @param reverse Whether to reverse the order before slicing between `min` and `max` (default false).
  * @param pretty Whether to pretty-print the JSON results.
  * @return JSON frequency table as an array of objects.
  */
  def termFreq(
    field: String,
    group: Option[String],
    filter: Option[String],
    order: Option[String],
    min: Option[Int],
    max: Option[Int],
    scale: Option[String],
    reverse: Option[Boolean],
    pretty: Option[Boolean]
  ) = Action.async {
    Future {
      try {
        // cutoff the results to the requested ranks
        val defaultMin = 0
        val defaultMax = 9

        val minIdx = min.getOrElse(defaultMin)
        val maxIdx = max.getOrElse(defaultMax)

        usingNewEngine(config) { extractorEngine =>
          // ensure that the requested field exists in the index
          val fields = extractorEngine.index.listFields()
          val fieldNames = fields.iterator.asScala.toList
          // if the field exists, find the frequencies of each term
          if (fieldNames contains field) {
            // find the frequency of all terms in this field
            val terms = fields.terms(field)
            val termsEnum = filter match {

              // get filtered terms only
              case Some(filterString) =>
                // check for proper regex
                val valid = {
                  filterString.r // throws exception if invalid Scala regex (superset of Lucene regex)
                  true
                }

                if (valid) {
                  // NB: This is Lucene regex, *not* Perl-compatible
                  // see https://www.elastic.co/guide/en/elasticsearch/reference/5.6/query-dsl-regexp-query.html#regexp-syntax
                  // TODO: unify regex type with ruleFreq filter
                  val automaton = new RegExp(filter.get).toAutomaton
                  new CompiledAutomaton(automaton).getTermsEnum(terms)
                } else terms.iterator

              case _ =>
                // just go through all terms
                terms.iterator

            }

            val firstLevel = order match {
              // alphanumeric order as defined by Lucene's term order
              case Some("alpha") =>
                reverse match {
                  // we must cycle through the whole set of terms and just keep the tail
                  case Some(true) =>
                    // we don't know where the end is in advance, so we queue each freq as we go
                    val termFreqs = new scala.collection.mutable.Queue[(String, Long)]()

                    TermsAndFreqs(termsEnum).foreach { termAndFreq =>
                      termFreqs.enqueue((termAndFreq.term, termAndFreq.freq))
                      // if we exceed the size we need, just throw the oldest away
                      if (termFreqs.size > maxIdx) termFreqs.dequeue()
                    }
                    termFreqs
                      .toIndexedSeq
                      .reverse
                      .slice(minIdx, maxIdx + 1)

                  // just take the first (max + 1) items, since they are stored in that order
                  case _ =>
                    val termFreqs = new FrequencyTable(
                      minIdx,
                      maxIdx,
                      reverse.getOrElse(false)
                    )

                    TermsAndFreqs(termsEnum).take(maxIdx + 1).foreach { termAndFreq =>
                      termFreqs.update(termAndFreq.term, termAndFreq.freq)
                    }
                    termFreqs.get
                }
              // if not alphanumeric (hence frequency), we go through all terms and compare frequencies
              case _ =>
                val termFreqs = new FrequencyTable(
                  minIdx,
                  maxIdx,
                  reverse.getOrElse(false)
                )

                TermsAndFreqs(termsEnum).foreach { termAndFreq =>
                  termFreqs.update(termAndFreq.term, termAndFreq.freq)
                }
                termFreqs.get
            }

            // count instances of each pairing of `field` and `group` terms
            val groupedTerms =
              if (group.nonEmpty && fieldNames.contains(group.get)) {
                // this is O(awful) but will only be costly when (max - min) is large
                val pairs = for {
                  (term1, _) <- firstLevel
                  odinsonQuery =
                    extractorEngine.compiler.mkQuery(s"""(?<term> [$field="$term1"])""")
                  results = extractorEngine.query(odinsonQuery)
                  scoreDoc <- results.scoreDocs
                  eachMatch <- scoreDoc.matches
                  // FIXME: getOrElse
                  term2 = extractorEngine.dataGatherer.getTokensForSpan(
                    scoreDoc.doc,
                    eachMatch,
                    group.get
                  ).head
                } yield (term1, term2)
                // count instances of this pair of terms from `field` and `group`, respectively
                pairs.groupBy(identity).mapValues(_.size.toLong).toIndexedSeq
              } else firstLevel

            // order again if there's a secondary grouping variable
            val reordered = groupedTerms.sortBy { case (ser, conditionedFreq) =>
              ser match {
                case singleTerm: String =>
                  (firstLevel.indexOf((singleTerm, conditionedFreq)), -conditionedFreq)
                case (term1: String, _: String) =>
                  // find the total frequency of the term (ignoring group condition)
                  val totalFreq = firstLevel.find(_._1 == term1).get._2
                  (firstLevel.indexOf((term1, totalFreq)), -conditionedFreq)
              }
            }

            // transform the frequencies as requested, preserving order
            val scaled = scale match {
              case Some("log10") => reordered map { case (term, freq) => (term, log10(freq)) }
              case Some("percent") =>
                val countTotal = terms.getSumTotalTermFreq
                reordered map { case (term, freq) => (term, freq.toDouble / countTotal) }
              case _ => reordered.map { case (term, freq) => (term, freq.toDouble) }
            }

            // rearrange data into a Seq of Maps for Jsonization
            val jsonObjs = scaled.map { case (termGroup, freq) =>
              termGroup match {
                case singleTerm: String =>
                  Json.obj("term" -> singleTerm.asInstanceOf[String], "frequency" -> freq)
                case (term1: String, term2: String) =>
                  Json.obj("term" -> term1, "group" -> term2, "frequency" -> freq)
              }
            }
            Json.toJson(jsonObjs).format(pretty)
          } else {
            // the requested field isn't in this index
            Json.obj().format(pretty)
          }
        }
      } catch handleNonFatal
    }
  }

    /** Count how many times each rule matches from the active grammar on the active dataset.
    * @param grammar An Odinson grammar.
    * @param allowTriggerOverlaps Whether or not event arguments are permitted to overlap with the event's trigger.
    * @param filter Optional regular expression filter for the rule name.
    * @param order "freq" for greatest-to least frequency (default), "alpha" for alphanumeric order.
    * @param min Highest rank to return (0 is highest possible value).
    * @param max Lowest rank to return (e.g., 9).
    * @param scale "count" for raw frequencies (default), "log10" for log-transform, "percent" for percent of total.
    * @param reverse Whether to reverse the order before slicing between `min` and `max` (default false).
    * @param pretty Whether to pretty-print the JSON results.
    * @return JSON frequency table as an array of objects.
    */
  def ruleFreq() = Action { request =>
    usingNewEngine(config) { extractorEngine =>
      val ruleFreqRequest = request.body.asJson.get.as[RuleFreqRequest]
      //println(s"GrammarRequest: ${gr}")
      val grammar = ruleFreqRequest.grammar
      val allowTriggerOverlaps = ruleFreqRequest.allowTriggerOverlaps.getOrElse(false)
      // TODO: Allow grouping factor: "ruleType" (basic or event), "accuracy" (wrong or right), others?
      // val group = gr.group
      val filter = ruleFreqRequest.filter
      val order = ruleFreqRequest.order
      val min = ruleFreqRequest.min
      val max = ruleFreqRequest.max
      val scale = ruleFreqRequest.scale
      val reverse = ruleFreqRequest.reverse
      val pretty = ruleFreqRequest.pretty
      try {
        // rules -> OdinsonQuery
        val extractors = extractorEngine.ruleReader.compileRuleString(grammar)

        val mentions: Seq[Mention] = {
          val iterator = extractorEngine.extractMentions(
            extractors,
            numSentences = extractorEngine.numDocs(),
            allowTriggerOverlaps = allowTriggerOverlaps,
            disableMatchSelector = false
          )
          iterator.toVector
        }

        val ruleFreqs = mentions
          // rule name is all that matters
          .map(_.foundBy)
          // collect the instances of each rule's results
          .groupBy(identity)
          // filter the rules by name, if a filter was passed
          // NB: this is Scala style anchored regex, *not* Lucene's RegExp
          // TODO: unify regex style with that of termFreq's filter
          .filter { case (ruleName, ms@_) => isMatch(ruleName, filter) }
          // count how many matches for each rule
          .map { case (k, v) => k -> v.length }
          .toSeq

        // order the resulting frequencies as requested
        val ordered = order match {
          // alphabetical
          case Some("alpha") => ruleFreqs.sortBy { case (ruleName, _) => ruleName }
          // frequency (default)
          case _ => ruleFreqs.sortBy { case (ruleName, freq) => (-freq, ruleName) }
        }

        // reverse if necessary
        val reversed = reverse match {
          case Some(true) => ordered.reverse
          case _          => ordered
        }

        // Count instances of every rule
        val countTotal = reversed.map(_._2).sum

        // cutoff the results to the requested ranks
        val defaultMin = 0
        val defaultMax = 9
        val sliced =
          reversed.slice(min.getOrElse(defaultMin), max.getOrElse(defaultMax) + 1).toIndexedSeq

        // transform the frequencies as requested, preserving order
        val scaled = scale match {
          case Some("log10") => sliced map { case (rule, freq) => (rule, log10(freq)) }
          case Some("percent") =>
            sliced map { case (rule, freq) => (rule, freq.toDouble / countTotal) }
          case _ => sliced.map { case (rule, freq) => (rule, freq.toDouble) }
        }

        // rearrange data into a Seq of Maps for Jsonization
        val jsonObjs = scaled.map { case (ruleName, freq) =>
          Json.obj("term" -> ruleName, "frequency" -> freq)
        }

        Json.arr(jsonObjs).format(pretty)
      } catch handleNonFatal
    }
  }

  /** Return `nBins` quantile boundaries for `data`. Each bin will have equal probability.
    * @param data The data to be binned.
    * @param nBins The number of quantiles (e.g. 4 for quartiles).
    * @param isContinuous True if the data is continuous (if it has been log10ed, for example)
    * @return A sequence of quantile boundaries which should be inclusive of all data.
    */
  def quantiles(data: Array[Double], nBins: Int, isContinuous: Option[Boolean]): Seq[Double] = {
    val sortedData = data.sorted
    // quantile boundaries expressed as percentiles
    val percentiles = (0 to nBins) map (_.toDouble / nBins)

    val bounds = percentiles.foldLeft(List.empty[Double])((res, percentile) => {
      // approximate index of this percentile
      val i = percentile * (sortedData.length - 1)
      // interpolate between the two values of `data` that `i` falls between
      val lowerBound = floor(i).toInt
      val upperBound = ceil(i).toInt
      val fractionalPart = i - lowerBound
      val interpolation = sortedData(lowerBound) * (1 - fractionalPart) +
        sortedData(upperBound) * fractionalPart
      // if data is count data, the boundaries should be on whole numbers
      val toAdd = isContinuous match {
        case Some(true) => interpolation
        case _          => round(interpolation).toDouble
      }
      // ensure that boundaries have a reasonable width to mitigate floating point errors
      // ensure no width-zero bins
      if (toAdd - res.headOption.getOrElse(-1.0) > 1e-12) {
        toAdd :: res
      } else {
        res
      }
    })

    bounds.reverse
  }

  /** Count the instances of @data that fall within each consecutive pair of bounds (lower-bound inclusive).
    * @param data The count/frequency data to be analyzed.
    * @param bounds The boundaries that define the bins used for histogram summaries.
    * @return The counts of `data` that fall into each bin.
    */
  def histify(data: List[Double], bounds: List[Double]): List[Long] = {
    @tailrec
    def iter(data: List[Double], bounds: List[Double], result: List[Long]): List[Long] =
      bounds match {
        // empty list can't be counted
        case Nil => Nil
        // the last item in the list -- all remaining data fall into the last bin
        case head@_ :: Nil => data.size :: result
        // shave off the unallocated datapoints that fall under this boundary cutoff and count them
        case head :: tail =>
          val (leftward, rightward) = data.partition(_ < head)
          iter(rightward, tail, leftward.size :: result)
      }

    iter(data, bounds, List.empty[Long]).reverse
  }

  // helper function
  private def processCounts(
    frequencies: List[Double],
    bins: Option[Int],
    equalProbability: Option[Boolean],
    xLogScale: Option[Boolean]
  ): Seq[JsObject] = {
    // log10-transform the counts
    val scaledFreqs = xLogScale match {
      case Some(true) => frequencies.map(log10)
      case _          => frequencies
    }

    val nBins: Int =
      if (bins.getOrElse(-1) > 0) {
        // user-provided bin count
        bins.get
      } else if (equalProbability.getOrElse(false)) {
        // more bins for equal probability graph
        ceil(2.0 * pow(scaledFreqs.length, 0.4)).toInt
      } else {
        // Rice rule
        ceil(2.0 * cbrt(scaledFreqs.length)).toInt
      }

    val (max, min) = (scaledFreqs.max, scaledFreqs.min)

    // the boundaries of every bin (of length nBins + 1)
    val allBounds = equalProbability match {
      case Some(true) =>
        // use quantiles to equalize the probability of each bin
        quantiles(scaledFreqs.toArray, nBins, isContinuous = xLogScale)

      case _ =>
        // use an invariant bin width
        val rawBinWidth = (max - min) / nBins.toDouble
        val binWidth = if (xLogScale.getOrElse(false)) rawBinWidth else round(rawBinWidth)
        (0 until nBins).foldLeft(List(min.toDouble))((res, _) =>
          (binWidth + res.head) :: res
        ).reverse
    }

    // right-inclusive bounds (for counting bins)
    val rightBounds = allBounds.tail.toList // map (_ + epsilon)

    // number of each count falling into this bin
    val binCounts = histify(scaledFreqs, rightBounds)
    val totalCount = binCounts.sum.toDouble

    // unify allBounds and binCounts to generate one JSON object per bin
    for (i <- allBounds.init.indices) yield {
      val width = allBounds(i + 1) - allBounds(i)
      val x = allBounds(i)
      val y = equalProbability match {
        case Some(true) =>
          // bar AREA (not height) should be proportional to the count for this bin
          // thus it is density rather than probability or count
          if (totalCount > 0 & width > 0) binCounts(i) / totalCount / width else 0.0
        case _ =>
          // report the actual count (can be scaled by UI)
          binCounts(i).toDouble
      }
      Json.obj(
        "w" -> width,
        "x" -> x,
        "y" -> y
      )
    }
  }

  /** Return coordinates defining a histogram of counts/frequencies for a given field.
    * @param field The field to analyze, e.g. lemma.
    * @param bins The number of bins to use for data partitioning (optional).
    * @param equalProbability Use variable-width bins to equalize the probability of each bin (optional).
    * @param xLogScale `log10`-transform the counts of each term (optional).
    * @param pretty Whether to pretty-print the JSON returned by the function.
    * @return A JSON array of each bin, defined by width, lower bound (inclusive), and frequency.
    */
  def termHist(
    field: String,
    bins: Option[Int],
    equalProbability: Option[Boolean],
    xLogScale: Option[Boolean],
    pretty: Option[Boolean]
  ) = Action.async {
    Future {
      usingNewEngine(config) { extractorEngine =>
        // ensure that the requested field exists in the index
        val fields = extractorEngine.index.listFields()

        val fieldNames = fields.iterator.asScala.toList
        // if the field exists, find the frequencies of each term
        if (fieldNames contains field) {
          // find the frequency of all terms in this field
          val termsEnum = fields.terms(field).iterator()
          val frequencies = TermsAndFreqs(termsEnum).map { termAndFreq =>
            termAndFreq.freq.toDouble
          }.toList
          val jsonObjs = processCounts(frequencies, bins, equalProbability, xLogScale)

          Json.arr(jsonObjs).format(pretty)
        } else {
          // the requested field isn't in this index
          Json.obj().format(pretty)
        }
      }
    }
  }

  /** Return coordinates defining a histogram of counts/frequencies of matches of each rule.
    * @param grammar An Odinson grammar.
    * @param allowTriggerOverlaps Whether or not event arguments are permitted to overlap with the event's trigger.
    * @param bins Number of bins to cut the rule counts into.
    * @param equalProbability Whether to make bin widths variable to make them equally probable.
    * @param xLogScale `log10`-transform the counts of each rule (optional).
    * @param pretty Whether to pretty-print the JSON returned by the function.
    * @return A JSON array of each bin, defined by width, lower bound (inclusive), and frequency.
    */
  def ruleHist() = Action { request =>
    usingNewEngine(config) { extractorEngine =>
      val ruleHistRequest = request.body.asJson.get.as[RuleHistRequest]
      val grammar = ruleHistRequest.grammar
      val allowTriggerOverlaps = ruleHistRequest.allowTriggerOverlaps.getOrElse(false)
      val bins = ruleHistRequest.bins
      val equalProbability = ruleHistRequest.equalProbability
      val xLogScale = ruleHistRequest.xLogScale
      val pretty = ruleHistRequest.pretty
      try {
        // rules -> OdinsonQuery
        val extractors = extractorEngine.ruleReader.compileRuleString(grammar)

        val mentions: Seq[Mention] = {
          val iterator = extractorEngine.extractMentions(
            extractors,
            numSentences = extractorEngine.numDocs(),
            allowTriggerOverlaps = allowTriggerOverlaps,
            disableMatchSelector = false
          )
          iterator.toVector
        }

        val frequencies = mentions
          // rule name is all that matters
          .map(_.foundBy)
          // collect the instances of each rule's results
          .groupBy(identity)
          // filter the rules by name, if a filter was passed
          // .filter{ case (ruleName, ms) => isMatch(ruleName, filter) }
          // count how many matches for each rule
          .map { case (k, v) => v.length.toDouble }
          .toList

        val jsonObjs = processCounts(frequencies, bins, equalProbability, xLogScale)

        Json.arr(jsonObjs).format(pretty)
      } catch handleNonFatal
    }
  }

    /** Return all terms for a given field in orthographic order.   *
    * @param field A token field such as word, lemma, or tag.
    * @return The complete [[List]] of terms in this field for the current index.
    */
  private def fieldVocabulary(field: String): List[String] = {
    // get terms from the requested field (error if it doesn't exist)
    usingNewEngine(config) { extractorEngine =>
      val fields = extractorEngine.index.listFields()
      val terms = TermsAndFreqs(fields.terms(field).iterator()).map(_.term).toList

      terms
    }
  }

  /** Retrieves the POS tags for the current index (limited to extant tags).
    * @param pretty Whether to pretty-print the JSON results.
    * @return A JSON array of the tags in use in this index.
    */
  def tagsVocabulary(pretty: Option[Boolean]) = Action.async {
    // get ready to fail if tags aren't reachable
    try {
      cache.getOrElseUpdate[JsValue]("vocabulary.tags", vocabularyExpiry) {
        val tags = fieldVocabulary(posTagTokenField)
        val json = Json.toJson(tags)
        Future(json)
      }.map { json => json.format(pretty) }
    } catch handleNonFatalInFuture
  }
}