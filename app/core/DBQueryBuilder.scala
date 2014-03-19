package core

import play.api.libs.json.{Writes, Json, JsObject}
import reactivemongo.bson.BSONObjectID

/* Implicits */
import play.modules.reactivemongo.json.BSONFormats._

/**
 * Query builder wrapping common queries and MongoDB operators.
 *
 * TODO: create a real query `builder`
 *
 * @author      Pedro De Almeida (almeidap)
 */
object DBQueryBuilder {

	def id(objectId: String): JsObject = id(BSONObjectID(objectId))

	def id(objectId: BSONObjectID): JsObject = Json.obj("_id" -> objectId)

	def set(field: String, data: JsObject): JsObject = set(Json.obj(field -> data))

	def set[T](field: String, data: T)(implicit writer: Writes[T]): JsObject = set(Json.obj(field -> data))

	def set(data: JsObject): JsObject = Json.obj("$set" -> data)

	def set[T](data: T)(implicit writer: Writes[T]): JsObject = Json.obj("$set" -> data)

	def push[T](field: String, data: T)(implicit writer: Writes[T]): JsObject = Json.obj("$push" -> Json.obj(field -> data))

	def pull[T](field: String, query: T)(implicit writer: Writes[T]): JsObject = Json.obj("$pull" -> Json.obj(field -> query))

	def unset(field: String): JsObject = Json.obj("$unset" -> Json.obj(field -> 1))

	def inc(field: String, amount: Int) = Json.obj("$inc" -> Json.obj(field -> amount))

}
