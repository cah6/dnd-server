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
                    //at this point, need to check if user is in the room. 
                    //if they're in the room, update their status
                    if (room.users contains userID){
                        val newUserMap = room.users - userID + (userID -> true)
                        update(room.identify, Json.obj("users" -> Json.toJson(newUserMap))) map {
                        _ => println(s"Recognized the room $roomname and updated $userID's status!")
                        }
                    }
                    //if not, add them and update status
                    else {
                        update(room.identify, Json.obj("users" -> Json.toJson((room.users + (userID -> true))))) map {
                        _ => println(s"Recognized the room $roomname and added $userID to it!")
                        }
                    }
    			}
    			//if the room doesn't exist
    			case None => {
    				//create a new room with list containing user -> true
    				insert(Room(roomname, Map[String, Boolean](userID -> true))) map {
                        _ => println(s"Didn't recognize room $roomname, creating it now!")}
    			}
    		}
            //If there was an error while accessing the room, print it.
    		case Failure(t)		=> println("Error while trying to get Option[Room]: " + t.getMessage)
    		
    	}
    }

    def findByRoomname(roomname: String): Future[Option[Room]] = findOne(Json.obj("roomname" -> roomname))

    
}