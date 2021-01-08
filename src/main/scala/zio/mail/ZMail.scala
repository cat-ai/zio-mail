package zio.mail

import zio.ZIO
import zio.blocking.Blocking
import zio.stream.ZStream
import zio.nio.core.file.Path

import java.io.IOException

import ZMail._

/**
 * All methods exposed are lift into ZIO {@link zio.ZIO} or ZStream {@link zio.stream.ZStream}
 * */
trait ZMail[+A] {

  def settings: ZMailSettings

  def liftUnsafe[U](f: A => U): ZIO[Blocking, IOException, U]

  def read(store: ZMessageStore): ZStream[Blocking, IOException, MailResource]

  def send(zMessage: ZMessage): ZIO[Blocking, IOException, Unit]

  def send[R <: Blocking](zMessage: ZMessage,
                          content: ZStream[R, Throwable, Content]): ZIO[R, IOException, Unit]

  def remove(store: ZMessageStore): ZStream[Blocking, IOException, Int]
}

object ZMail {

  type ZPath   = Path
  type Content = (String, ZPath)
}