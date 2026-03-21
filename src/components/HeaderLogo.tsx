
import React from "react";

const HeaderLogo: React.FC = () => {
  return (
    <div className="flex items-center group cursor-pointer w-full max-w-sm">
      {/* Animated Logo Container */}
      <div className="relative mr-4 transform transition-all duration-500 group-hover:scale-110">
        {/* Outer rotating ring */}
        <div className="absolute inset-0 w-12 h-12 border-2 border-transparent bg-gradient-to-r from-violet-400 via-cyan-400 to-violet-400 rounded-full animate-spin opacity-70 group-hover:opacity-100 transition-opacity duration-300" 
             style={{ background: 'linear-gradient(45deg, transparent, transparent), conic-gradient(from 0deg, #8b5cf6, #06b6d4, #8b5cf6)', 
                      mask: 'radial-gradient(circle at center, transparent 70%, black 72%)'}}></div>
        
        {/* Inner pulsing ring */}
        <div className="absolute inset-1 w-10 h-10 border border-violet-300 rounded-full pulse-ring"></div>
        
        {/* Sound Wave Bars */}
        <div className="relative w-12 h-12 flex items-center justify-center">
          <div className="flex items-center justify-center gap-0.5">
            {[6, 10, 14, 18, 14, 10, 6].map((height, i) => (
              <div
                key={i}
                className="w-0.5 bg-gradient-to-t from-violet-600 via-cyan-400 to-violet-500 rounded-full transition-all duration-500 group-hover:from-violet-700 group-hover:to-cyan-300 wave-bar"
                style={{ 
                  height: `${height}px`,
                  animationDelay: `${i * 0.15}s`,
                  transform: 'scaleY(0.8)',
                }}
              ></div>
            ))}
          </div>
        </div>
      </div>
      
      {/* Animated Text with Enhanced Typography */}
      <div className="relative overflow-hidden flex-1">
        <h1 className="text-2xl font-light text-gray-800 transition-all duration-500 group-hover:text-violet-700 tracking-wide">
          <span className="inline-block transform transition-all duration-500 group-hover:translate-x-2 text-animation">
            AI Voice
          </span>
          <span className="inline-block ml-2 font-medium transform transition-all duration-700 group-hover:translate-x-1 text-animation-delayed">
            Solutions
          </span>
        </h1>
        
        {/* Animated underline */}
        <div className="absolute bottom-0 left-0 w-0 h-0.5 bg-gradient-to-r from-violet-500 via-cyan-400 to-violet-500 transition-all duration-700 group-hover:w-full underline-expand"></div>
        
        {/* Subtle background glow on hover */}
        <div className="absolute inset-0 bg-gradient-to-r from-violet-50 to-cyan-50 opacity-0 transition-opacity duration-500 group-hover:opacity-30 rounded-lg -z-10"></div>
      </div>
    </div>
  );
};

export default HeaderLogo;
