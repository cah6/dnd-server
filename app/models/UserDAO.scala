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

object UserDAO extends DocumentDAO[User] {

    val collectionName = "users"

    def newUserJoined(username: String) = {
        //check if we have user in database of distinct users
        findOne(Json.obj("username" -> username)) onComplete {
            case Success(futureuser)    => futureuser match {
                //if we already have the user registered in database, don't have to do anything
                case Some(user) => {
                }
                //if we don't, add them to database 
                case None => {
                    insert(User(username)) map {
                        _ => println(s"Adding user $username to users database!")}
                }
            }
            //If there was an error while accessing the user, print it.
            case Failure(t)     => println("Error while trying to get Option[Room]: " + t.getMessage)
        }
        
    }

}