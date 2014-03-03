package models

import akka.actor._
import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps

import play.api._
import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent._

import akka.util.Timeout
import akka.pattern.ask

import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._

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

	type Point = (Int, Int)
	val gridSize: Point = (4,3)

	var members = Map.empty[String, Concurrent.Channel[JsValue]]

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
			notifySome("gridSize", "null", JsArray(Seq(JsNumber(gridSize._1), JsNumber(gridSize._2))), Set(username))
			var currentUsers: Seq[String] = (for ((k, v) <- (members - username)) yield k).toSeq
			notifySome("players", "null", Json.toJson(currentUsers), Set(username));
			// val msg = JsObject(List(
			// 		"type"		-> JsString("gridSize"),
			// 		"data"		-> JsObject(Seq(
			// 				"gridSize" -> JsArray(Seq(JsNumber(gridSize._1), JsNumber(gridSize._2)))
			// 			))
			// 		))
			//Send a message solely to new user to give them the grid size to display.
			// println("Sending grid size to " + username)
			// for ((recipient, connection) <- members
			// 	if (recipient == username)
			// 	) yield connection.push(msg)

			notifyAll("join", username, JsNull) //tell everybody that this player joined
		}

		case Message(username: String, json: JsValue) => {
			(json \ "type") match {
				case s: JsString		=> notifyAll(s.as[String], username, json \ "data")
				case _ 					=> println("Did not recognize data type.")
			}
		}

		case Quit(username) => {
			println(username + " has left.")
			members = members - username
			notifyAll("quit", username, JsNull)
		}

	}

	//Send data to all users.
	def notifyAll(dataType: String, player: String, data: JsValue) {
		val msg = JsObject(List(
					"type" 		-> JsString(dataType),
					"player"	-> JsString(player),
					"data"		-> data))

		for (channel <- members.values){
			channel.push(msg)
		}
	}

	def notifySome(dataType: String, player: String, data: JsValue, recipients: Set[String]) {
		val msg = JsObject(List(
					"type" 		-> JsString(dataType),
					"player"	-> JsString(player),
					"data"		-> data))
		for ((member, channel) <- members filterKeys recipients) {
			channel.push(msg)
		}
	}

}

case class Join(username: String)
case class Quit(username: String)
case class Message(username: String, text: JsValue)
case class NotifyJoin(username: String)

case class Connected(enumerator:Enumerator[JsValue])
case class CannotConnect(msg: String)