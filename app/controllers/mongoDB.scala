package controllers

import play.api.test._
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

	def createRoom(roomname: String) = Action.async {
		val room = Room(roomname, List[Long]())
	    // insert the user
	    val futureResult = rooms.insert(room)
	    futureResult.map(_ => Ok)
	}

	//def containsRoom(roomname: String) = ?

	def addUser(roomname: String, username: String) = {

		val modifier = Json.obj("$set" -> Json.arr(
			"users" -> Await.result(getUserList(roomname),10 seconds)
			))

		//rooms.update(Json.obj("roomname" -> roomname), modifier).result(10 seconds)
		rooms.update(Json.obj("roomname" -> roomname), modifier).onComplete {
			case Failure(e) => throw e
			case Success(_) => println("successful!")
		}
	}

	def getUserList(roomname: String): Future[List[Long]] = {
		val futureuserlist = rooms.find(Json.obj("roomname" -> roomname)).cursor.collect[List]().map {
			futureroomlist => futureroomlist.head.users
		}
		futureuserlist
	}

}



