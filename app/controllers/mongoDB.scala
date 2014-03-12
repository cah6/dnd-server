package controllers

import play.api._
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import scala.concurrent.Future

// Reactive Mongo imports
import reactivemongo.api._
import reactivemongo.core.commands._

// Reactive Mongo plugin, including the JSON-specialized collection
import play.modules.reactivemongo.MongoController
import play.modules.reactivemongo.json.collection.JSONCollection

object mongoDB extends Controller with MongoController {

	import play.api.data.Form
	import models._
  	import models.JsonFormats._

	def rooms: JSONCollection = db.collection[JSONCollection]("rooms")

	def createRoom(roomname: String): Future[LastError] = {
		val room = Room(roomname, List[Long]())
	    // insert the user
	    rooms.insert(room)
	}

}



