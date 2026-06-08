package com.voiceai.contact.service;

import com.voiceai.contact.dto.ContactFormRequest;
import com.voiceai.contact.dto.EnhancedFormRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import java.io.UnsupportedEncodingException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String senderEmail; // Authentication email used to send

    @Value("${RECEIVER_EMAIL:${spring.mail.username:}}")
    private String toEmail; // Inbox receiving the notifications

    @Value("${RESEND_API_KEY:}")
    private String resendApiKey;

    public void sendContactEmail(ContactFormRequest request) {
        String safeName = (request.getName() != null && !request.getName().isEmpty()) ? request.getName() : "Unknown";
        String safeForm = (request.getSubmittedFrom() != null && !request.getSubmittedFrom().isEmpty()) ? request.getSubmittedFrom() : "Website";
        String subject = "New Inquiry from " + safeName + " (" + safeForm + ")";
        String htmlBody = buildHtmlEmailBody(request.getName(), request.getEmail(), null, request.getCompany(), null, null, request.getMessage(), request.getSubmittedFrom());
        String replyTo = (request.getEmail() != null && request.getEmail().contains("@") && request.getEmail().contains(".")) ? request.getEmail() : null;

        if (resendApiKey != null && !resendApiKey.trim().isEmpty()) {
            sendViaResend(subject, htmlBody, replyTo);
            return;
        }

        // SMTP fallback
        if (mailSender == null) {
            throw new RuntimeException("Neither Resend API Key nor JavaMailSender is configured.");
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            String senderName = (request.getName() != null && !request.getName().trim().isEmpty())
                    ? request.getName().trim()
                    : "User";
            if (senderName.length() > 50) {
                senderName = senderName.substring(0, 50);
            }
            helper.setFrom(new InternetAddress(senderEmail, senderName + " via Application"));
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            if (replyTo != null) {
                helper.setReplyTo(replyTo);
            }
            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new RuntimeException("Failed to send contact email via SMTP", e);
        }
    }

    public void sendEnhancedInquiryEmail(EnhancedFormRequest request) {
        String safeName = (request.getName() != null && !request.getName().isEmpty()) ? request.getName() : "Unknown";
        String safeForm = (request.getSubmittedFrom() != null && !request.getSubmittedFrom().isEmpty()) ? request.getSubmittedFrom() : "Website";
        String subject = "New Inquiry from " + safeName + " (" + safeForm + ")";
        String agentTypes = (request.getAgentTypes() != null && !request.getAgentTypes().isEmpty()) 
                ? String.join(", ", request.getAgentTypes()) 
                : null;
        String htmlBody = buildHtmlEmailBody(request.getName(), request.getEmail(), null, request.getCompany(), request.getPrimaryGoal(), agentTypes, request.getMessage(), request.getSubmittedFrom());
        String replyTo = (request.getEmail() != null && request.getEmail().contains("@") && request.getEmail().contains(".")) ? request.getEmail() : null;

        if (resendApiKey != null && !resendApiKey.trim().isEmpty()) {
            sendViaResend(subject, htmlBody, replyTo);
            return;
        }

        // SMTP fallback
        if (mailSender == null) {
            throw new RuntimeException("Neither Resend API Key nor JavaMailSender is configured.");
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            String senderName = (request.getName() != null && !request.getName().trim().isEmpty())
                    ? request.getName().trim()
                    : "User";
            if (senderName.length() > 50) {
                senderName = senderName.substring(0, 50);
            }
            helper.setFrom(new InternetAddress(senderEmail, senderName + " via Application"));
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            if (replyTo != null) {
                helper.setReplyTo(replyTo);
            }
            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new RuntimeException("Failed to send enhanced inquiry email via SMTP", e);
        }
    }

    private void sendViaResend(String subject, String htmlBody, String replyTo) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(resendApiKey);

            Map<String, Object> body = new HashMap<>();
            
            // If senderEmail is configured with a custom domain, use it. Otherwise, default to sandbox onboarding.
            String fromAddress = (senderEmail != null && senderEmail.contains("@"))
                    ? "AI Voice Agent Form <" + senderEmail + ">"
                    : "AI Voice Agent Form <onboarding@resend.dev>";
            body.put("from", fromAddress);
            
            body.put("to", Collections.singletonList(toEmail));
            body.put("subject", subject);
            body.put("html", htmlBody);
            
            if (replyTo != null) {
                body.put("reply_to", replyTo);
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.postForEntity("https://api.resend.com/emails", entity, String.class);
        } catch (Exception e) {
            throw new RuntimeException("Resend API failed to send email: " + e.getMessage(), e);
        }
    }

    private String buildHtmlEmailBody(String name, String email, String phone, String company, String primaryGoal, String agentTypes, String messageText, String submittedFrom) {
        String safeName = (name != null && !name.isEmpty()) ? name : "N/A";
        String safeEmail = (email != null && !email.isEmpty()) ? email : "N/A";
        String safePhone = (phone != null && !phone.isEmpty()) ? phone : "N/A";
        String safeCompany = (company != null && !company.isEmpty()) ? company : "N/A";
        String safePrimaryGoal = (primaryGoal != null && !primaryGoal.isEmpty()) ? primaryGoal : "N/A";
        String safeAgentTypes = (agentTypes != null && !agentTypes.isEmpty()) ? agentTypes : "N/A";
        String safeMessage = (messageText != null && !messageText.isEmpty()) ? messageText.replace("\n", "<br>") : "N/A";
        String safeSubmittedFrom = (submittedFrom != null && !submittedFrom.isEmpty()) ? submittedFrom : "N/A";

        return "<!DOCTYPE html>\n" +
               "<html>\n" +
               "<body style=\"margin:0;padding:0;background-color:#f4f6f8;font-family:Arial,sans-serif;\">\n" +
               "  <div style=\"max-width:600px;margin:20px auto;background:#ffffff;padding:24px;border-radius:8px;box-shadow:0 2px 8px rgba(0,0,0,0.05);\">\n" +
               "    \n" +
               "    <h2 style=\"margin-top:0;color:#333;\">New Contact Form Submission</h2>\n" +
               "    <hr style=\"border:none;border-top:1px solid #eee;margin:16px 0;\" />\n" +
               "\n" +
               "    <p><strong>Name:</strong> " + safeName + "</p>\n" +
               "    <p><strong>Email:</strong> " + safeEmail + "</p>\n" +
               "    <p><strong>Phone:</strong> " + safePhone + "</p>\n" +
               "    <p><strong>Company:</strong> " + safeCompany + "</p>\n" +
               "    <p><strong>Primary Goal:</strong> " + safePrimaryGoal + "</p>\n" +
               "    <p><strong>Agent Types:</strong> " + safeAgentTypes + "</p>\n" +
               "\n" +
               "    <div style=\"margin-top:20px;\">\n" +
               "      <p><strong>Message:</strong></p>\n" +
               "      <div style=\"background:#f9f9f9;padding:15px;border-radius:6px;line-height:1.5;\">\n" +
               "        " + safeMessage + "\n" +
               "      </div>\n" +
               "    </div>\n" +
               "\n" +
               "    <hr style=\"margin:20px 0;\" />\n" +
               "\n" +
               "    <p style=\"font-size:12px;color:#777;\">\n" +
               "      Submitted from: " + safeSubmittedFrom + "<br/>\n" +
               "      Timestamp: " + getCurrentTimestamp() + "\n" +
               "    </p>\n" +
               "\n" +
               "  </div>\n" +
               "</body>\n" +
               "</html>";
    }

    private String getCurrentTimestamp() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return LocalDateTime.now().format(formatter);
    }
}
