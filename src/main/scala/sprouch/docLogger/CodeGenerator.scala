package sprouch.docLogger

import spray.http.HttpRequest

trait CodeGenerator {
  def generateCode(request:HttpRequest):Seq[String]
  def singleQuoted(s:String):String = "'" + s.replaceAll("\\\\", "\\\\\\\\").replaceAll("'","\\\\'") + "'"
  def doubleQuoted(s:String):String = "\"" + s.replaceAll("\\\\", "\\\\\\\\").replaceAll("\"","\\\\\"") + "\""
  def indented(indent:Int, s:String) = List.fill(indent)("    ").mkString + s
    
}
