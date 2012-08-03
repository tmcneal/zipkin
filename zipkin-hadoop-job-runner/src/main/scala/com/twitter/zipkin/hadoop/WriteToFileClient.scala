/*
* Copyright 2012 Twitter Inc.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.twitter.zipkin.hadoop

import java.io._
import java.util.Scanner
import com.twitter.zipkin.gen
import collection.mutable.HashMap

/**
 * A client which writes to a file. This is intended for use mainly to format emails
 * @param combineSimilarNames
 * @param jobname
 */

abstract class WriteToFileClient(combineSimilarNames: Boolean, jobname: String) extends HadoopJobClient(combineSimilarNames) {

  protected var outputDir = ""

  def populateServiceNameList(s: Scanner) {
    if (!combineSimilarNames) return
    while (s.hasNextLine()) {
      val line = new Scanner(s.nextLine())
      serviceNameList.add(getKeyValue(line))
    }
  }

  def getKeyValue(lineScanner: Scanner) = {
    lineScanner.next().replace('/', '.')
  }

  def start(input: String, outputDir: String) {
    this.outputDir = outputDir
    populateServiceNameList(new Scanner(new File(input)))
    processFile(new Scanner(new File(input)))
  }
}

/**
 * A companion object to the WriteToFileClient which ensures that only one writer is ever open per service
 */

object WriteToFileClient {

  protected var pws : HashMap[String, PrintWriter] = new HashMap[String, PrintWriter]()

  def getWriter(s: String) = {
    if (pws.contains(s)) pws(s)
    else {
      val pw = new PrintWriter((new FileOutputStream(s, true)))
      pws += s -> pw
      pw
    }
  }

  def closeAllWriters() {
    for (s <- pws.keys) {
      pws(s).close()
    }
  }

}

/**
 * A client which writes MemcacheRequest data to the file specified
 */

class MemcacheRequestClient extends WriteToFileClient(true, "MemcacheRequest") {

  def processKey(service: String, values: List[String]) {
    val numberMemcacheRequests = {
      val valuesToInt = values map ({ s: String => augmentString(s).toInt })
      valuesToInt.foldLeft(0) ((left: Int, right: Int) => left + right )
    }
    val pw = WriteToFileClient.getWriter(outputDir + "/" + service)
    pw.println(service + " made " + numberMemcacheRequests + " redundant memcache requests")
    pw.flush()
  }

}

/**
 * A client which writes to a file, per each service pair
 */

abstract class WriteToFilePerServicePairClient(jobname: String) extends WriteToFileClient(false, jobname) {

  def writeHeader(service: String, pw: PrintWriter)

  def writeValue(value: String, pw: PrintWriter)

  def processKey(service: String, values: List[String]) {
    val pw = WriteToFileClient.getWriter(outputDir + "/" + service)
    writeHeader(service, pw)
    values.foreach {value: String => writeValue(value, pw)}
    pw.flush()
  }
}


/**
 * A client which writes Timeouts data to the file specified
 */

class TimeoutsClient extends WriteToFilePerServicePairClient("Timeouts") {

  def writeHeader(service: String, pw: PrintWriter) {
    pw.println(service + " timed out in calls to the following services:")
  }

  def writeValue(value: String, pw: PrintWriter) {
    pw.println(value)
  }

}


/**
 * A client which writes Retries data to the file specified
 */

class RetriesClient extends WriteToFilePerServicePairClient("Retries") {

  def writeHeader(service: String, pw: PrintWriter) {
    pw.println(service + " retried in calls to the following services:")
  }

  def writeValue(value: String, pw: PrintWriter) {
    pw.println(value)
  }

}


/**
 * A client which writes WorstRuntimes data to the file specified
 */

class WorstRuntimesClient extends WriteToFilePerServicePairClient("WorstRuntimes") {

  def writeHeader(service: String, pw: PrintWriter) {
    pw.println("Service " + service + " took the longest for these spans:")
  }

  def writeValue(value: String, pw: PrintWriter) {
    pw.println(value)
  }

}


/**
 * A client which writes WorstRuntimesPerTrace data to the file specified. Formats it as a HTML url
 */

class WorstRuntimesPerTraceClient(zipkinUrl: String) extends WriteToFilePerServicePairClient("WorstRuntimesPerTrace") {

  def writeHeader(service: String, pw: PrintWriter) {
    pw.println("Service " + service + " took the longest for these traces:")
  }

  // TODO: Use script to determine which traces are sampled, then wrap in pretty HTML
  def writeValue(value: String, pw: PrintWriter) {
    pw.println("<a href=\"" + zipkinUrl + "/traces/" + value + "\">" + value + "</a>")
  }

}


/**
 * A client which writes ExpensiveEndpoints data to the file specified
 */

class ExpensiveEndpointsClient extends WriteToFilePerServicePairClient("ExpensiveEndpoints") {

  def writeHeader(service: String, pw: PrintWriter) {
    pw.println("The most expensive calls for " + service + " were:")
  }

  def writeValue(value: String, pw: PrintWriter) {
    pw.println(value)
  }

}