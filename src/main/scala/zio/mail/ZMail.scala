package zio.mail

import zio.ZIO
import zio.blocking.Blocking
import zio.stream.ZStream
import zio.nio.core.file.Path

import java.io.IOException

import ZMail._

/**
 * All methods are lift into ZIO {@link zio.ZIO} or ZStream {@link zio.stream.ZStream}
 * */
trait ZMail[+A] {

  /**
   * */
  def settings: ZMailSettings

  /***
   * Lifts unsafe blocking IO function into a ZIO pure value
   *
   * @param f unsafe function to lift
   * @tparam U u
   */
  def liftUnsafe[U](f: A => U): ZIO[Blocking, IOException, U]

  /***
   * Read a messages by using stream (protocol specific)
   *
   * @param store
   * @tparam MailResource
   */
  def read(store: ZMessageStore): ZStream[Blocking, IOException, MailResource]

  /***
   * Transports a message to recipients (protocol specific)
   *
   * @param zMessage
   */
  def send(zMessage: ZMessage): ZIO[Blocking, IOException, Unit]

  /***
   * Transports a message with attachments to recipients (protocol specific)
   *
   * @param zMessage
   * @param ZIO[R, Throwable, Vector[Content]]
   * @tparam R Blocking environment of the content {@link ZMail.Content}
   */
  def send[R <: Blocking](zMessage: ZMessage,
                          content: ZIO[R, Throwable, Vector[Content]]): ZIO[R, IOException, Unit]

  /***
   * Deletes a messages from sender (protocol specific)
   *
   * @param store message store
   * @tparam returns message number
   */
  def remove(store: ZMessageStore): ZStream[Blocking, IOException, Int]
}

object ZMail {

  type ZPath   = Path
  type CID     = String
  type Content = (CID, ZPath)
}