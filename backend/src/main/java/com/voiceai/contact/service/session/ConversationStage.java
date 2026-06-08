package com.voiceai.contact.service.session;

public enum ConversationStage {
    WELCOME,
    COLLECT_NAME,
    COLLECT_DEPARTMENT,
    COLLECT_DATE,
    COLLECT_TIME,
    CONFIRMATION,
    POST_CONFIRM,
    RESCHEDULE,
    COMPLETED
}
