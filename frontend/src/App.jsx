import React, { useState, useRef, useEffect } from 'react';
import SearchForm from './components/SearchForm';
import ResultCard from './components/ResultCard';
import ParticlesBackground from './components/ParticlesBackground';
import './styles/globals.css';
import './App.css';

function App() {
  const [loading, setLoading] = useState(false);
  const [showResults, setShowResults] = useState(false);
  const [results, setResults] = useState([]);
  const [searchParams, setSearchParams] = useState(null);
  
  // Filters
  const [filterProvider, setFilterProvider] = useState('All Providers');
  const [filterMinSpeed, setFilterMinSpeed] = useState('Any');

  // Update page title based on state
  useEffect(() => {
    if (!showResults) {
      document.title = 'ISP Radar';
    } else if (loading) {
      document.title = 'Searching...';
    } else {
      document.title = 'Results View';
    }
  }, [loading, showResults]);

  const handleSearch = async (params) => {
    setSearchParams(params);
    setLoading(true);
    setShowResults(true);

    try {
      const checkPayload = {
        state: params.state,
        municipality: params.municipality,
        postalCode: params.postalCode,
        street: params.street,
        area: params.area,
        number: params.number,
        numberItem: params.numberItem,
      };

      const response = await fetch('http://localhost:8080/api/address/check', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(checkPayload),
      });

      const data = await response.json();
      
      // Transform the response into result cards
      const plans = data.plans || [];
      setResults(
        plans.map(plan => {
          let tech = 'ADSL';
          if (plan.maxDownloadMbps > 24) tech = 'VDSL';
          if (plan.maxDownloadMbps >= 200 || (plan.name && plan.name.toLowerCase().includes('fiber'))) tech = 'FTTH';

          return {
            name: plan.name || 'Plan',
            provider: plan.provider || 'Provider',
            download: plan.maxDownloadMbps ? `${plan.maxDownloadMbps} Mbps` : 'N/A',
            upload: plan.maxUploadMbps ? `${plan.maxUploadMbps} Mbps` : 'N/A',
            tech: tech,
            price: plan.price || null,
            available: true,
          };
        })
      );

      // Simulate loading for better UX
      setTimeout(() => {
        setLoading(false);
      }, 500);
    } catch (error) {
      console.error('Error checking availability:', error);
      setLoading(false);
      setResults([]);
    }
  };

  const handleNewSearch = () => {
    setShowResults(false);
    setResults([]);
    setLoading(false);
    setSearchParams(null);
  };

  const searchId = Math.floor(Math.random() * 0xFFFFFF).toString(16).padStart(6, '0');

  // Filter the results
  const filteredResults = results.filter(plan => {
    if (filterProvider !== 'All Providers' && plan.provider.toLowerCase() !== filterProvider.toLowerCase()) {
      return false;
    }
    if (filterMinSpeed !== 'Any') {
      const minSpeedNum = parseInt(filterMinSpeed, 10);
      const isGbps = filterMinSpeed.includes('Gbps');
      const minSpeedMbps = isGbps ? minSpeedNum * 1000 : minSpeedNum;
      
      const planSpeed = parseInt(plan.download, 10);
      if (!planSpeed || planSpeed < minSpeedMbps) return false;
    }
    return true;
  });

  const availableTechs = Array.from(new Set(results.map(r => r.tech)));

  return (
    <div className={`app-container ${showResults ? 'results-mode' : 'search-mode'}`}>
      <ParticlesBackground />
      {!showResults && (
        <div className="hero-section">
          <div className="hero-content">
            <h1><span className="isp-radar-logo"><span className="isp-part">ISP</span><span className="radar-part">Radar</span></span></h1>
            <p>Check Internet Service Provider availability at your location in Greece</p>
          </div>
          <div className="centered-search-container">
            <SearchForm onSearch={handleSearch} loading={loading} />
          </div>
        </div>
      )}

      {showResults && (
        <div className="results-layout">
          {/* Left Column: Search Summary */}
          <div className="left-column">
            <div className="summary-card glass-panel">
              <div className="summary-header">
                <span className="search-id">Search #{searchId}</span>
                <span className="status-badge">{loading ? 'Searching...' : 'Completed'}</span>
              </div>
              <div className="summary-address">
                <h3>
                  {searchParams?.street?.label} {searchParams?.number}
                </h3>
                <p>
                  {searchParams?.municipality?.label}, {searchParams?.state?.label} {searchParams?.postalCode?.label}
                </p>
              </div>
              
              {!loading && availableTechs.length > 0 && (
                <div className="summary-section">
                  <span className="section-label">Available Technologies</span>
                  {availableTechs.map(tech => (
                    <span key={tech} className="tech-chip">{tech}</span>
                  ))}
                </div>
              )}

              <div className="summary-footer">
                <button className="secondary-btn" onClick={handleNewSearch}>Edit Address</button>
                <button className="primary-btn outline" onClick={() => handleSearch(searchParams)}>Refresh</button>
              </div>
            </div>
            
            <div className="filters-card glass-panel">
              <h3>Filters</h3>
              <div className="filter-group">
                <label>Provider</label>
                <select 
                  className="filter-select"
                  value={filterProvider}
                  onChange={(e) => setFilterProvider(e.target.value)}
                >
                  <option>All Providers</option>
                  <option>Cosmote</option>
                  <option>Vodafone</option>
                  <option>Nova</option>
                </select>
              </div>
              <div className="filter-group">
                <label>Min Speed</label>
                <select 
                  className="filter-select"
                  value={filterMinSpeed}
                  onChange={(e) => setFilterMinSpeed(e.target.value)}
                >
                  <option>Any</option>
                  <option>100 Mbps</option>
                  <option>300 Mbps</option>
                  <option>1 Gbps</option>
                </select>
              </div>
            </div>
          </div>

          {/* Right Column: Search Box (top) + Results (bottom) */}
          <div className="right-column">
            <div className="right-search-container glass-panel">
               <SearchForm onSearch={handleSearch} loading={loading} />
            </div>

            <div className="results-content">
              {loading ? (
                <>
                  <div className="results-header">
                    <h2>Searching Plans...</h2>
                  </div>
                  <div className="results-grid">
                    <ResultCard plan={{ name: 'Plan 1', provider: 'Loading...' }} loading={true} />
                    <ResultCard plan={{ name: 'Plan 2', provider: 'Loading...' }} loading={true} />
                  </div>
                </>
              ) : (
                <>
                  <div className="results-header">
                    <h2>{filteredResults.length} Plans Found</h2>
                    <div className="provider-toggles">
                      <span className={`toggle cosmote ${filterProvider === 'Cosmote' ? 'active' : ''}`} onClick={() => setFilterProvider(filterProvider === 'Cosmote' ? 'All Providers' : 'Cosmote')}>Cosmote</span>
                      <span className={`toggle vodafone ${filterProvider === 'Vodafone' ? 'active' : ''}`} onClick={() => setFilterProvider(filterProvider === 'Vodafone' ? 'All Providers' : 'Vodafone')}>Vodafone</span>
                      <span className={`toggle nova ${filterProvider === 'Nova' ? 'active' : ''}`} onClick={() => setFilterProvider(filterProvider === 'Nova' ? 'All Providers' : 'Nova')}>Nova</span>
                    </div>
                  </div>
                  
                  <div className="results-scroll-container">
                    <div className="results-grid">
                      {filteredResults.length > 0 ? (
                        filteredResults.map((plan, index) => (
                          <ResultCard key={index} plan={plan} loading={false} />
                        ))
                      ) : (
                        <div className="no-results glass-panel">
                          <p>No ISP plans match your filters.</p>
                        </div>
                      )}
                    </div>
                  </div>
                </>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default App;