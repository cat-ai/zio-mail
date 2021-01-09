package zio.mail.protocol

import zio._
import zio.blocking._
import zio.mail._
import zio.stream._

import java.io.IOException
import javax.mail.{Flags, Folder, Session => JMSession, Store => JMStore}
import ZMail._

import Pop3.Store

/**
 * Post Office Protocol (POP) is an application-layer Internet standard protocol used by e-mail clients to retrieve e-mail
 * from a mail server via TCP/IP connection
 * */
final class Pop3(store: Store,
                 override val settings: Pop3Settings) extends ZMail[Store] {

  override def liftUnsafe[U](f: Store => U): ZIO[Blocking, IOException, U] =
    effectBlockingIO(f(store)).refineToOrDie[IOException]

  override def read(store: ZMessageStore): ZStream[Blocking, IOException, MailResource] =
    (ZStream.fromEffect {
      store.folder match {
        case inbox @ "INBOX" => liftUnsafe(_ getFolder inbox) tap(f => effectBlockingIO(f.open(Folder.READ_ONLY)))
        case _               => ZIO.fail(FolderNotFoundError("POP3 protocol allow access only to the 'INBOX'"))
      }
    } >>= {
      folder =>
        store match {
          case ZMessageStoreDefault(_)          => Stream.fromIterable(folder.getMessages.toVector)
          case ZMessageStoreSender(_, senders)  => Stream.fromIterable {
                                                     for {
                                                       sender  <- senders.toVector
                                                       message <- folder.getMessages.toVector
                                                       from    <- message.getFrom.toVector if from.toString contains sender
                                                     } yield message
                                                   }
        }
    }) map(MailResource.fromMessage(store.folder, _))

  override def remove(store: ZMessageStore): ZStream[Blocking, IOException, Int] =
    (ZStream.fromEffect {
      store.folder match {
        case inbox @ "INBOX" => liftUnsafe(_ getFolder inbox) tap(f => effectBlockingIO(f.open(Folder.READ_WRITE)))
        case _               => ZIO.fail(FolderNotFoundError("POP3 protocol allow access only to the 'INBOX'"))
      }
    } >>= {
      folder =>
        store match {
          case ZMessageStoreDefault(_)          => Stream.fromIterable(folder.getMessages.toVector)
          case ZMessageStoreSender(_, senders)  => Stream.fromIterable {
                                                     for {
                                                       msg    <- folder.getMessages.toVector
                                                       from   <- msg.getFrom.toVector
                                                       sender <- senders.toVector if sender == from.toString
                                                     } yield msg
                                                   }
        }
    }) tap(msg => effectBlockingIO(msg.setFlag(Flags.Flag.DELETED, true))) map(_.getMessageNumber)

  override def send(zMessage: ZMessage): ZIO[Blocking, IOException, Unit] =
    ZIO.fail(ProtocolRestrictionError("POP is incoming protocol used to retrieve messages from email servers"))

  override def send[R <: Blocking](zMessage: ZMessage,
                                   content: ZStream[R, Throwable, Content]): ZIO[R, IOException, Unit] =
    ZIO.fail(ProtocolRestrictionError("POP is incoming protocol used to retrieve messages from email servers"))
}

object Pop3 extends MailProtocol[JMStore, Pop3Settings] {
  type Store = JMStore

  def connect(pop3Settings: Pop3Settings): ZManaged[Blocking, SessionConnectionError, ZMail[Store]] = {
    val session = JMSession.getInstance(pop3Settings.toProperties, authenticator(pop3Settings))
    ZManaged.make(
      effectBlocking {
        val store = session.getStore("pop3")
        store.connect(pop3Settings.credentials.user, pop3Settings.credentials.password)

        new Pop3(store, pop3Settings)
      }.mapError(
        SessionConnectionError(s"Failed to connect to server ${pop3Settings.host}:${pop3Settings.port} as a ${pop3Settings.credentials.user}", _)
      )
    ) {
        _.liftUnsafe(_.close()).ignore >>= { _ =>
          effectBlocking(session.getStore.close()).whenM(URIO(session.getStore.isConnected)).ignore
        }
    }
  }
}