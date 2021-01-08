package zio.mail

import java.io.IOException

case class SessionConnectionError(message: String, cause: Throwable) extends IOException(message, cause)

case class FolderNotFoundError(message: String) extends IOException(message)

case class RecipientNotFoundError(message: String) extends IOException(message)

case class ProtocolRestrictionError(message: String) extends IOException