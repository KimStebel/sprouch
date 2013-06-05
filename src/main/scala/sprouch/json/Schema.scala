package sprouch.json

import sprouch.JsonProtocol._
import spray.json._
import scala.util.Random

object Schema {
  
  private val names = Seq("Isabella", "Sophia", "Emma", "Olivia", "Ava", "Emily", "Abigail",
         "Madison", "Chloe", "Mia", "Addison", "Elizabeth", "Ella", "Natalie",
         "Samantha", "Alexis", "Lily", "Grace", "Hailey", "Alyssa", "Lillian",
         "Hannah", "Avery", "Leah", "Nevaeh", "Sofia", "Ashley", "Anna",
         "Brianna", "Sarah", "Zoe", "Victoria", "Gabriella", "Brooklyn",
         "Kaylee", "Taylor", "Layla", "Allison", "Evelyn", "Riley", "Amelia",
         "Khloe", "Makayla", "Aubrey", "Charlotte", "Savannah", "Zoey",
         "Bella", "Kayla", "Alexa", "Jacob", "Ethan", "Michael", "Jayden",
         "William", "Alexander", "Noah", "Daniel", "Aiden", "Anthony",
         "Joshua", "Mason", "Christopher", "Andrew", "David", "Matthew",
         "Logan", "Elijah", "James", "Joseph", "Gabriel", "Benjamin", "Ryan",
         "Samuel", "Jackson", "John", "Nathan", "Jonathan", "Christian",
         "Liam", "Dylan", "Landon", "Caleb", "Tyler", "Lucas", "Evan", "Gavin",
         "Nicholas", "Isaac", "Brayden", "Luke", "Angel", "Brandon", "Jack",
         "Isaiah", "Jordan", "Owen", "Carter", "Connor", "Justin")
  
  private val ipsum = """lorem ipsum dolor sit amet consetetur sadipscing elitr sed
diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat
sed diam voluptua at vero eos et accusam et justo duo dolores et ea rebum
stet clita kasd gubergren no sea takimata sanctus est lorem ipsum dolor sit
amet lorem ipsum dolor sit amet consetetur sadipscing elitr sed diam nonumy
eirmod tempor invidunt ut labore et dolore magna aliquyam erat sed diam
voluptua at vero eos et accusam et justo duo dolores et ea rebum stet clita
kasd gubergren no sea takimata sanctus est lorem ipsum dolor sit amet lorem
ipsum dolor sit amet consetetur sadipscing elitr sed diam nonumy eirmod
tempor invidunt ut labore et dolore magna aliquyam erat sed diam voluptua
at vero eos et accusam et justo duo dolores et ea rebum stet clita kasd
gubergren no sea takimata sanctus est lorem ipsum dolor sit amet

duis autem vel eum iriure dolor in hendrerit in vulputate velit esse
molestie consequat vel illum dolore eu feugiat nulla facilisis at vero eros
et accumsan et iusto odio dignissim qui blandit praesent luptatum zzril
delenit augue duis dolore te feugait nulla facilisi lorem ipsum dolor sit
amet consectetuer adipiscing elit sed diam nonummy nibh euismod tincidunt
ut laoreet dolore magna aliquam erat volutpat

ut wisi enim ad minim veniam quis nostrud exerci tation ullamcorper
suscipit lobortis nisl ut aliquip ex ea commodo consequat duis autem vel
eum iriure dolor in hendrerit in vulputate velit esse molestie consequat
vel illum dolore eu feugiat nulla facilisis at vero eros et accumsan et
iusto odio dignissim qui blandit praesent luptatum zzril delenit augue duis
dolore te feugait nulla facilisi

nam liber tempor cum soluta nobis eleifend option congue nihil imperdiet
doming id quod mazim placerat facer possim assum lorem ipsum dolor sit
amet consectetuer adipiscing elit sed diam nonummy nibh euismod tincidunt
ut laoreet dolore magna aliquam erat volutpat ut wisi enim ad minim veniam
quis nostrud exerci tation ullamcorper suscipit lobortis nisl ut aliquip ex
ea commodo consequat

duis autem vel eum iriure dolor in hendrerit in vulputate velit esse
molestie consequat vel illum dolore eu feugiat nulla facilisis

at vero eos et accusam et justo duo dolores et ea rebum stet clita kasd
gubergren no sea takimata sanctus est lorem ipsum dolor sit amet lorem
ipsum dolor sit amet consetetur sadipscing elitr sed diam nonumy eirmod
tempor invidunt ut labore et dolore magna aliquyam erat sed diam voluptua
at vero eos et accusam et justo duo dolores et ea rebum stet clita kasd
gubergren no sea takimata sanctus est lorem ipsum dolor sit amet lorem
ipsum dolor sit amet consetetur sadipscing elitr at accusam aliquyam diam
diam dolore dolores duo eirmod eos erat et nonumy sed tempor et et invidunt
justo labore stet clita ea et gubergren kasd magna no rebum sanctus sea sed
takimata ut vero voluptua est lorem ipsum dolor sit amet lorem ipsum dolor
sit amet consetetur sadipscing elitr sed diam nonumy eirmod tempor invidunt
ut labore et dolore magna aliquyam erat

consetetur sadipscing elitr sed diam nonumy eirmod tempor invidunt ut
labore et dolore magna aliquyam erat sed diam voluptua at vero eos et
accusam et justo duo dolores et ea rebum stet clita kasd gubergren no sea
takimata sanctus est lorem ipsum dolor sit amet lorem ipsum dolor sit amet
consetetur sadipscing elitr sed diam nonumy eirmod tempor invidunt ut
labore et dolore magna aliquyam erat sed diam voluptua at vero eos et
accusam et justo duo dolores et ea rebum stet clita kasd gubergren no sea
takimata sanctus est lorem ipsum dolor sit amet lorem ipsum dolor sit amet
consetetur sadipscing elitr sed diam nonumy eirmod tempor invidunt ut
labore et dolore magna aliquyam erat sed diam voluptua at vero eos et
accusam et justo duo dolores et ea rebum stet clita kasd gubergren no sea
takimata sanctus est lorem ipsum dolor sit amet""".split('\n')
         
  /*
    int: random integer between min/max - options: min, max
    float: random float between min/max - options: min, max
    string: random string, length <=15 - options: no options
    seededstring: random string, length <=15 appended to seed - options: seed
    ipsum: lines of lorem ipsum text - options: lines
    choice: random pick from a list of values - options: values (list)
    bool: random true/false - options: no options
    name: random name chosen from 100 popular names - options: no options
    nest: a nested structure, value should be another type (including nest) - options: value
*/
  sealed trait Schema
  case class FieldSchema(
      `type`:String,
      min:Option[Double],
      max:Option[Double],
      seed:Option[String],
      lines:Option[Int],
      values:Option[Seq[String]],
      value:Option[ComplexSchema]
  ) extends Schema
  case class ComplexSchema(fields: Map[String, Schema]) extends Schema {
    def generate():JsObject = {
      JsObject(fields.map { 
        case (k,fs:FieldSchema)=> k -> (fs.`type`.toLowerCase match {
          case "int" => JsNumber(randomInt(fs.min.map(_.toInt), fs.max.map(_.toInt)))
          case "float" => JsNumber(
              if (fs.min.isEmpty && fs.max.isEmpty)
                randomDouble
              else
                randomDouble(fs.min.getOrElse(Double.MinValue), fs.max.getOrElse(Double.MaxValue)))
          case "string" => JsString(randomString)
          case "seededstring" => JsString(fs.seed.getOrElse("") + randomString)
          case "bool" => JsBoolean(rand.nextBoolean)
          case "choice" => JsString(randomElem(fs.values.getOrElse(List(""))))
          case "name" => JsString(randomElem(names))
          case "ipsum" => JsString(ipsum.take(randomInt(min=1, max=fs.lines.getOrElse(ipsum.length))).mkString("\n"))
          case "nest" => fs.value.get.generate()
        })
        case (k, c:ComplexSchema) => k -> c.generate()  
      })
    }
  }
  private implicit lazy val fieldSchemaFormat = jsonFormat7(FieldSchema)
  implicit lazy val complexSchemaFormat:JsonFormat[ComplexSchema] = new JsonFormat[ComplexSchema] {
    override def read(js:JsValue):ComplexSchema = {
      val jso = js.asJsObject
      ComplexSchema(jso.fields.map{ case (k,v) => {
        k -> fieldSchemaFormat.read(v)
      }})
    }
    
    override def write(st:ComplexSchema):JsValue = {
      JsObject(st.fields.map{ case(k,v) => {
        k -> (v match {
          case fs:FieldSchema => fieldSchemaFormat.write(fs)
          case cs:ComplexSchema => write(cs)
        })
      }})
    }
  }
  private val rand = new Random

  private def randomInt(min:Option[Int], max:Option[Int]):Int = randomInt(
      min.getOrElse(Integer.MIN_VALUE),
      max.getOrElse(Integer.MAX_VALUE))
  private def randomInt(min:Int = Integer.MIN_VALUE, max:Int = Integer.MAX_VALUE):Int = {
    rand.nextInt(max - min + 1) + min
  }
  private def randomDouble(min:Double, max:Double) = min + (rand.nextDouble * (max-min))
  private def randomDouble = rand.nextDouble
  private def randomChar = rand.nextPrintableChar
  private def randomString = rand.nextString(15)
  private def randomElem[A](s:Seq[A]):A = {
    s(randomInt(min = 0, max = s.length - 1))
  } 
  

  
}