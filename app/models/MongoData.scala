package models

case class Room(roomname: String, users: List[Long]) 

object JsonFormats {
	
	import play.api.libs.json.Json
	import play.api.data._
	import play.api.data.Forms._
	
	implicit val roomFormat = Json.format[Room]
}