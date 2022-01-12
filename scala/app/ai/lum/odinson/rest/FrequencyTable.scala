package ai.lum.odinson.rest

class FrequencyTable(minIdx: Int, maxIdx: Int, reverse: Boolean) {

  private var freqs: List[(String, Long)] = Nil

  private def greaterThanOrEqualTo(reverse: Boolean)(first: Long, second: Long) =
    if (reverse) first <= second else first >= second

  private def greaterThan(reverse: Boolean)(first: Long, second: Option[Long]) =
    if (reverse) {
      // anything is less than a None
      first < second.getOrElse(Long.MaxValue)
    } else {
      // anything is greater than a None
      first > second.getOrElse(Long.MinValue)
    }

  private val geq: (Long, Long) => Boolean = greaterThanOrEqualTo(reverse)
  private val gt: (Long, Option[Long]) => Boolean = greaterThan(reverse)

  def update(newTerm: String, newFreq: Long): Unit = {
    // only update if new term is higher (or lower, if reversed) frequency as last member
    // OR if we don't have enough elements in our frequency table
    if (gt(newFreq, freqs.lastOption.map(_._2)) || freqs.length <= maxIdx) {
      // separate those in front of this value from those behind
      val (before, after) = freqs.partition { case (_, extantFreq) => geq(extantFreq, newFreq) }
      // secondary sorting feature is alphanumeric, same as the input order
      // therefore we can safely ignore everything we cut out after maxIdx
      // FIXME: correct this
      //freqs = ((before :+ (newTerm, newFreq)) ++ after).take(maxIdx + 1)
    }
  }

  def get: List[(String, Long)] = freqs.slice(minIdx, maxIdx + 1)

}