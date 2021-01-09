package zio.mail

import zio.ZIO
import zio.blocking.{Blocking, _}

import java.io.IOException
import javax.mail.internet.{MimeBodyPart, MimeMessage, MimeMultipart}
import javax.mail.{Message, Part, Session}

case object Loader

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

  def asMessageZio(from: String,
                   zMessage: ZMessage,
                   session: Session): ZIO[Blocking, IOException, Message] =
    ZIO.succeed(new MimeMessage(session))
      .tap(msg => effectBlockingIO(msg.setFrom(from)))
      .tap(msg => if (zMessage.subject.nonEmpty) effectBlockingIO(msg.setSubject(zMessage.subject)) else ZIO.unit)
      .tap {
        msg => if (zMessage.recipients.nonEmpty)
          effectBlockingIO(msg.setRecipients(Message.RecipientType.TO, zMessage.recipients.mkString(",")))
        else
          ZIO.fail(RecipientNotFoundError("Empty recipients"))
      } tap {
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
    }

  def asTextMessageZio(from: String,
                       zMessage: ZMessage,
                       session: Session): ZIO[Blocking, IOException, Message] =
    asMessageZio(from, zMessage, session) >>= {
        msg =>
          (ZIO.succeed(new MimeBodyPart)
            .tap(messageBodyPart => effectBlockingIO(messageBodyPart.setText(zMessage.text))) >>= {
              messageBodyPart =>
                ZIO.succeed(new MimeMultipart).tap(multipart => effectBlockingIO(multipart.addBodyPart(messageBodyPart)))
          }).tap(content => effectBlockingIO(msg.setContent(content)))
            .as(msg)
     }

  def asHtmlTextMessageWithAttachZio(text: String,
                                     message: Message,
                                     content: Vector[ZMail.Content]): ZIO[Blocking, IOException, Message] = {
    ZIO.succeed(message) tap {
      msg =>
        ZIO.succeed(new MimeBodyPart) tap {
          messageBodyPart => effectBlockingIO(messageBodyPart.setContent(text, "text/html"))
        } tap {
          messageBodyPart =>
            ZIO.succeed(new MimeMultipart) tap {
              multipart => effectBlockingIO(multipart.addBodyPart(messageBodyPart))
            } >>= {
              multipart =>
                (ZIO.succeed(new MimeBodyPart) tap {
                  imagePart =>
                    ZIO.succeed(content) >>= {
                      msgContent => effectBlockingIO {
                                       msgContent foreach {
                                         case cid -> zPath =>
                                           effectBlockingIO(imagePart.setHeader("Content-ID", s"<$cid>")) >>= {
                                             _ =>
                                               effectBlockingIO(imagePart.setDisposition(Part.INLINE)) tap {
                                                 _ => effectBlockingIO(imagePart.attachFile(s"${zPath.filename}"))
                                               }
                                           } >>= (_ => effectBlockingIO(multipart.addBodyPart(imagePart)))
                                       }
                                     }
                    }
                }).as(multipart)
            } >>= { multipart => effectBlockingIO(msg.setContent(multipart)) }
        }
    }
  }
}