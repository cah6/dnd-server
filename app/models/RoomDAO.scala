package controllers

import play.api._
import play.api.mvc._
import play.api.libs.json._
import scala.concurrent.Future
import scala.util.{Try, Success, Failure}

// Reactive Mongo imports
import reactivemongo.api._
import reactivemongo.api.indexes.IndexType._

// Reactive Mongo plugin, including the JSON-specialized collection
import play.modules.reactivemongo.MongoController
import play.modules.reactivemongo.json.collection.JSONCollection

import core._
import models._
import models.JsonFormats._

object RoomDAO extends DocumentDAO[Room] {

    val collectionName = "rooms"

    //used to return Future[Either[ServiceException, Room]]
    def insertOrUpdate(roomname: String, userID: String) = {
    	//see if we have a room by desired name
    	findByRoomname(roomname).onComplete { 
            //case for successfully completing the find query, and getting a Future[Room]
    		case Success(futureroom) 	=> futureroom match {
                //if we already have the room in the database
    			case Some(room) => {
    				//get user list of roomname, add username to list, and update with new list as query
    				update(room.identify, Json.obj("users" -> Json.arr(userID :: room.users))) map {
                        _ => println(s"Recognized the room $roomname and added user to it!") }
    			}
    			//if the room doesn't exist
    			case None => {
    				//create a new room with list containing user
    				insert(Room(roomname, List[String](userID))) map {
                        _ => println(s"Didn't recognize room $roomname, creating it now!")}
    			}
    		}
            //If there was an error while accessing the room, print it.
    		case Failure(t)		=> println("Error while trying to get Option[Room]: " + t.getMessage)
    		
    	}
    }

    def findByRoomname(roomname: String): Future[Option[Room]] = findOne(Json.obj("roomname" -> roomname))

    
}