package zio.mail

import zio.ZIO
import zio.blocking._

import java.io.IOException
import javax.mail.internet.{MimeBodyPart, MimeMessage, MimeMultipart}
import javax.mail.{Message, Session}

sealed trait ZMessage {

  def recipients: Seq[String]
  def carbonCopy: Seq[String]
  def blindCarbonCopy: Seq[String]
  def subject: String
  def text: String
}

case class ZMessageTo(override val recipients: Seq[String],
                      override val subject: String,
                      override val text: String = "") extends ZMessage {

  override def carbonCopy: Seq[String] = Nil
  override def blindCarbonCopy: Seq[String] = Nil
}

case class ZMessageToCc(override val recipients: Seq[String],
                        override val carbonCopy: Seq[String],
                        override val subject: String,
                        override val text: String = "") extends ZMessage {

  override def blindCarbonCopy: Seq[String] = Nil
}

case class ZMessageToCcBcc(override val recipients: Seq[String],
                           override val carbonCopy: Seq[String],
                           override val blindCarbonCopy: Seq[String],
                           override val subject: String,
                           override val text: String = "") extends ZMessage

object ZMessage {

  def asTextMessageZio(from: String,
                       zMessage: ZMessage,
                       session: Session): ZIO[Blocking, IOException, Message] =
    ZIO.succeed(new MimeMessage(session))
      .tap(msg => effectBlockingIO(msg.setFrom(from)))
      .tap {
        msg => if (zMessage.recipients.nonEmpty)
                 effectBlockingIO(msg.setRecipients(Message.RecipientType.TO, zMessage.recipients.mkString(",")))
               else
                 ZIO.fail(RecipientNotFoundError("Empty recipients"))
      }.tap {
        msg => if (zMessage.carbonCopy.nonEmpty)
                 effectBlockingIO(msg.setRecipients(Message.RecipientType.CC, zMessage.carbonCopy mkString ",")).as(zMessage) >>= {
                   zMsg =>
                     if (zMsg.blindCarbonCopy.nonEmpty)
                       effectBlockingIO(msg.setRecipients(Message.RecipientType.BCC, zMsg.blindCarbonCopy mkString ","))
                     else
                       ZIO.unit
                 }
               else
                 ZIO.unit
      }.tap {
        msg =>
          (ZIO.succeed(new MimeBodyPart)
            .tap(messageBodyPart => effectBlockingIO(messageBodyPart.setText(zMessage.text))) >>= {
              messageBodyPart =>
                ZIO.succeed(new MimeMultipart).tap(multipart => effectBlockingIO(multipart.addBodyPart(messageBodyPart)))
          }).tap(content => effectBlockingIO(msg.setContent(content)))
     }
}