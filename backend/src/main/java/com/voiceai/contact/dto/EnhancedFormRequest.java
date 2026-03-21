package com.voiceai.contact.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class EnhancedFormRequest extends ContactFormRequest {

    @NotBlank(message = "Primary goal is required")
    private String primaryGoal;

    private List<String> agentTypes;
    
    public EnhancedFormRequest() {
        super();
        this.setSubmittedFrom("Enhanced Inquiry Form");
    }

    public String getPrimaryGoal() { return primaryGoal; }
    public void setPrimaryGoal(String primaryGoal) { this.primaryGoal = primaryGoal; }

    public List<String> getAgentTypes() { return agentTypes; }
    public void setAgentTypes(List<String> agentTypes) { this.agentTypes = agentTypes; }
}
