package basics

import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.util.zip.ZipFile

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

import console.CsvToOds

@RunWith(classOf[JUnitRunner])
class Reading extends FunSuite {
  test("Convert CSV file"){
    val csv = File.createTempFile("prefix", null)
    val csvWriter = new FileWriter(csv)
    csvWriter.write("col1, col 2,x\n34, ddf dsf,\"a\"\"c\nb\"\nabc")
    csvWriter.flush()
    val ods = File.createTempFile("prefix", null)
    
    CsvToOds.main(Array("--output", ods.getAbsolutePath(), "--input", csv.getAbsolutePath()))
    
    val zip = new ZipFile(ods)
    val contentReader = new BufferedReader(new InputStreamReader(zip.getInputStream(zip.getEntry("content.xml"))))
    val content = Stream.continually(contentReader.readLine).takeWhile(_!=null).mkString
    assert(content.contains("col1"))
    assert(content.indexOf("col1")<content.indexOf("col 2"))
    assert(content.contains("abc"))
  }
}