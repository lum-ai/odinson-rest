package ai.lum.odinson.rest

import org.apache.lucene.index.TermsEnum

case class TermAndFreq(term: String, freq: Long)

class TermsAndFreqs(termsEnum: TermsEnum) extends Traversable[TermAndFreq] {

  override def foreach[U](f: TermAndFreq => U): Unit = {
    while (termsEnum.next() != null) {
      val term = termsEnum.term.utf8ToString()
      val freq = termsEnum.totalTermFreq

      f(TermAndFreq(term, freq))
    }
  }

}

object TermsAndFreqs {

  def apply(termsEnum: TermsEnum): TermsAndFreqs = new TermsAndFreqs(termsEnum)
}