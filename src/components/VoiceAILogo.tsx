import React from "react";
import "./VoiceAILogo.css";

const VoiceAILogo: React.FC = () => {
  return (
    <div className="logo-container">
      <div className="logo-main">
        <div className="logo-icon">
          <div className="ai-circle"></div>
          <div className="sound-waves">
            {[...Array(7)].map((_, i) => (
              <div
                key={i}
                className="wave"
                style={{ animationDelay: `${i * 0.1}s` }}
              />
            ))}
          </div>
        </div>
        <div>
          <div className="company-name">VoiceAI</div>
          <div className="tagline">Intelligent Voice Agents</div>
        </div>
      </div>
    </div>
  );
};

export default VoiceAILogo;
