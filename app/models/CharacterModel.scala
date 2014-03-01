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

			case Connected(enumerator) => 
				// Create an Iteratee to consume the feed
				val iteratee = Iteratee.foreach[JsValue] { event =>
					//whenever a message is received on the websocket, send it to the room as a Message
					defaultRoom ! Message(username, event)
					}.map{ _ =>
						defaultRoom ! Quit(username)	//if the user leaves (comm stream closes), tell everyone that
					}
					(iteratee,enumerator)

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
				}
				sender ! Connected(e)						//report back that user has connected
				self ! NotifyJoin(username)								//push to notify everyone users has joined
			}
		}

		case NotifyJoin(username) => {
			val msg = JsObject(List(
					"recipient" -> JsArray(JsString(username) :: Nil),
					"data"		-> JsObject(Seq(
							"gridSize" -> JsArray(Seq(JsNumber(gridSize._1), JsNumber(gridSize._2)))
						))
					))
			for ((recipient, connection) <- members
				if (recipient == username)
				) yield connection.push(msg)
			//chatChannel.push(msg) //tell user size of map

			//notifyAll("join", username, "has entered the room") //tell everybody that this player joined
		}

		case Message(username: String, json: JsValue) => {
			println(username + " sent:")
			(json \ "type") match {
				case s: JsString		=> println("User sent: " + (json \ "data" \ s.as[String]))
				case _ 					=> println("Did not recognize data type.")
			}
		}

		case Quit(username) => {
			println(username + " has left.")
			members = members - username
			//notifyAll("quit", username, "has left the room")
		}

	}

	//Send data to all users.
	def notifyAll(kind: String, user: String, text: String) {
		println("In notify all, kind = " + kind)
		val msg = JsObject(
			Seq(
				"kind" -> JsString(kind),
				"user" -> JsString(user),
				"message" -> JsString(text),
				"recipient" -> JsArray(
					//members.toList.map(JsString)
					)
				)
			)
		//chatChannel.push(msg)
	}

}

case class Join(username: String)
case class Quit(username: String)
case class Message(username: String, text: JsValue)
case class NotifyJoin(username: String)

case class Connected(enumerator:Enumerator[JsValue])
case class CannotConnect(msg: String)