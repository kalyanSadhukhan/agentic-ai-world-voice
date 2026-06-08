package com.voiceai.contact.service.speech;

import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class SpeechTextFormatter {

    private final Map<Pattern, String> replacements = new LinkedHashMap<>();

    public SpeechTextFormatter() {
        // Brand name & greetings (order from longest to shortest match)
        addReplacement("(?i)welcome to voiceaiagentpro\\.?\\b", "वॉइस एआई एजेंट प्रो में आपका स्वागत है");
        addReplacement("(?i)welcome to voice ai agent pro\\.?\\b", "वॉइस एआई एजेंट प्रो में आपका स्वागत है");
        addReplacement("(?i)voiceaiagentpro\\b", "वॉइस एआई एजेंट प्रो");
        addReplacement("(?i)voice ai agent pro\\b", "वॉइस एआई एजेंट प्रो");

        // Role & clinical terms
        addReplacement("(?i)appointment assistant\\b", "अपॉइंटमेंट सहायक");
        addReplacement("(?i)appointment\\b", "अपॉइंटमेंट");
        addReplacement("(?i)assistant\\b", "सहायक");
        addReplacement("(?i)doctor\\b", "डॉक्टर");
        addReplacement("(?i)dr\\.\\s*", "डॉक्टर ");
        addReplacement("(?i)\\bdr\\b", "डॉक्टर");

        // Departments
        addReplacement("(?i)orthopedic\\b", "ऑर्थोपेडिक");
        addReplacement("(?i)cardiology\\b", "कार्डियोलॉजी");
        addReplacement("(?i)neurology\\b", "न्यूरोलॉजी");
        addReplacement("(?i)dermatology\\b", "डर्मेटोलॉजी");
        addReplacement("(?i)pediatrics\\b", "पीडियाट्रिक्स");
        addReplacement("(?i)general physician\\b", "जनरल फिजिशियन");

        // Doctor names
        addReplacement("(?i)sharma\\b", "शर्मा");
        addReplacement("(?i)reddy\\b", "रेड्डी");
        addReplacement("(?i)khan\\b", "खान");
        addReplacement("(?i)iyer\\b", "आयर");
        addReplacement("(?i)verma\\b", "वर्मा");
        addReplacement("(?i)nair\\b", "नायर");
        addReplacement("(?i)gupta\\b", "गुप्ता");
        addReplacement("(?i)das\\b", "दास");
        addReplacement("(?i)mehta\\b", "मेहता");
        addReplacement("(?i)banerjee\\b", "बनर्जी");
        addReplacement("(?i)pillai\\b", "पिल्लई");
        addReplacement("(?i)roy\\b", "रॉय");
        addReplacement("(?i)kapoor\\b", "कपूर");
        addReplacement("(?i)singh\\b", "सिंह");
        addReplacement("(?i)ali\\b", "अली");
        addReplacement("(?i)joshi\\b", "जोशी");
        addReplacement("(?i)kumar\\b", "कुमार");
        addReplacement("(?i)thomas\\b", "थॉमस");
        addReplacement("(?i)patel\\b", "पटेल");
        addReplacement("(?i)arora\\b", "अरोड़ा");
        addReplacement("(?i)mishra\\b", "मिश्रा");
        addReplacement("(?i)fernandes\\b", "फर्नांडिस");
        addReplacement("(?i)sen\\b", "सेन");

        // Weekdays
        addReplacement("(?i)monday\\b", "सोमवार");
        addReplacement("(?i)tuesday\\b", "मंगलवार");
        addReplacement("(?i)wednesday\\b", "बुधवार");
        addReplacement("(?i)thursday\\b", "गुरुवार");
        addReplacement("(?i)friday\\b", "शुक्रवार");
        addReplacement("(?i)saturday\\b", "शनिवार");
        addReplacement("(?i)sunday\\b", "रविवार");

        // Months
        addReplacement("(?i)january\\b", "जनवरी");
        addReplacement("(?i)february\\b", "फ़रवरी");
        addReplacement("(?i)march\\b", "मार्च");
        addReplacement("(?i)april\\b", "अप्रैल");
        addReplacement("(?i)may\\b", "मई");
        addReplacement("(?i)june\\b", "जून");
        addReplacement("(?i)july\\b", "जुलाई");
        addReplacement("(?i)august\\b", "अगस्त");
        addReplacement("(?i)september\\b", "सितंबर");
        addReplacement("(?i)october\\b", "अक्टूबर");
        addReplacement("(?i)november\\b", "नवंबर");
        addReplacement("(?i)december\\b", "दिसंबर");

        // General dialog cues
        addReplacement("(?i)reschedule\\b", "रिशेड्यूल");
        addReplacement("(?i)cancel\\b", "कैंसिल");
        addReplacement("(?i)name\\b", "नाम");
        addReplacement("(?i)department\\b", "विभाग");
        addReplacement("(?i)date\\b", "तारीख");
        addReplacement("(?i)time\\b", "समय");
        addReplacement("(?i)yes\\b", "हाँ");
        addReplacement("(?i)no\\b", "नहीं");
        addReplacement("(?i)ok\\b", "ठीक");
        addReplacement("(?i)okay\\b", "ठीक");
        addReplacement("(?i)sure\\b", "ज़रूर");
    }

    private void addReplacement(String regex, String replacement) {
        replacements.put(Pattern.compile(regex), replacement);
    }

    public String formatForHindiTts(String text) {
        if (text == null) return null;
        String formatted = text;
        for (Map.Entry<Pattern, String> entry : replacements.entrySet()) {
            formatted = entry.getKey().matcher(formatted).replaceAll(entry.getValue());
        }
        return formatted;
    }
}
