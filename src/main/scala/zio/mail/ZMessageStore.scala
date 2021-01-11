package zio.mail


/**
 * Store for storing and retrieving messages folders and senders
 * */
sealed trait ZMessageStore {
  def folder: String
  def from: Seq[String]
}

case class ZMessageStoreDefault(folder: String = "INBOX") extends ZMessageStore {
  override def from: Seq[String] = Nil
}

case class ZMessageStoreSender(folder: String = "INBOX",
                               from: Seq[String]) extends ZMessageStore