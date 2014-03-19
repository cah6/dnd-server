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

object CharacterDAO extends DocumentDAO[Character] {

    val collectionName = "characters"

}