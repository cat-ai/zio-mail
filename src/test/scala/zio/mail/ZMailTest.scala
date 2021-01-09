package zio.mail

import zio.blocking.Blocking
import zio.mail.syntax._
import zio._
import zio.duration.durationInt
import zio.nio.core.file.{Path => ZPath}
import zio.test.Assertion.equalTo
import zio.test._
import zio.test.environment.TestEnvironment

import java.io.IOException

object ZMailTest extends DefaultRunnableSpec {

  val smtpSettings = SmtpSettings(
    MailCredentials("your-email@google.com", "your_password"),
    465,
    "smtp.gmail.com",
    true,
    false,
    465
  )

  val pop3Settings = Pop3Settings(
    MailCredentials("your-email@google.com", "your_password"),
    995,
    "pop.gmail.com",
    true,
    true,
    995
  )

  val Smtp: ZLayer[Blocking, TestFailure[SessionConnectionError], SMTP] = smtp(smtpSettings).mapError(TestFailure.fail)
  val Pop3: ZLayer[Blocking, TestFailure[SessionConnectionError], POP3] = pop3(pop3Settings).mapError(TestFailure.fail)

  val cid1 = "googlePicImg"
  val cid2 = "yahooPicImg"
  val pathToCid1 = ZPath("path/to/cid1File")
  val pathToCid2 = ZPath("path/to/cid1File")


  val htmlBody =
    s"""
       |<html>This message contains Google/Yahoo pics.<br>
       |The first image is a Google pic:<br>
       |<img src=\"cid:$cid1\" width=\"30%\" height=\"30%\" /><br>
       |The second one is a Yahoo pic:<br>
       |<img src=\"cid:$cid2\" width=\"30%\" height=\"30%\" /><br>
       |CYA!
       |<html>
       |""".stripMargin

  override def spec: Spec[TestEnvironment, TestFailure[IOException], TestSuccess] =
    suite("ProtocolTests")(
      testM("Send via SMTP")(
        for {
          result <- SMTP.send(
                      ZMessageTo(
                        "some-friend@google.com" :: Nil,
                        "Subject",
                        """
                          |Falax species rerum
                          |A capite ad calcem
                          |Ad futuram rei memoriam
                          |Ad perpetuam rei memoriam
                          |Amata nobis quantum amabitur nulla
                          |Amicus incommodus ab inimico non differt
                          |""".stripMargin
                      )
                    ).map(_ => true)
      } yield assert(result)(equalTo(true))
    ),
      testM("Send a message with attachment via SMTP")(
        for {
          result <- SMTP.send(
                      ZMessageToCc(
                        "best-friend@gmail.com" :: Nil,
                        "another-friend@google.com" :: "some-friend@gmail.com" :: Nil,
                        "Google and Yahoo pics",
                        htmlBody
                      ),
                      ZIO.succeed(Vector(cid1 -> pathToCid1, cid2 -> pathToCid2))
                     ).map(_ => true)
        } yield assert(result)(equalTo(true))
      ),
      testM("Read via POP3")(
        for {
          size <- POP3.read("INBOX" / ("some-friend"`@`"google.com")).runCollect.map(_.size)
        } yield assert(size)(equalTo(1))
      )
    ).provideCustomLayerShared(Pop3 ++ Smtp ++ Blocking.live) @@ TestAspect.timeout(5.minutes)
}
