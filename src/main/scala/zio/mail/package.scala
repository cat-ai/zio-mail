package zio

import zio.blocking.Blocking
import zio.mail.ZMail.Content
import zio.mail.protocol._
import zio.stream.ZStream

import java.io.IOException
import javax.mail.internet.{InternetAddress, MimeMessage}

package object mail {

  type SMTP = Has[ZMail[Smtp.Session]]

  type POP3 = Has[ZMail[Pop3.Store]]

  type IMAP = Has[ZMail[Imap.Store]]

  object POP3 {

    def liftUnsafe[U](f: Pop3.Store => U): ZIO[POP3 with Blocking, IOException, U] =
      ZIO.accessM(_.get.liftUnsafe(f))

    def read(store: ZMessageStore): ZStream[POP3 with Blocking, IOException, MailResource] =
      ZStream.accessStream(_.get.read(store))

    def send(zMessage: ZMessage): ZIO[POP3 with Blocking, IOException, Unit] =
      ZIO.accessM(_.get.send(zMessage))

    def send[R <: Blocking](zMessage: ZMessage,
                            content: ZIO[R, Throwable, Vector[Content]]): ZIO[POP3 with R, IOException, Unit] =
      ZIO.accessM(_.get.send(zMessage, content))

    def remove(store: ZMessageStore): ZStream[POP3 with Blocking, IOException, Int] =
      ZStream.accessStream(_.get.remove(store))
  }

  object IMAP {

    def liftUnsafe[U](f: Imap.Store => U): ZIO[IMAP with Blocking, IOException, U] =
      ZIO.accessM(_.get.liftUnsafe(f))

    def read(store: ZMessageStore): ZStream[IMAP with Blocking, IOException, MailResource] =
      ZStream.accessStream(_.get.read(store))

    def send(zMessage: ZMessage): ZIO[IMAP with Blocking, IOException, Unit] =
      ZIO.accessM(_.get.send(zMessage))

    def send[R <: Blocking](zMessage: ZMessage,
                            content: ZIO[R, Throwable, Vector[Content]]): ZIO[IMAP with R, IOException, Unit] =
      ZIO.accessM(_.get.send(zMessage, content))

    def remove(store: ZMessageStore): ZStream[IMAP with Blocking, IOException, Int] =
      ZStream.accessStream(_.get.remove(store))
  }

  object SMTP {

    def liftUnsafe[U](f: Smtp.Session => U): ZIO[SMTP with Blocking, IOException, U] =
      ZIO.accessM(_.get.liftUnsafe(f))

    def read(store: ZMessageStore): ZStream[SMTP with Blocking, IOException, MailResource] =
      ZStream.accessStream(_.get.read(store))

    def send(zMessage: ZMessage): ZIO[SMTP with Blocking, IOException, Unit] =
      ZIO.accessM(_.get.send(zMessage))

    def send[R <: Blocking](zMessage: ZMessage,
                            content: ZIO[R, Throwable, Vector[Content]]): ZIO[SMTP with R, IOException, Unit] =
      ZIO.accessM(_.get.send(zMessage, content))

    def remove(store: ZMessageStore): ZStream[SMTP with Blocking, IOException, Int] =
      ZStream.accessStream(_.get.remove(store))
  }

  object syntax {

    implicit class StringToInternetAddressOps(username: String) {

      def `@`(domain: String): InternetAddress =
        new InternetAddress(s"$username@$domain")
    }

    implicit class InternetAddressAsZMessageStore(folder: String) {

      def / : ZMessageStore =
        ZMessageStoreDefault(folder)

      def / (from: String): ZMessageStore =
        ZMessageStoreSender(folder, from :: Nil)

      def / (from: InternetAddress): ZMessageStore =
        ZMessageStoreSender(folder, from.getAddress :: Nil)

      def / (recipinents: Seq[String]): ZMessageStore =
        ZMessageStoreSender(folder, recipinents)

      def / (recipinents: Array[InternetAddress]): ZMessageStore =
        ZMessageStoreSender(folder, recipinents map(_.getAddress))
    }
  }


  def pop3(settings: Pop3Settings): ZLayer[Blocking, SessionConnectionError, POP3] =
    ZLayer.fromManaged(Pop3.connect(settings))

  def imap(settings: ImapSettings): ZLayer[Blocking, SessionConnectionError, IMAP] =
    ZLayer.fromManaged(Imap.connect(settings))

  def smtp(settings: SmtpSettings): ZLayer[Blocking, SessionConnectionError, SMTP] =
    ZLayer.fromManaged(Smtp.connect(settings))

}
