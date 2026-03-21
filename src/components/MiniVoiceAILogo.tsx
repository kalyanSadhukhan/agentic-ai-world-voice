import React from "react";
import "./VoiceAILogo.css";

interface MiniVoiceAILogoProps {
  variant?: "dark" | "light" | "monochrome" | "icon-only";
}

const MiniVoiceAILogo: React.FC<MiniVoiceAILogoProps> = ({ variant = "dark" }) => {
  const isIconOnly = variant === "icon-only";
  const containerClass = `variation ${
    variant === "icon-only" ? "" : variant + "-version"
  }`;

  return (
    <div className={containerClass}>
      {!isIconOnly && (
        <h3>
          {variant.charAt(0).toUpperCase() + variant.slice(1).replace("-", " ")}
        </h3>
      )}
      <div className="mini-logo">
        <div className="mini-icon">
          {variant === "icon-only" && (
            <div
              style={{
                position: "absolute",
                width: "100%",
                height: "100%",
                border: "1px solid #8b5cf6",
                borderRadius: "50%",
                animation: "rotate 3s linear infinite",
              }}
            ></div>
          )}
          <div className="mini-waves">
            {[8, 12, 16, 12, 8].map((height, i) => (
              <div
                key={i}
                className="mini-wave"
                style={{ height: `${height}px` }}
              ></div>
            ))}
          </div>
        </div>
        {!isIconOnly && <div className="mini-name">VoiceAI</div>}
      </div>
    </div>
  );
};

export default MiniVoiceAILogo;
