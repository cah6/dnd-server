package controllers

import play.api.libs.json._
import play.api.mvc._
import play.api.libs.iteratee._
import play.api.libs.concurrent.Execution.Implicits._
import models._


object Application extends Controller {

	/**
   	 * Handles the chat websocket.
     */
	def connect(room: String, user: String) = WebSocket.async[JsValue] { request  =>
   		ChatRoom.join(room, user)
	}
}