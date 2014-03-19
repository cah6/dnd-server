package models

import core.IdentifiableModel

import reactivemongo.bson._
import play.modules.reactivemongo.json.BSONFormats._

case class Room(
	roomname: String,
	users: Map[String, Boolean], //maps whether users are looking at this room and should get updates for it
	override var _id: Option[BSONObjectID] = None
	) extends IdentifiableModel 

case class User(
	username: String,
	override var _id: Option[BSONObjectID] = None
	) extends IdentifiableModel

case class Character(
	name: String,
	className: String,
	race: String,
	owner: String,						//unique ID of owner, not name
	currentRoom: Option[String] = None,	//unique ID of room, not name
	override var _id: Option[BSONObjectID] = None
	) extends IdentifiableModel

object JsonFormats {
	
	import play.api.libs.json.Json
	import play.api.data._
	import play.api.data.Forms._
	
	implicit val roomFormat = Json.format[Room]
	implicit val userFormat = Json.format[User]
	implicit val characterFormat = Json.format[Character]
}