package core

import reactivemongo.bson.BSONObjectID

/**
 * Base model for `identifiable` documents.
 *
 * @author      Pedro De Almeida (almeidap)
 */
trait IdentifiableModel {
	var _id: Option[BSONObjectID]

	def identify: String = _id.map(value => value.stringify).getOrElse("")
}