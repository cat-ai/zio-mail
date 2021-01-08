package zio.mail

import org.jsoup.Jsoup

import java.io.{InputStream, ByteArrayOutputStream}
import javax.mail.{BodyPart, Flags, Multipart, Part, Message => JMMessage}

sealed trait MailResource {
  def folder: String
  def number: Int
  def subject: String
  def from: Seq[String]
  def recipients: Seq[String]
  def contentType: String
  def content: AnyRef
}

case class TextMailResource(folder: String,
                            number: Int,
                            subject: String,
                            from: Seq[String],
                            recipients: Seq[String],
                            contentType: String,
                            content: String,
                            flags: Flags) extends MailResource

case class MultipartMailResource(folder: String,
                                 number: Int,
                                 subject: String,
                                 from: Seq[String],
                                 recipients: Seq[String],
                                 contentType: String,
                                 content: Seq[Array[Byte]],
                                 flags: Flags) extends MailResource

object MailResource {

  def inputStreamToBytes(is: InputStream, bytes: Array[Byte]): Array[Byte] = {

    def read(nRead: Int, buffer: Array[Byte])
            (baos: ByteArrayOutputStream): ByteArrayOutputStream =
      if (nRead > 0) {
        baos.write(buffer, 0, nRead)
        read(is.read(buffer, 0, buffer.length), buffer)(baos)
      } else baos

    read(is.read(bytes, 0, bytes.length), bytes)(new ByteArrayOutputStream).toByteArray
  }

  def fromMessage(folder: String, msg: JMMessage): MailResource =
    if (msg.isMimeType("multipart/mixed"))
      multipartWithAttachments(folder, msg)
    else
      text(folder, msg)

  private def retrieveText(msg: JMMessage): String =
    msg.getContent match {
      case text: String         => text
      case multipart: Multipart =>
        (0 until multipart.getCount).foldLeft("") {
          case acc -> idx =>
            val part = multipart.getBodyPart(idx)

            part.getContent match {
              case txt: String if part.isMimeType("text/plain") => acc ++ txt
              case txt: String if part.isMimeType("text/html")  => acc ++ (Jsoup.parse(txt).text())
              case _                                                       => acc
            }
        }
    }

  private def retrieveAttachments(msg: JMMessage): List[InputStream] = {

    def retrieveFromBodyPart(part: BodyPart)
                            (acc: List[InputStream]): List[InputStream] =
      part.getContent match {
        case multipart: Multipart       => (0 until multipart.getCount).foldLeft(acc) {
          case acc -> idx => retrieveFromBodyPart(multipart.getBodyPart(idx))(Nil) ::: acc
        }

        case _: InputStream | _: String =>
          Option(part.getDisposition)
            .filter(Part.ATTACHMENT.equalsIgnoreCase)
            .orElse(Option(part.getFileName))
            .filterNot(_.isBlank)
            .fold(acc)(_ => part.getInputStream :: acc)
        case _                          => acc
      }

    msg.getContent match {
      case multipart: Multipart => (0 until multipart.getCount).foldLeft(List.empty[InputStream]) {
        case acc -> idx => retrieveFromBodyPart(multipart.getBodyPart(idx))(Nil) ::: acc
      }

      case _                    => Nil
    }
  }

  def multipartWithAttachments(folder: String,
                               message: JMMessage): MailResource =
    MultipartMailResource(folder,
      message.getMessageNumber,
      message.getSubject,
      message.getFrom.toVector.map(_.toString),
      message.getAllRecipients.toVector.map(_.toString),
      message.getContentType,
      retrieveAttachments(message).toVector.map(inputStreamToBytes(_, new Array[Byte](1 << 14))),
      message.getFlags)

  def text(folder: String,
           message: JMMessage): MailResource =
    TextMailResource(folder,
      message.getMessageNumber,
      message.getSubject,
      message.getFrom.toVector.map(_.toString),
      message.getAllRecipients.toVector.map(_.toString),
      message.getContentType,
      retrieveText(message),
      message.getFlags)
}