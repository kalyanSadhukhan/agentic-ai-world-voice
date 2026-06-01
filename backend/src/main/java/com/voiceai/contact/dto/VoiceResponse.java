package com.voiceai.contact.dto;

public class VoiceResponse {
    private String userText;
    private String text;
    private String audio;
    private boolean endCall;
    private String sessionId;

    public VoiceResponse() {}

    public VoiceResponse(String userText, String text, String audio, boolean endCall, String sessionId) {
        this.userText = userText;
        this.text = text;
        this.audio = audio;
        this.endCall = endCall;
        this.sessionId = sessionId;
    }

    public String getUserText() {
        return userText;
    }

    public void setUserText(String userText) {
        this.userText = userText;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getAudio() {
        return audio;
    }

    public void setAudio(String audio) {
        this.audio = audio;
    }

    public boolean isEndCall() {
        return endCall;
    }

    public void setEndCall(boolean endCall) {
        this.endCall = endCall;
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}
