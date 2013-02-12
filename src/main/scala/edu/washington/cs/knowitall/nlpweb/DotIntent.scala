package edu.washington.cs.knowitall.nlpweb

import java.io.PrintWriter
import java.net.URLConnection
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import unfiltered.filter.Intent
import unfiltered.request.GET
import unfiltered.request.POST
import unfiltered.request.Path
import unfiltered.request.Seg
import unfiltered.response.ComposeResponse
import unfiltered.response.ContentType
import unfiltered.response.ResponseBytes
import java.net.URLDecoder
import org.apache.commons.codec.net.URLCodec

object DotIntent extends BasePage {
  val base64 = new Base64(false)
  val logger = LoggerFactory.getLogger(this.getClass)
  val urlCodec = new URLCodec

  final val DEFAULT_FORMAT="png"

  def dotbin(dot: String, format: String): Array[Byte] = {
    val p = Runtime.getRuntime().exec("dot -T" + format)
    val pw = new PrintWriter(p.getOutputStream)
    try {
      pw.write(dot)
    } finally {
      pw.close()
    }

    // copy the error (if any)
    IOUtils.copy(p.getErrorStream, System.err)
    IOUtils.toByteArray(p.getInputStream)
  }

  def dotbase64(dot: String, format: String): String = {
    base64.encodeToString(dotbin(dot, format)).trim
  }

  lazy val dotFormats = {
    val target = "Use one of:"
    try {
      val p = Runtime.getRuntime().exec("dot -Tasdf")
      p.getOutputStream().close

      val response = IOUtils.toString(p.getErrorStream)

      if (p.exitValue == 127) {
        // format not found
        List.empty
      } else {
        val index = response.indexOf(target)
        if (index == -1) {
          // output not as expected
          logger.warn("DOT response unexpected: " + response)
          List()
        }
        else {
          // we found the formats
          val formats = response.substring(index + target.length).trim.toLowerCase.split("\\s+").toList
          logger.info("Supported DOT formats: " + formats)
          formats
        }
      }
    } catch {
      case e => logger.error("Could not establish dot formats.", e); List.empty
    }
  }

  def intent = Intent {
    case req @ GET(Path("/dotter/")) =>
      basicPage(req,
        name = "Dotter",
        text = "",
        info = "",
        config = "",
        stats = "",
        result = "")

    case req @ GET(Path(Seg("dotter" :: text :: Nil))) =>
      basicPage(req,
        name = "Dotter",
        info = "",
        text = text,
        config = "",
        stats = "",
        result = "")

    case req @ POST(Path(Seg("dotter" :: xs))) =>
      val dot = req.parameterValues("text").headOption.getOrElse {
        throw new IllegalArgumentException("text field not specified.")
      }
      val base64Image = dotbase64(dot, DEFAULT_FORMAT)
      basicPage(req,
        name = "Dotter",
        text = dot,
        result = "<img id=\"graph\" src=\"data:image/"+DEFAULT_FORMAT+";base64,"+base64Image+"\">")
    case req @ POST(Path(Seg("dotbin" :: format :: Nil))) =>
      req match {
        case Params(params) => println(params)
      }
      req.parameterNames foreach println
      val dot = req.parameterValues("dot").headOption.getOrElse {
        throw new IllegalArgumentException("dot field not specified in POST")
      }
      if (!(dotFormats contains format)) {
        errorPage(req, "Format not supported: " + format)
      } else {
        val contentType = guessContentType(format)
        logger.debug("Sending to dot: " + contentType + ": " + dot)
        val bytes = dotbin(dot, format)
        new ComposeResponse(ContentType(contentType) ~> ResponseBytes(bytes))
      }

    case req @ POST(Path(Seg("dotbase64" :: format :: Nil))) =>
      val dot = req.parameterValues("dot").headOption.getOrElse {
        throw new IllegalArgumentException("dot field not specified in POST")
      }
      if (!(dotFormats contains format)) {
        errorPage(req, "Format not supported: " + format)
      } else {
        val contentType = guessContentType(format)
        logger.debug("Sending to dot: " + contentType + ": " + dot)
        val base64 = dotbase64(dot, format)
        new ComposeResponse(ContentType(contentType) ~> ResponseString(base64))
      }

    case req @ GET(Path(Seg("dot" :: format :: xs))) if !(dotFormats contains format) =>
      errorPage(req, "Format not supported: " + format + ".\n" + "Supported formats by dot: " + dotFormats.mkString("\n"))

    case req @ GET(Path(Seg("dot" :: format :: Nil))) =>
      errorPage(req, "You must specify pass a DOT graph as a parameter.")

    case req @ GET(Path(Seg("dot" :: format :: code :: Nil))) =>
      val decoded = URLDecoder.decode(code, "UTF-8")
      if (!(dotFormats contains format)) {
        errorPage(req, "Format not supported: " + format)
      } else {
        val contentType = guessContentType(format)
        logger.debug("Sending to dot: " + contentType + ": " + decoded)
        val p = Runtime.getRuntime().exec("dot -T" + format)
        val pw = new PrintWriter(p.getOutputStream)
        try {
          pw.write(decoded)
        } finally {
          pw.close()
        }

        // copy the error (if any)
        IOUtils.copy(p.getErrorStream, System.err)
        val bytes = IOUtils.toByteArray(p.getInputStream)
        new ComposeResponse(ContentType(contentType) ~> ResponseBytes(bytes))
      }
  }

  private def guessContentType(ext: String) =
    if (ext == "svg") "text/html"
    else URLConnection.guessContentTypeFromName("." + ext)
}
