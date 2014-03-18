package models

import core.IdentifiableModel

import reactivemongo.bson._
import play.modules.reactivemongo.json.BSONFormats._

case class Room(
	roomname: String,
	users: List[String], 
	override var _id: Option[BSONObjectID] = None
	) extends IdentifiableModel 

case class User(
	username: String,
	override var _id: Option[BSONObjectID] = None
	) extends IdentifiableModel

object JsonFormats {
	
	import play.api.libs.json.Json
	import play.api.data._
	import play.api.data.Forms._
	
	implicit val roomFormat = Json.format[Room]
	implicit val userFormat = Json.format[User]
}