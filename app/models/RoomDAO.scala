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
    		case Success(optionroom) => optionroom match {
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
                        _ => println(s"Didn't recognize room $roomname, but just created it!")
                    }
    			}
    		}
            //If there was an error while accessing the room, print it.
    		case Failure(t)		=> println("Error while trying to get Option[Room]: " + t.getMessage)
    		
    	}
    }

    def setInactive(userID: String) = {
        //get rooms that the user is in and active
        find(Json.obj("users." + userID -> true)) onComplete {
            //if the query was successful, get the list of rooms found
            case Success(roomList) => {
                //for each room the user is in, update the user list with the user mapped to false
                for (room <- roomList) yield {
                    val newUserMap = room.users - userID + (userID -> false)
                    update(room.identify, Json.obj("users" -> Json.toJson(newUserMap))) map {
                        _ => println(s"Set $userID to inactive in room " + room.roomname)
                    }
                }
            }
            case Failure(t) => println("Error while trying to find users to set them inactive: " + t.getMessage)
        }
    }

    def findByRoomname(roomname: String): Future[Option[Room]] = findOne(Json.obj("roomname" -> roomname))

}