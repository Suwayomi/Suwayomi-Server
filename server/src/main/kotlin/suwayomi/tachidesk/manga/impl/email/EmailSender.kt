package suwayomi.tachidesk.manga.impl.email

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.activation.DataHandler
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.util.ByteArrayDataSource
import suwayomi.tachidesk.manga.impl.util.SecretBox
import suwayomi.tachidesk.server.serverConfig
import java.util.Properties

/**
 * Sends emails over SMTP using ServerConfig credentials. Wraps the
 * jakarta.mail Session/Transport API so the rest of the codebase can
 * fire-and-forget without dealing with low-level mail details.
 */
object EmailSender {
    private val logger = KotlinLogging.logger {}

    data class Attachment(
        val data: ByteArray,
        val filename: String,
        val mime: String,
    )

    /**
     * Build a Session from current ServerConfig values. Throws when
     * required fields are blank so callers can surface the error to the
     * UI. Reads the SMTP password through SecretBox.decrypt().
     */
    private fun session(): Session {
        val host = serverConfig.smtpHost.value
        val port = serverConfig.smtpPort.value
        val tls = serverConfig.smtpUseStartTls.value
        val username = serverConfig.smtpUsername.value
        val passwordEnc = serverConfig.smtpPasswordEncrypted.value
        require(host.isNotBlank()) { "SMTP host not configured" }
        require(username.isNotBlank()) { "SMTP username not configured" }
        require(passwordEnc.isNotBlank()) { "SMTP password not configured" }

        val password = SecretBox.decrypt(passwordEnc)
        val props =
            Properties().apply {
                put("mail.smtp.host", host)
                put("mail.smtp.port", port.toString())
                put("mail.smtp.auth", "true")
                if (tls) {
                    put("mail.smtp.starttls.enable", "true")
                    put("mail.smtp.starttls.required", "true")
                }
                // Sensible defaults for connect/read timeouts (10s/30s).
                put("mail.smtp.connectiontimeout", "10000")
                put("mail.smtp.timeout", "30000")
                put("mail.smtp.writetimeout", "30000")
            }
        val auth =
            object : Authenticator() {
                override fun getPasswordAuthentication() = PasswordAuthentication(username, password)
            }
        return Session.getInstance(props, auth)
    }

    /** Sends a plain text + optional attachments email. Throws on failure. */
    fun send(
        toEmails: List<String>,
        subject: String,
        bodyText: String,
        attachments: List<Attachment> = emptyList(),
    ) {
        require(toEmails.any { it.isNotBlank() }) { "No recipient address" }
        val fromAddress =
            serverConfig.smtpFromEmail.value
                .takeIf { it.isNotBlank() }
                ?: serverConfig.smtpUsername.value

        val session = session()
        val msg =
            MimeMessage(session).apply {
                setFrom(InternetAddress(fromAddress))
                setRecipients(
                    Message.RecipientType.TO,
                    toEmails
                        .filter { it.isNotBlank() }
                        .map { InternetAddress(it) }
                        .toTypedArray(),
                )
                this.subject = subject
            }

        if (attachments.isEmpty()) {
            msg.setText(bodyText, "UTF-8")
        } else {
            val multipart = MimeMultipart()
            val textPart =
                MimeBodyPart().apply {
                    setText(bodyText, "UTF-8")
                }
            multipart.addBodyPart(textPart)
            attachments.forEach { att ->
                val ds = ByteArrayDataSource(att.data, att.mime)
                val part =
                    MimeBodyPart().apply {
                        dataHandler = DataHandler(ds)
                        fileName = att.filename
                    }
                multipart.addBodyPart(part)
            }
            msg.setContent(multipart)
        }
        Transport.send(msg)
        logger.info {
            "SMTP send ok to=${toEmails.joinToString(",")} subject='$subject' attachments=${attachments.size}"
        }
    }
}
