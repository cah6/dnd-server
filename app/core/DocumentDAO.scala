package core

import play.api.Logger
import play.api.libs.json._
import play.modules.reactivemongo.json.collection.JSONCollection

import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson._

import org.joda.time.DateTime
import scala.concurrent.Future

/**
 * DAO for MongoDB documents.
 *
 * @author      Pedro De Almeida (almeidap)
 */
trait DocumentDAO[T <: IdentifiableModel] extends BaseDAO {

	def collection: JSONCollection = db.collection[JSONCollection](collectionName)

	def insert(document: T)(implicit writer: Writes[T]): Future[Either[ServiceException, T]] = {
		document._id = Some(BSONObjectID.generate)
		Logger.debug(s"Inserting document: [collection=$collectionName, data=$document]")
		Recover(collection.insert(document)) {
			document
		}
	}

	def find(query: JsObject = Json.obj())(implicit reader: Reads[T]): Future[List[T]] = {
		Logger.debug(s"Finding documents: [collection=$collectionName, query=$query]")
		collection.find(query).cursor[T].collect[List]()
	}

	def findById(id: String)(implicit reader: Reads[T]): Future[Option[T]] = findOne(DBQueryBuilder.id(id))

	def findById(id: BSONObjectID)(implicit reader: Reads[T]): Future[Option[T]] = findOne(DBQueryBuilder.id(id))

	def findOne(query: JsObject)(implicit reader: Reads[T]): Future[Option[T]] = {
		Logger.debug(s"Finding one: [collection=$collectionName, query=$query]")
		collection.find(query).one[T]
	}

	def update(id: String, document: T)(implicit writer: Writes[T]): Future[Either[ServiceException, T]] = {
		Logger.debug(s"Updating document: [collection=$collectionName, id=$id, document=$document]")
		Recover(collection.update(DBQueryBuilder.id(id), DBQueryBuilder.set(document))) {
			document
		}
	}

	def update(id: String, query: JsObject): Future[Either[ServiceException, JsObject]] = {
		val data = updated(query)
		Logger.debug(s"Updating by query: [collection=$collectionName, id=$id, query=$data]")
		Recover(collection.update(DBQueryBuilder.id(id), data)) {
			data
		}
	}

	def push[S](id: String, field: String, data: S)(implicit writer: Writes[S]): Future[Either[ServiceException, S]] = {
		Logger.debug(s"Pushing to document: [collection=$collectionName, id=$id, field=$field data=$data]")
		Recover(collection.update(DBQueryBuilder.id(id), DBQueryBuilder.push(field, data)
		)) {
			data
		}
	}

	def pull[S](id: String, field: String, query: S)(implicit writer: Writes[S]): Future[Either[ServiceException, Boolean]] = {
		Logger.debug(s"Pulling from document: [collection=$collectionName, id=$id, field=$field query=$query]")
		Recover(collection.update(DBQueryBuilder.id(id), DBQueryBuilder.pull(field, query))) {
			true
		}
	}

	def unset(id: String, field: String): Future[Either[ServiceException, Boolean]] = {
		Logger.debug(s"Unsetting from document: [collection=$collectionName, id=$id, field=$field]")
		Recover(collection.update(DBQueryBuilder.id(id), DBQueryBuilder.unset(field))) {
			true
		}
	}

	def remove(id: String): Future[Either[ServiceException, Boolean]] = remove(BSONObjectID(id))

	def remove(id: BSONObjectID): Future[Either[ServiceException, Boolean]] = {
		Logger.debug(s"Removing document: [collection=$collectionName, id=$id]")
		Recover(
			collection.remove(DBQueryBuilder.id(id))
		) {
			true
		}
	}

	def remove(query: JsObject, firstMatchOnly: Boolean = false): Future[Either[ServiceException, Boolean]] = {
		Logger.debug(s"Removing document(s): [collection=$collectionName, firstMatchOnly=$firstMatchOnly, query=$query]")
		Recover(
			collection.remove(query, firstMatchOnly = firstMatchOnly)
		) {
			true
		}
	}

	def updated(data: JsObject) = {
		data.validate((__ \ '$set).json.update(
			__.read[JsObject]
		)).fold(
			error => data,
			success => success
		)
	}

	// def ensureIndex(
	// 	key: List[(String, IndexType)],
	// 	name: Option[String] = None,
	// 	unique: Boolean = false,
	// 	background: Boolean = false,
	// 	dropDups: Boolean = false,
	// 	sparse: Boolean = false,
	// 	version: Option[Int] = None,
	// 	options: BSONDocument = BSONDocument()) = {
	// 	val index = Index(key, name, unique, background, dropDups, sparse, version, options)
	// 	Logger.info(s"Ensuring index: $index")
	// 	collection.indexesManager.ensure(index)
	// }
}
