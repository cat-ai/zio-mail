package zio.mail.protocol

import zio._
import zio.blocking._
import zio.mail._
import zio.stream._

import java.io.IOException
import javax.mail.search.FlagTerm
import javax.mail.{Flags, Folder, Session => JMSession, Store => JMStore}

import ZMail.Content
import Imap.Store

/**
 * Internet standard protocol used by email clients to retrieve email messages from a mail server over a TCP/IP connection.
 * IMAP generally leave messages on the server until the user explicitly deletes them
 * IMAP offers access to the mail storage. Clients may store local copies of the messages, but these are considered to be a temporary cache.
 * */
final class Imap(store: Store,
                 override val settings: ImapSettings) extends ZMail[Store] {

  override def liftUnsafe[U](f: Store => U): ZIO[Blocking, IOException, U] =
    effectBlockingIO(f(store)).refineToOrDie[IOException]

  override def read(messageStore: ZMessageStore): ZStream[Blocking, IOException, MailResource] =
    (ZStream.fromEffect {
      liftUnsafe(_ getFolder messageStore.folder) tap(f => effectBlockingIO(f.open(Folder.READ_WRITE)))
    } >>= {
      folder =>
        messageStore match {
          case ZMessageStoreDefault(_)          => Stream.fromIterable(folder.getMessages.toVector)
          case ZMessageStoreSender(_, senders)  => Stream.fromIterable {
                                                     for {
                                                       msg    <- folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false)).toVector
                                                       from   <- msg.getFrom.toVector
                                                       sender <- senders.toVector if sender == from.toString
                                                     } yield msg
                                                   }
        }
    }) map(MailResource.fromMessage(messageStore.folder, _))

  override def remove(messageStore: ZMessageStore): ZStream[Blocking, IOException, Int] =
    (ZStream.fromEffect {
      liftUnsafe(_ getFolder messageStore.folder) tap(f => effectBlockingIO(f.open(Folder.READ_WRITE)))
    } >>= {
      folder =>
        messageStore match {
          case ZMessageStoreDefault(_)          => Stream.fromIterable(folder.getMessages.toVector.map(_ -> folder))
          case ZMessageStoreSender(_, senders)  => Stream.fromIterable {
                                                    for {
                                                       msg    <- folder.getMessages.toVector
                                                       from   <- msg.getFrom.toVector
                                                       sender <- senders.toVector if sender == from.toString
                                                     } yield msg -> folder
                                                   }
        }
    }) tap {
      case (msg, folder) => effectBlockingIO(msg.setFlag(Flags.Flag.DELETED, true)) >>= {
        _ =>
          if (settings.uidplus) effectBlockingIO(folder.expunge)
          else ZIO.unit
      }
    } map { case (msg, _) => msg.getMessageNumber }

  override def send(zMessage: ZMessage): ZIO[Blocking, IOException, Unit] =
    ZIO.fail(ProtocolRestrictionError("IMAP is incoming protocol used to retrieve messages from email servers"))

  override def send[R <: Blocking](zMessage: ZMessage,
                                   content: ZStream[R, Throwable, Content]): ZIO[R, IOException, Unit] =
    ZIO.fail(ProtocolRestrictionError("IMAP is incoming protocol used to retrieve messages from email servers"))
}

object Imap extends MailProtocol[JMStore, ImapSettings] {
  type Store = JMStore

  def connect(settings: ImapSettings): ZManaged[Blocking, SessionConnectionError, ZMail[Store]] = {
    val session = JMSession.getDefaultInstance(settings.toProperties, authenticator(settings))
    ZManaged.make(
      effectBlocking {
        val store = session.getStore
        store.connect(settings.host, settings.credentials.user, settings.credentials.password)

        new Imap(store, settings)
      }.mapError(
        SessionConnectionError(s"Failed to connect to server ${settings.host}:${settings.port} as a ${settings.credentials.user}", _)
      )
    ) {
      _.liftUnsafe(_.close()).ignore >>= { _ =>
        effectBlocking(session.getStore.close()).whenM(URIO(session.getStore.isConnected)).ignore
      }
    }
  }
}