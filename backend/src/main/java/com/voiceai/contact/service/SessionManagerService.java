package com.voiceai.contact.service;

import com.voiceai.contact.model.SessionState;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionManagerService {

    private final ConcurrentHashMap<String, SessionState> activeSessions = new ConcurrentHashMap<>();

    public SessionState getOrCreateSession(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty() || !activeSessions.containsKey(sessionId)) {
            String newSessionId = UUID.randomUUID().toString();
            SessionState newState = new SessionState(newSessionId);
            activeSessions.put(newSessionId, newState);
            return newState;
        }
        return activeSessions.get(sessionId);
    }

    public void removeSession(String sessionId) {
        if (sessionId != null) {
            activeSessions.remove(sessionId);
        }
    }
}
