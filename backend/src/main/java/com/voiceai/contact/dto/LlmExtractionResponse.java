package com.voiceai.contact.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LlmExtractionResponse {
    private String name;
    private String department;
    private String date;
    private String time;
    private Boolean isConfirming;
    private Boolean isOutOfScope;
    private Boolean isQuerying;
    private String reply;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public Boolean getIsConfirming() { return isConfirming; }
    public void setIsConfirming(Boolean isConfirming) { this.isConfirming = isConfirming; }

    public Boolean getIsOutOfScope() { return isOutOfScope; }
    public void setIsOutOfScope(Boolean isOutOfScope) { this.isOutOfScope = isOutOfScope; }

    public Boolean getIsQuerying() { return isQuerying; }
    public void setIsQuerying(Boolean isQuerying) { this.isQuerying = isQuerying; }

    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
}
