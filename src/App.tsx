
import React from "react";
import HeaderLogo from "./components/HeaderLogo";
import Index from "./pages/Index";
import "./components/HeaderLogo.css";

function App() {
  return (
    <div className="min-h-screen bg-white">
      {/* Header with Unified Logo */}
      <header className="bg-white shadow-sm border-b backdrop-blur-sm bg-white/90">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between items-center h-16">
            <HeaderLogo />
          </div>
        </div>
      </header>
      
      {/* Main content */}
      <Index />
    </div>
  );
}

export default App;
