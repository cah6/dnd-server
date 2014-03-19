package core

import scala.concurrent.ExecutionContext

import play.api.Play.current
import play.modules.reactivemongo.ReactiveMongoPlugin
import reactivemongo.bson.{BSONObjectID, BSONValue}

// /**
//  * Helper around `MongoDB` resources.
//  *
//  * @author      Pedro De Almeida (almeidap)
//  */
trait MongoHelper {

	implicit def ec: ExecutionContext = ExecutionContext.Implicits.global

	//lazy val
	def db = ReactiveMongoPlugin.db

}

object MongoHelper extends MongoHelper {

	def identify(bson: BSONValue) = bson.asInstanceOf[BSONObjectID].stringify

}
