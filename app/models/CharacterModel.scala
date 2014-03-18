package models

import akka.actor._
import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.collection.mutable

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

import models.JsonFormats._
import scala.util.{Try, Success, Failure}

import core._

object ChatRoom {

	implicit val timeout = Timeout(10 second)

	lazy val connectPoint = Akka.system.actorOf(Props[ChatRoom])

	def join(username:String): Future[(Iteratee[JsValue,_],Enumerator[JsValue])] = {
		//join user with credentials they input
		((connectPoint) ? Join(username)).map {
			//user was connected successfully, from now on will be referred to by ID
			case Connected(enumerator, userID: String) => {
				println("Got a message! Sending it to chatroom to be handled.")
				// Create an Iteratee to consume the feed
				val iteratee = Iteratee.foreach[JsValue] { event =>
					//whenever a message is received on the websocket, send it to the room as a Message
					connectPoint ! Message(userID, event)
					}.map{ _ =>
						//this map is automatically called when the channel closes
						connectPoint ! Quit(userID, enumerator)
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

	//map of all connected users (by their unique ID, not username) to the channel we talk to them with
	var members = mutable.Map.empty[String, List[Concurrent.Channel[JsValue]]]

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

	def receive = {

		case Join(username: String) => {
			//handle database aspect of user joining
			UserDAO.newUserJoined(username)

			//save value of sender so we can access it in a little bit
			val origin = sender

			//handle updating user channel list
			UserDAO.findOne(Json.obj("username" -> username)) onComplete {
				case Success(futureuser)    => futureuser match {
	                //if we have the user, add them to our list of users
	                case Some(user) => {
	                	val userID = user.identify
	                	//update our members map to have this channel in the user list
	                	val e = Concurrent.unicast[JsValue]{ channel =>
	                		println(s"Adding $userID to server-side channels.")
							//prepend this connection to current list of channels, and update members with that mapping
							(members get userID) match {
							//if user is connected somewhere else, get the list of channels and update it
							case Some(channelList)	=> members(userID) = channel :: channelList
							//if user wasn't connected at all before this, make a new entry in map for him/her
							case None 				=> members = members + (userID -> List(channel))
							}
						}
						println("Attempting to send back that user was connected.")
						origin ! Connected(e, userID)	//report back that user has connected, with their ID
					}
	                //if we don't (which shouldn't happen), say something
	                case None => println(s"Couldn't find user $username, though they were just added.")
	            }
	            //If there was an error while accessing the user, print it.
	            case Failure(t)     => {
	            	println("Error while trying to get Option[User]: " + t.getMessage)
	            }
	        }
	    }

		//All received messages are propogated as a Message case. Pattern match to find the message type here,
		//then act accordingly.
		case Message(userID: String, json: JsValue) => {
			println("received message " + json + " from " + userID)
			(json \ "type").as[String] match {
				case "joinGameType"		=> {
					val gamename = (json \ "gamename").as[String]
					//go through creation routine
					RoomDAO.insertOrUpdate(gamename, userID)
				}
				case "playerType"		=> {
					val x = (json \ "location" \ "x").as[Int]
					val y = (json \ "location" \ "y").as[Int]
					val positionInfo = Player("positionType", userID, Point(x, y))
					notifyAll(Json.toJson(positionInfo))
				}
				case _ 	=> println("Did not recognize data type.")
			}
		}

		//Automatically called from server system when a client connection is closed.
		case Quit(userID, channel) => {
			println(s"Removing $userID from server connection list.")
			//remove this channel from the member's list of channels
			members(userID) = members(userID) diff List(channel)
		}

	}

	//Send data to all users.
	def notifyAll(msg: JsValue) {
		// for (channel <- members.values){
		// 	channel.push(msg)
		// }
	}

	//Send data to only a subset of users
	def notifySome(msg: JsValue, recipients: Set[String]) {
		// for ((member, channel) <- members filterKeys recipients) {
		// 	channel.push(msg)
		// }
	}

}

case class Join(username: String)
case class Quit(userID: String, enumerator:Enumerator[JsValue])
case class Message(userID: String, text: JsValue)

case class Connected(enumerator:Enumerator[JsValue], userID: String)
case class CannotConnect(msg: String)
