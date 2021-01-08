package zio.mail.protocol

import zio.ZManaged
import zio.blocking.Blocking
import zio.mail.{ZMailSettings, ZMail}

import java.io.IOException
import javax.mail.{Authenticator, PasswordAuthentication}

trait MailProtocol[E, S <: ZMailSettings] {

  def connect(settings: S): ZManaged[Blocking, IOException, ZMail[E]]

  val authenticator: S => Authenticator = settings =>
    new Authenticator {
      override def getPasswordAuthentication: PasswordAuthentication =
        new PasswordAuthentication(settings.credentials.user, settings.credentials.password)
    }
}