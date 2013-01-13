package console

import java.io.BufferedReader
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

import scala.Array.canBuildFrom

import org.supercsv.prefs.CsvPreference

import api.CsvMapReader
import scopt.immutable.OptionParser

/**
 * Converts CSV files to ODS.
 * The size of the files is not limited by memory.
 */
object CsvToOds {
  case class Config(
    // ODS file without content data but having meta data
    template: String = "templates/template.ods",
    // ODS file
    output: OutputStream = System.out,
    // CSV file
    input: BufferedReader = io.Source.stdin.bufferedReader,
    // Quote char
    quote: Char = '"',
    // Column separator
    delimiter: Char = ',',
    trimSpaces: Boolean = false)

  def main(args: Array[String]) {
    val argParser = new OptionParser[Config]("java -jar csvtools.jar") {
      override def showUsage = Console.err.println("Converts a CSV file to an ODS spreadsheet." + usage)
      
      def options = Seq(
        opt("t", "template", "Path of template file") {
          (name, config) => config.copy(template = name)
        },
        opt("o", "output", "Path of generated file. Defaults to standard output.") {
          (value, config) => config.copy(output = new FileOutputStream(value))
        },
        opt("i", "input", "Path of CSV file to convert. Defaults to standard input.") {
          (value, config) => config.copy(input = new BufferedReader(new InputStreamReader(new FileInputStream(value))))
        },
        opt("q", "quote", "Quote character. Defaults to \".") {
          (value, config) =>
            if (value.length() == 1) {
              config.copy(quote = value.charAt(0))
            } else throw new IllegalArgumentException("Quote character must be one character.")
        },
        opt("d", "delimiter", "Column separator. Defaults to ,.") {
          (value, config) =>
            if (value.length() == 1) {
              config.copy(quote = value.charAt(0))
            } else throw new IllegalArgumentException("Column delimiter character must be one character.")
        },
        booleanOpt("trim-spaces", "Trims spaces around values. Default behavior is to retain them."){
          (value, config) => config.copy(trimSpaces = value)
        })
    }.parse(args, Config()) map { config =>
      // Open the template which does not contain content but meta data
      var zip = new ZipFile(config.template)

      var zipOut = new ZipOutputStream(config.output)
      var zipEntries = zip.entries()
      while (zipEntries.hasMoreElements()) {
        val entry = zipEntries.nextElement()
        val is = zip.getInputStream(entry)
        val (outEntry, finalIs) = entry.getName() match {
          // Process content.xml
          case name @ "content.xml" => {
            val pref = new CsvPreference.Builder(config.quote, config.delimiter, "\r\n").surroundingSpacesNeedQuotes(config.trimSpaces).build()
            (new ZipEntry(name), contentInputStream(is, config.input, pref))
          }
          // Simple copy other files
          case _ => (new ZipEntry(entry), is)
        }
        zipOut.putNextEntry(outEntry)
        var b = finalIs.read()
        while (b != -1) {
          zipOut.write(b)
          b = finalIs.read()
        }
        zipOut.closeEntry()
      }
      zipOut.close()
    }
  }

  /**
   * Places content from the CSV in the template
   * @return Stream including data from CSV
   */
  def contentInputStream(original: InputStream, csvStream: BufferedReader, pref: CsvPreference): InputStream = {
    // Chop template (okay to happen in memory since template is expected to be rather small)
    val templateReader = new BufferedReader(new InputStreamReader(original))
    val templateContent = Stream.continually(templateReader.readLine()).takeWhile(_ != null).mkString
    val templateHeader = templateContent.substring(0, templateContent.indexOf("<table:table-row "))
    val templateFooter = templateContent.substring(templateContent.indexOf("</table:table-row>") + "</table:table-row>".length())

    val mapReader = new CsvMapReader(csvStream, pref)
    val cols = mapReader.getColumns
    val s = mapReader.toIterator

    // Stream which combines header, data from CSV and footer
    val output = new InputStream {
      // Initially header of conent.xml + column headers
      var buffer: Array[Byte] = (templateHeader + (<table:table-row table:style-name="ro1">{
        for (col <- cols) yield <table:table-cell office:value-type="string"><text:p>{
          col
        }</text:p></table:table-cell>
      }</table:table-row>)).getBytes()
      // Index of character in buffer that will be returned on next call to read
      var bufferPosition = 0

      var allCellsWritten = false

      def read(): Int = {
        if (bufferPosition < buffer.length) {
          bufferPosition = bufferPosition + 1
          buffer(bufferPosition - 1).toInt
        } else if (s.hasNext) {
          // Fill buffer with next record (row in spreadsheet)
          val record = s.next
          buffer = (<table:table-row table:style-name="ro1">{
            for (col <- cols) yield <table:table-cell office:value-type="string"><text:p>{
              record.getOrElse(col, "")
            }</text:p></table:table-cell>
          }</table:table-row> + "\n").getBytes()
          bufferPosition = 0
          read
        } else if (allCellsWritten == false) {
          // Append conent.xml footer after all cell contents have been written
          allCellsWritten = true
          buffer = templateFooter.getBytes()
          bufferPosition = 0
          read
        } else
          // Signalize the stream has been fully read
          -1
      }
    }
    output
  }
}