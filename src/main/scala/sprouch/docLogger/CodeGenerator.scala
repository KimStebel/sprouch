package sprouch.docLogger

import spray.http.HttpRequest

trait CodeGenerator {
  def generateCode(request:HttpRequest):Seq[String]
  protected def singleQuoted(s:String):String = "'" + escapeBackslash(s).replaceAll("'","\\\\'") + "'"
  protected def escapeDoublequote(s:String) = s.replaceAll("\"", "\\\\\"")
  protected def jsSingleQuoted(s:String):String = replaceLinebreak(singleQuoted(s))
  protected def replaceLinebreak(s:String) = s.split("\\r?\\n").mkString("\\n")
  protected def escapeBackslash(s:String) = s.replaceAll("\\\\", "\\\\\\\\")
  protected def indented(indent:Int, s:String) = List.fill(indent)("    ").mkString + s
  protected def tripleQuoted(s:String) = {
    val tq = "\"\"\""
    tq + s + tq
  }
  protected def multilineIndented(indent:Int, s:String) = s.split("\n").map(indented(indent, _)).mkString("\n")
}
