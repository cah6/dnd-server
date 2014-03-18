package core

/**
 * Trait for service exceptions.
 *
 * @author  Pedro De Almeida (almeidap)
 */
trait ServiceException extends Exception {
	val message: String
	val nestedException: Throwable
}
