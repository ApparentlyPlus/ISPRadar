import React from 'react';
import '../styles/LoadingAnimation.css';

export default function LoadingAnimation() {
  return (
    <div className="loading-animation-container">
      <div className="loading-animation">
        <div className="loader" />
        <div className="inner-glow" />
      </div>
      <p className="loading-text">Searching for plans...</p>
    </div>
  );
}
