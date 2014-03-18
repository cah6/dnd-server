package core

import org.joda.time.DateTime

/**
  * Base model for `temporal` documents.
  *
  * @author      Pedro De Almeida (almeidap)
  */
trait TemporalModel extends IdentifiableModel {
	 var created: Option[DateTime]
	 var updated: Option[DateTime]
}
