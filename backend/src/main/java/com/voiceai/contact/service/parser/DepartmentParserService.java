package com.voiceai.contact.service.parser;

import org.springframework.stereotype.Service;

@Service
public class DepartmentParserService {

    public String parseDepartment(String text) {
        if (text == null) return null;
        String s = text.toLowerCase().trim();

        // 1. Orthopedic: haddi, bone, ortho, orthopedic, joint, ghutna, knee, plus original bodily symptoms
        if (s.contains("ortho") || s.contains("orthopedic") || s.contains("haddi") || s.contains("हड्डी") 
                || s.contains("bone") || s.contains("joint") || s.contains("ghutna") || s.contains("घुटने") 
                || s.contains("knee") || s.contains("अस्थि") || s.contains("जोड़") || s.contains("कमर") 
                || s.contains("बाँह्") || s.contains("टाँग")) {
            return "Orthopedic";
        }

        // 2. Cardiology: heart, cardio, dil
        if (s.contains("cardio") || s.contains("cardiology") || s.contains("heart") || s.contains("dil") 
                || s.contains("दिल") || s.contains("छाती") || s.contains("सांस")) {
            return "Cardiology";
        }

        // 3. Neurology: neuro, brain, migraine, sir dard
        if (s.contains("neuro") || s.contains("neurology") || s.contains("brain") || s.contains("migraine") 
                || s.contains("sir dard") || s.contains("दिमाग") || s.contains("सिरदर्द") || s.contains("मिर्गी")) {
            return "Neurology";
        }

        // 4. Dermatology: skin, allergy, pimples, rash
        if (s.contains("derma") || s.contains("dermatology") || s.contains("skin") || s.contains("allergy") 
                || s.contains("pimples") || s.contains("rash") || s.contains("त्वचा") || s.contains("खाज") 
                || s.contains("दाने") || s.contains("पिंपल")) {
            return "Dermatology";
        }

        // 5. General Physician: fever, cold, cough, bukhar
        if (s.contains("general") || s.contains("physician") || s.contains("fever") || s.contains("cold") 
                || s.contains("cough") || s.contains("bukhar") || s.contains("सामान्य") || s.contains("gp") 
                || s.contains("बुखार") || s.contains("जुकाम")) {
            return "General Physician";
        }

        // 6. Pediatrics: child, baby, bachcha
        if (s.contains("paed") || s.contains("pedia") || s.contains("pediatrics") || s.contains("child") 
                || s.contains("baby") || s.contains("bachcha") || s.contains("बच्च")) {
            return "Pediatrics";
        }

        return null;
    }
}
