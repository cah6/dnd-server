package models

import akka.actor._
import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps

import play.api._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.iteratee._
import play.api.libs.concurrent._

import akka.util.Timeout
import akka.pattern.ask

import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._

import play.api.db._

import anorm._
import anorm.SqlParser._

object ChatRoom{

	implicit val timeout = Timeout(10 second)

	lazy val defaultRoom = Akka.system.actorOf(Props[ChatRoom])

	def join(username:String):Future[(Iteratee[JsValue,_],Enumerator[JsValue])] = {

		((defaultRoom) ? Join(username)).map {

			case Connected(enumerator) => {
				// Create an Iteratee to consume the feed
				val iteratee = Iteratee.foreach[JsValue] { event =>
					//whenever a message is received on the websocket, send it to the room as a Message
					defaultRoom ! Message(username, event)
					}.map{ _ =>
						defaultRoom ! Quit(username)	//if the user leaves (comm stream closes), tell everyone that
					}
					(iteratee,enumerator)
			}

			// Connection error: don't open an iteratee
			case CannotConnect(error) => 
			
			// A finished Iteratee sending EOF
			val iteratee = Done[JsValue, Unit]((),Input.EOF)
			// Send an error and close the socket
			val enumerator =  Enumerator[JsValue](JsObject(Seq("error" -> JsString(error)))).andThen(Enumerator.enumInput(Input.EOF))
			(iteratee,enumerator)
		}

	}
}

class ChatRoom extends Actor {

	//type Point = (Int, Int)
	//val gridSize: Point = (4,3)

	var members = Map.empty[String, Concurrent.Channel[JsValue]]

	implicit val pointFormat = (
		(__ \ "x").format[Int] and
		(__ \ "y").format[Int]
		)(Point.apply, unlift(Point.unapply)) 

	implicit val playerFormat = (
		(__ \ "type").format[String] and 
		(__ \ "name").format[String] and
		(__ \ "location").format[Point]
		)(Player.apply, unlift(Player.unapply))

	implicit val startInfoFormat = (
		(__ \ "type").format[String] and
		(__ \ "gridSize").format[Point] and
		(__ \ "players").format[List[Player]]
		)(StartInfo.apply, unlift(StartInfo.unapply)) 

	val player = {
		get[Long]("id") ~ 
		get[String]("name") ~
		get[Int]("x") ~
		get[Int]("y") map {
			case id~name~x~y => Player("playerType", name, Point(x, y))
		}
	}

	def receive = {

		case Join(username) => {
			if(members.contains(username)) {
				sender ! CannotConnect("This username is already used")	//report back that user could not connect
			}
			else {
				val e = Concurrent.unicast[JsValue]{c =>
					println("Adding " + username + " to chatroom.")
					members = members + (username -> c)
					self ! NotifyJoin(username)
				}
				sender ! Connected(e)						//report back that user has connected
			}
		}

		case NotifyJoin(username) => {
			//tell user starting info: the grid size and current players
			val startInfo = StartInfo("startInfoType", Point(4,3), getAllPlayers())
			println(Json.toJson(startInfo));
			notifySome(Json.toJson(startInfo), Set(username))
			
			//tell everybody that this user has joined, so they can add to their local copy
			val joinInfo = Player("joinType", username, Point(0, 0))
			notifyAll(Json.toJson(joinInfo))

			//add this new user to our databsae
			create(username, 0, 0)
		}

		//All received messages are propogated as a Message case. Pattern match to find the message type here,
		//then act accordingly.
		case Message(username: String, json: JsValue) => {
			println("received message from " + username)
			(json \ "type").as[String] match {
				case "playerType"		=> {
					println("Parsing playerType data")
					val x = (json \ "location" \ "x").as[Int]
					val y = (json \ "location" \ "y").as[Int]
					val positionInfo = Player("positionType", username, Point(x, y))
					notifyAll(Json.toJson(positionInfo))
				}
				case _ 					=> println("Did not recognize data type.")
			}
		}

		//Automatically called from server system when a client connection is closed. Tells this to all
		//connected users.
		case Quit(username) => {
			println(username + " has left.")
			members = members - username
			val quitInfo = Player("quitType", username, Point(0, 0))
			notifyAll(Json.toJson(quitInfo))

			//remove user from database
			delete(username)
		}

	}

	//Send data to all users.
	def notifyAll(msg: JsValue) {
		for (channel <- members.values){
			channel.push(msg)
		}
	}

	def notifySome(msg: JsValue, recipients: Set[String]) {
		for ((member, channel) <- members filterKeys recipients) {
			channel.push(msg)
		}
	}

	def getAllPlayers(): List[Player] = DB.withConnection { implicit c =>
	  SQL("select * from core").as(player *)
	}

	def create(name: String, x: Int, y: Int) {
	  DB.withConnection { implicit c =>
	    SQL("insert into core (name, x, y) values ({name}, {x}, {y})").on(
	      'name -> name,
	      'x 	-> x,
	      'y	-> y
	    ).executeUpdate()
	  }
	}

	def delete(name: String) {
	  DB.withConnection { implicit c =>
	    SQL("delete from core where name = {name}").on(
	      'name -> name
	    ).executeUpdate()
	  }
	}

}

case class Join(username: String)
case class Quit(username: String)
case class Message(username: String, text: JsValue)
case class NotifyJoin(username: String)

case class Connected(enumerator:Enumerator[JsValue])
case class CannotConnect(msg: String)
