package models

import core.IdentifiableModel

import reactivemongo.bson._
import play.modules.reactivemongo.json.BSONFormats._

case class Room(
	override var _id: Option[BSONObjectID] = None,
	roomname: String, 
	users: List[Long]
	) extends IdentifiableModel 

object JsonFormats {
	
	import play.api.libs.json.Json
	import play.api.data._
	import play.api.data.Forms._
	
	implicit val roomFormat = Json.format[Room]
}