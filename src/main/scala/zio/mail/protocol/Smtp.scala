package zio.mail.protocol

import zio._
import zio.blocking._
import zio.mail._
import zio.stream._

import java.io.IOException
import javax.mail.{Session => JMSession, Transport}

import Smtp.Session
import ZMail.Content

/**
 * The Simple Mail Transfer Protocol (SMTP) is a communication protocol for electronic mail transmission
 * SMTP only for sending messages to a mail
 * */
final class Smtp(session: Session,
                 override val settings: SmtpSettings) extends ZMail[Session] {

  override def liftUnsafe[U](f: Session => U): ZIO[Blocking, IOException, U] = effectBlockingIO(f(session))

  override def send(zMessage: ZMessage): ZIO[Blocking, IOException, Unit] =
    (ZMessage.asTextMessageZio(settings.credentials.user, zMessage, session) >>= {
      message => effectBlockingIO(Transport.send(message)).refineToOrDie[IOException]
    }).refineToOrDie[IOException]

  override def send[R <: Blocking](zMessage: ZMessage,
                                   content: ZStream[R, Throwable, Content]): ZIO[R, IOException, Unit] = ???

  /**
   * You can't use SMTP to read email
   * */
  override def read(store: ZMessageStore): ZStream[Blocking, IOException, MailResource] =
    ZStream.fail(ProtocolRestrictionError("SMTP does not allow reading"))

  /**
   * You can't use SMTP to delete email
   * */
  override def remove(store: ZMessageStore): ZStream[Blocking, IOException, Int] =
    ZStream.fail(ProtocolRestrictionError("SMTP does not allow deleting"))
}

object Smtp extends MailProtocol[JMSession, SmtpSettings] {
  type Session = JMSession

  def connect(settings: SmtpSettings): ZManaged[Blocking, SessionConnectionError, ZMail[Session]] = {

    val session = JMSession.getInstance(settings.toProperties, authenticator(settings))
    ZManaged.make(
      effectBlocking(new Smtp(session, settings)).mapError(
        SessionConnectionError(s"Failed to connect to server ${settings.host}:${settings.port} as a ${settings.credentials.user}", _)
      )
    ) {
      _.liftUnsafe(_.getTransport.close()).ignore >>= { _ =>
        effectBlocking(session.getTransport.close()).whenM(URIO(session.getTransport.isConnected)).ignore
      }
    }
  }
}