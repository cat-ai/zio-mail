package zio.mail

import java.util.Properties

case class MailCredentials(user: String, password: String)

trait ZMailSettings {

  def credentials: MailCredentials

  def port: Int

  def host: String

  def ssl: Boolean

  def startTls: Boolean

  def toProperties: Properties
}

/**
 * A POP3 server listens on well-known port number 110 for service requests.
 * Encrypted communication for POP3 is either requested after protocol initiation, using the STLS command, if supported, or by POP3S,
 * which connects to the server using Transport Layer Security (TLS) or Secure Sockets Layer (SSL) on well-known TCP port number 995.
 * */
final case class Pop3Settings(override val credentials: MailCredentials,
                              override val port: Int,
                              override val host: String,
                              override val ssl: Boolean,
                              override val startTls: Boolean = false,
                              socketFactoryPort: Int = 110,
                              socketFactoryClass: String = "javax.net.ssl.SSLSocketFactory") extends ZMailSettings {

  override def toProperties: Properties = {
    val props = new Properties

    props.put("mail.store.protocol", "pop3")
    props.put("mail.pop3.host", host)
    props.put("mail.pop3.user", credentials.user)

    if (ssl) props.put("mail.pop3.ssl.enable", ssl)
    if (startTls) props.put("mail.pop3.starttls.enable", startTls)

    props.put("mail.pop3.socketFactory", socketFactoryPort)
    props.put("mail.pop3.socketFactory.class", socketFactoryClass)
    props.put("mail.pop3.port", port)

    props
  }
}

/**
 * SMTP is typically submit outgoing email to the mail server on port 587 or 465
 * */
final case class SmtpSettings(override val credentials: MailCredentials,
                              override val port: Int,
                              override val host: String,
                              override val ssl: Boolean,
                              override val startTls: Boolean = false,
                              socketFactoryPort: Int = 587,
                              socketFactoryClass: String = "javax.net.ssl.SSLSocketFactory") extends ZMailSettings {

  override def toProperties: Properties = {
    val props = new Properties
    props.put("mail.smtp.host", host)
    props.put("mail.smtp.port", port)

    if (ssl) props.put("mail.smtp.auth", ssl)
    if (startTls) props.put("mail.smtp.starttls.enable", startTls)

    props.put("mail.smtp.socketFactory.port", socketFactoryPort)
    props.put("mail.smtp.socketFactory.class", socketFactoryClass)

    props
  }
}

/**
 * An IMAP server typically listens on well-known port 143, while IMAP over SSL/TLS (IMAPS) uses 993
 * */
final case class ImapSettings(override val credentials: MailCredentials,
                              override val port: Int,
                              override val host: String,
                              override val ssl: Boolean,
                              override val startTls: Boolean = false,
                              socketFactoryClass: String = "javax.net.ssl.SSLSocketFactory",
                              uidplus: Boolean = false) extends ZMailSettings {

  override def toProperties: Properties = {
    val props = new Properties

    props.put("mail.imap.host", host)
    props.put("mail.imap.port", port.toString)

    if (ssl) {
      props.put("mail.smtp.auth", ssl)
      props.put("mail.imap.ssl.enable", port.toString)
    }

    if (startTls) props.put("mail.smtp.starttls.enable", startTls.toString)

    props
  }
}