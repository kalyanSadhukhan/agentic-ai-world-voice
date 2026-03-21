package com.voiceai.contact.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class ContactFormRequest {
    @NotBlank(message = "Name cannot be empty")
    private String name;

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email cannot be empty")
    private String email;

    @NotBlank(message = "Company cannot be empty")
    private String company;

    private String message;
    
    // Internal property for formatting
    private String submittedFrom = "Homepage Contact Form";

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getSubmittedFrom() { return submittedFrom; }
    public void setSubmittedFrom(String submittedFrom) { this.submittedFrom = submittedFrom; }
}
