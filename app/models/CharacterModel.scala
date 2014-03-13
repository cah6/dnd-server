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

import controllers._

object ChatRoom {

	implicit val timeout = Timeout(10 second)

	//var chatrooms: Map[String, ActorRef] = Map.empty[String, ActorRef]

	lazy val connectPoint = Akka.system.actorOf(Props[ChatRoom])

	def join(username:String): Future[(Iteratee[JsValue,_],Enumerator[JsValue])] = {

		//check if the specific chatroom exists. if not, make it and, regardless, use it to join a player
		// if (!chatrooms.contains(roomname)){
		// 	chatrooms = chatrooms + (roomname -> Akka.system.actorOf(Props[ChatRoom]))
		// }
		// val chatroom: ActorRef = chatrooms(roomname)

		((connectPoint) ? Join(username)).map {

			case Connected(enumerator) => {
				// Create an Iteratee to consume the feed
				val iteratee = Iteratee.foreach[JsValue] { event =>
					//whenever a message is received on the websocket, send it to the room as a Message
					connectPoint ! Message(username, event)
					}.map{ _ =>
						connectPoint ! Quit(username)	//if the user leaves (comm stream closes), tell everyone that
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

	//map of all online users to the channel we talk to them with
	var members = Map.empty[String, Concurrent.Channel[JsValue]]

	//implicit vals are so we can easily format defined classes to JSON to send
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

	//define how to retrieve a user from SQL database of all users
	val user = {
		get[Long]("id") ~ 
		get[String]("username") ~
		get[Boolean]("isOnline") map {
			case id~username~isOnline => User(id, username, isOnline)
		}
	}

	def receive = {

		case Join(username) => {
			val count = numUsers(username)
			
			//if we don't have them in the database, create a new entry for them
			if (count == 0){
				println("Creating a new user for " + username)
				create(username, true)
			}
			//if we have them in the database, set isOnline to true and connect them
			else if (count == 1){
				println("Connecting existing user " + username)
				val e = Concurrent.unicast[JsValue]{c =>
					println("Adding " + username + " to server.")
					members = members + (username -> c)
					setOnline(username)
				}
				sender ! Connected(e)						//report back that user has connected
			}
			//if we have 2 or more in database, something went wrong
			else {
				println(count + " users with the same name, should not happen.")
				sender ! CannotConnect("This username is already used")	//report back that user could not connect
			}
		}

		// case NotifyJoin(username) => {
		// 	//tell user starting info: the grid size and current players
		// 	val startInfo = StartInfo("startInfoType", Point(4,3), getAllPlayers())
		// 	println(Json.toJson(startInfo));
		// 	notifySome(Json.toJson(startInfo), Set(username))
			
		// 	//tell everybody that this user has joined, so they can add to their local copy
		// 	val joinInfo = Player("joinType", username, Point(0, 0))
		// 	notifyAll(Json.toJson(joinInfo))

		// 	//add this new user to our database
		// 	create(username, 0, 0)
		// }

		//All received messages are propogated as a Message case. Pattern match to find the message type here,
		//then act accordingly.
		case Message(username: String, json: JsValue) => {
			println("received message " + json + " from " + username)
			(json \ "type").as[String] match {
				case "joinGameType"		=> {
					val gamename = (json \ "gamename").as[String]
					RoomDAO.findByRoomname(gamename)
				}
				case "playerType"		=> {
					val x = (json \ "location" \ "x").as[Int]
					val y = (json \ "location" \ "y").as[Int]
					val positionInfo = Player("positionType", username, Point(x, y))
					notifyAll(Json.toJson(positionInfo))
				}
				case _ 	=> println("Did not recognize data type.")
			}
		}

		//Automatically called from server system when a client connection is closed. Tells this to all
		//connected users.
		case Quit(username) => {
			println(username + " has left.")
			members = members - username;
			//set user to be offline
			setOffline(username)
		}

	}

	//Send data to all users.
	def notifyAll(msg: JsValue) {
		// for (channel <- members.values){
		// 	channel.push(msg)
		// }
	}

	def notifySome(msg: JsValue, recipients: Set[String]) {
		// for ((member, channel) <- members filterKeys recipients) {
		// 	channel.push(msg)
		// }
	}

	// def getAllPlayers(): List[Player] = DB.withConnection { implicit c =>
	// 	SQL("select * from core").as(player *)
	// }

	def numUsers(username: String): Long = {
		play.api.db.DB.withConnection { implicit c =>
			SQL("select count(*) from users where username = {username}").on(
				'username -> username
				).as(scalar[Long].single)
		}
	}

	def create(username: String, isOnline: Boolean) {
		play.api.db.DB.withConnection { implicit c =>
			SQL("insert into users (username, isOnline) values ({username}, {isOnline})").on(
				'username -> username,
				'isOnline -> true
				).executeUpdate()
		}
	}

	def delete(username: String) {
		play.api.db.DB.withConnection { implicit c =>
			SQL("delete from users where username = {username}").on(
				'username -> username
				).executeUpdate()
		}
	}

	def setOffline(username: String) {
		play.api.db.DB.withConnection { implicit c =>
			SQL("update users set isOnline=false where username={username}").on(
				'username -> username
				).executeUpdate()
		}
	}

	def setOnline(username: String) {
		play.api.db.DB.withConnection { implicit c =>
			SQL("update users set isOnline=true where username={username}").on(
				'username -> username
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

case class User(id: Long, username: String, isOnline: Boolean)
