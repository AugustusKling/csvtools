package api

import java.io.Reader
import org.supercsv.io.CsvListReader
import org.supercsv.prefs.CsvPreference

/**
 * Makes CSV contents available as stream of records.
 */
class CsvMapReader(in: Reader, pref: CsvPreference) extends Traversable[Map[String, String]] {
  val reader = new CsvListReader(in, pref)
  val headers = reader.getHeader(true)

  val stream = Stream.continually(reader.read).takeWhile(_ != null)

  /**
   * @return List of column names
   */
  def getColumns = headers

  override def toIterator: Iterator[Map[String, String]] = {
    val sv = stream.map(raw => {
      val data = raw.toArray(new Array[String](0))
      val t = for (i <- headers.indices)
        yield headers(i) -> (if (data.length > i) Some(data(i)) else None)

      (for (item <- headers.zip(data) if item._2 != null)
        yield item._1 -> item._2).toMap
    })
    sv.iterator
  }

  def foreach[U](f: Map[String, String] => U) = {
    toIterator.map(f)
  }
}