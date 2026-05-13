import React from 'react';
import '../styles/ResultCard.css';
import cmtLogo from '../assets/cmt.svg';
import vdfLogo from '../assets/vdf.svg';
import novaLogo from '../assets/nova.svg';

export default function ResultCard({ plan, loading }) {
  const getProviderLogo = (provider) => {
    const providerLower = provider?.toLowerCase() ?? '';
    if (providerLower.includes('cosmote')) return cmtLogo;
    if (providerLower.includes('vodafone')) return vdfLogo;
    if (providerLower.includes('nova')) return novaLogo;
    return null;
  };
  if (loading) {
    return (
      <div className="plan-card skeleton">
        <div className="plan-section header-section">
          <div className="provider-info">
            <div className="ske ske-logo"></div>
            <div className="card-names" style={{ width: '100%' }}>
              <div className="ske ske-title"></div>
              <div className="ske ske-sub"></div>
            </div>
          </div>
          <div className="ske ske-badge"></div>
        </div>
        <div className="plan-section speed-section">
          <div className="ske ske-speed-box"></div>
        </div>
        <div className="plan-section details-section">
          <div className="features-list">
            <div className="ske ske-chip"></div>
            <div className="ske ske-chip" style={{ width: '60px' }}></div>
          </div>
          <div className="ske ske-price"></div>
        </div>
      </div>
    );
  }

  const providerClass = plan.provider ? plan.provider.toLowerCase() : 'default';
  const downloadSpeed = plan.download ? plan.download.replace(/ Mbps/i, '') : '--';
  const uploadSpeed = plan.upload ? plan.upload.replace(/ Mbps/i, '') : null;

  return (
    <div className="plan-card">
      <div className="plan-section header-section">
        <div className="provider-info">
          <div className={`provider-logo ${providerClass}`}>
            <img src={getProviderLogo(plan.provider)} alt={plan.provider} />
          </div>
          <div className="plan-title">
            <div className="name" title={plan.name}>{plan.name}</div>
            <div className="subtitle">{plan.provider} Greece</div>
          </div>
        </div>
        <div className={`badge outline-badge ${providerClass}`}>{plan.provider}</div>
      </div>

      <div className="plan-section speed-section">
        <div className="speed-box">
          <div className="speed-label">ΕΩΣ</div>
          <div className="speed-main">
            <span className="val">{downloadSpeed}</span>
            <span className="unit">Mbps</span>
            <span className="arr">↓</span>
          </div>
          {uploadSpeed && uploadSpeed !== 'N/A' && (
            <>
              <div className="speed-sep">/</div>
              <div className="speed-sub">
                <span className="val">{uploadSpeed}</span>
                <span className="unit">Mbps</span>
                <span className="arr">↑</span>
              </div>
            </>
          )}
          <div className="tech-badge">{plan.tech}</div>
        </div>
      </div>

      <div className="plan-section details-section">
        <div className="features-list">
          <span className="feature-chip">Internet</span>
          {plan.available ? (
             <span className="feature-chip success">Available</span>
          ) : (
             <span className="feature-chip error">Unavailable</span>
          )}
        </div>
        <div className="price-block">
          <div className="price">€--.--</div>
          <div className="period">/ month</div>
        </div>
      </div>
    </div>
  );
}
