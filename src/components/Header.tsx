
import React from 'react';

interface HeaderProps {
  className?: string;
}

const Header: React.FC<HeaderProps> = ({ className }) => {
  return (
    <header className={`bg-white dark:bg-gray-900 shadow-sm ${className}`}>
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between items-center py-4">
          <div className="flex items-center">
            <h1 className="text-xl font-bold text-gray-900">AI Voice Solutions</h1>
          </div>
          <nav className="hidden md:flex space-x-8">
            {/* Your navigation items */}
          </nav>
        </div>
      </div>
    </header>
  );
};

export default Header;
